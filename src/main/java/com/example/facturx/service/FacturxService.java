package com.example.facturx.service;

import com.example.facturx.model.InvoiceDTO;
import com.example.facturx.model.InvoiceDTO.Line;
import com.example.facturx.model.InvoiceDTO.PartyDTO;
import org.mustangproject.*;
import org.mustangproject.ZUGFeRD.ZUGFeRDExporterFromPDFA;
import org.mustangproject.ZUGFeRD.Profiles;
import org.mustangproject.ZUGFeRD.IZUGFeRDExporter;
import org.mustangproject.ZUGFeRD.DXExporterFromA3;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.*;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.Files;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

@Service
public class FacturxService {

  private static final DateTimeFormatter DATE = DateTimeFormatter.ISO_LOCAL_DATE;

  public byte[] buildFacturX(InvoiceDTO dto, MultipartFile sourcePdf) {
    File tmpPdf = null;
    try {
      // 1) Eingangs-PDF temp. speichern
      tmpPdf = File.createTempFile("fx-src-", ".pdf");
      try (OutputStream os = Files.newOutputStream(tmpPdf.toPath())) {
        os.write(sourcePdf.getBytes());
      }

      // 2) Mustang-Invoice aufbauen
      Invoice inv = new Invoice();

      // --- Header ---
      String invNumber = dto.header.number;
      LocalDate issue = parseDate(dto.header != null ? dto.header.issueDate : null);
      if (issue == null) {
        // Mustang 2.19 requires a non-null issue date; fallback to today if not provided
        issue = LocalDate.now();
      }
      inv.setNumber(invNumber)
         .setIssueDate(java.sql.Date.valueOf(issue))
         .setCurrency(dto.header.currency != null ? dto.header.currency : "EUR");

      // Leistungszeitraum
      if (notBlank(dto.header.serviceFrom) || notBlank(dto.header.serviceTo)) {
        LocalDate from = parseDate(dto.header.serviceFrom);
        LocalDate to   = parseDate(dto.header.serviceTo);
        inv.setDetailedDeliveryPeriod(
            from != null ? java.sql.Date.valueOf(from) : null,
            to   != null ? java.sql.Date.valueOf(to)   : null
        );
      }

      // Fälligkeit + Text
      if (notBlank(dto.header.dueDate)) {
        LocalDate due = parseDate(dto.header.dueDate);
        inv.setDueDate(java.sql.Date.valueOf(due));
        inv.setPaymentTermDescription("Please remit until " + formatDE(due));
      }

      // --- Parteien ---
      TradeParty seller = mapParty(dto.seller, true);
      TradeParty buyer  = mapParty(dto.buyer,  false);
      inv.setSender(seller);
      inv.setRecipient(buyer);

      // BuyerReference (Käuferreferenz)
      if (dto.buyer != null && notBlank(dto.buyer.buyerReference)) {
        inv.setReferenceNumber(dto.buyer.buyerReference);
      }

      // Verwendungszweck/Payment Reference
      if (dto.payment != null && notBlank(dto.payment.remittanceInformation)) {
        inv.setPaymentReference(dto.payment.remittanceInformation);
      }

      // --- Positionen vorbereiten (Skalierung auf gewünschtes Grand Total) ---
      if (dto.lines == null || dto.lines.isEmpty()) {
        throw new IllegalArgumentException("At least one line is required");
      }

      class Prep {
        Line src;
        BigDecimal qty;
        BigDecimal vatPct;      // z.B. 19
        BigDecimal unitNetOrig; // aus net_price oder aus gross_price abgeleitet
      }

      List<Prep> preps = new ArrayList<>();
      BigDecimal grossSumCalc = BigDecimal.ZERO;

      for (Line l : dto.lines) {
        if (!notBlank(l.description)) throw new IllegalArgumentException("Line: description required");
        if (!notBlank(l.quantity))    throw new IllegalArgumentException("Line: quantity required");

        BigDecimal qty     = bd4(l.quantity);
        BigDecimal vatPct  = bd2(defaultIfBlank(l.taxRate, "0"));
        BigDecimal unitNet = l.unitNetPriceBD();

        // Brutto zur Skalierung
        BigDecimal unitGross = unitNet.multiply(BigDecimal.ONE.add(vatPct.movePointLeft(2)));
        BigDecimal lineGross = unitGross.multiply(qty);

        // Positionsrabatt (netto) berücksichtigen
        if (notBlank(l.discount)) {
          BigDecimal discNet = bd2(l.discount);
          if (discNet.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal lineNet = unitNet.multiply(qty).subtract(discNet);
            if (lineNet.compareTo(BigDecimal.ZERO) < 0) lineNet = BigDecimal.ZERO;
            unitNet  = lineNet.divide(qty, 4, RoundingMode.HALF_UP);
            unitGross = unitNet.multiply(BigDecimal.ONE.add(vatPct.movePointLeft(2)));
            lineGross = lineGross = unitGross.multiply(qty);
          }
        }

        grossSumCalc = grossSumCalc.add(lineGross);

        Prep p = new Prep();
        p.src = l; p.qty = qty; p.vatPct = vatPct; p.unitNetOrig = unitNet;
        preps.add(p);
      }

      // Ziel-Grand-Total bestimmen
      BigDecimal targetGrandGross = null;
      BigDecimal subtotalGross    = null;
      BigDecimal discountGross    = null;

      if (dto.totals != null) {
        if (notBlank(dto.totals.grandTotalGross)) targetGrandGross = bd2(dto.totals.grandTotalGross);
        if (notBlank(dto.totals.subtotalGross))   subtotalGross    = bd2(dto.totals.subtotalGross);
        if (notBlank(dto.totals.discountGross))   discountGross    = bd2(dto.totals.discountGross);
      }
      if (targetGrandGross == null && subtotalGross != null && discountGross != null) {
        targetGrandGross = subtotalGross.subtract(discountGross);
      }
      if (targetGrandGross == null) {
        targetGrandGross = grossSumCalc.setScale(2, RoundingMode.HALF_UP);
      }

      BigDecimal scale = BigDecimal.ONE;
      if (grossSumCalc.compareTo(BigDecimal.ZERO) > 0) {
        scale = targetGrandGross.divide(grossSumCalc, 10, RoundingMode.HALF_UP);
      }

      // --- Items hinzufügen (skaliert) ---
      for (Prep p : preps) {
        String unit = notBlank(p.src.unitCode) ? p.src.unitCode : "C62";
        BigDecimal unitNetScaled = p.unitNetOrig.multiply(scale).setScale(4, RoundingMode.HALF_UP);

        Product prod = new Product();
        prod.setName(p.src.description)
            .setUnit(unit)
            .setVATPercent(p.vatPct);
        if (notBlank(p.src.taxCategory)) {
          prod.setTaxCategoryCode(p.src.taxCategory);
        }

        Item item = new Item(prod, p.qty, unitNetScaled);

        // Positions-Rabatt (netto)
        if (notBlank(p.src.discount)) {
          BigDecimal disc = bd2(p.src.discount);
          if (disc.compareTo(BigDecimal.ZERO) > 0) {
            ArrayList<Allowance> als = new ArrayList<>();
            als.add(new Allowance(disc));
            item.setItemAllowances(als);
          }
        }

        inv.addItem(item);
      }
      
      // 3) Exporter mit Fallback:
      //    a) Versuche strikt über ZUGFeRDExporterFromPDFA (nur wenn Quell-PDF bereits PDF/A ist)
      //    b) Fallback auf DXExporterFromA3 (konvertiert "normales" PDF zu PDF/A-3B)
      IZUGFeRDExporter exporter;
      try {
        exporter = new ZUGFeRDExporterFromPDFA()
            .ignorePDFAErrors() // toleranter bei Preflight-Warnungen, greift aber nicht bei "not supported"
            .load(tmpPdf.getAbsolutePath())
            .setZUGFeRDVersion(2)
            .setProfile(Profiles.getByName("EN16931"))
            .setProducer("FacturX-Converter")
            .setCreator("Mustangproject");
      } catch (IllegalArgumentException | IOException ex) {
        exporter = new DXExporterFromA3()
            .load(tmpPdf.getAbsolutePath())
            .setZUGFeRDVersion(2)
            .setProfile(Profiles.getByName("EN16931"))
            .setProducer("FacturX-Converter")
            .setCreator("Mustangproject");
      }

      // Debug: Check invoice dates before setting transaction
      System.out.println("Invoice issue date: " + inv.getIssueDate());
      System.out.println("Invoice due date: " + inv.getDueDate());
      System.out.println("Invoice delivery period from: " + inv.getDeliveryPeriodStart());
      System.out.println("Invoice delivery period to: " + inv.getDeliveryPeriodEnd());
      
      // Direkt die Invoice übergeben (ohne ZUGFeRDTransaction)
      exporter.setTransaction(inv);

      ByteArrayOutputStream bos = new ByteArrayOutputStream();
      exporter.export(bos);
      exporter.close();

      return bos.toByteArray();

    } catch (IOException e) {
      throw new RuntimeException("Failed to build Factur-X PDF: " + e.getMessage(), e);
    } finally {
      if (tmpPdf != null) {
        try { Files.deleteIfExists(tmpPdf.toPath()); } catch (IOException ignored) {}
      }
    }
  }

  /* ===== Helpers ===== */

  private static boolean notBlank(String s) { return s != null && !s.trim().isEmpty(); }
  private static String defaultIfBlank(String s, String def) { return notBlank(s) ? s : def; }

  private static BigDecimal bd4(String s) { return new BigDecimal(s.replace(',', '.')).setScale(4, RoundingMode.HALF_UP); }
  private static BigDecimal bd2(String s) { return new BigDecimal(s.replace(',', '.')).setScale(2, RoundingMode.HALF_UP); }

  private static LocalDate parseDate(String iso) {
    if (!notBlank(iso)) return null;
    String d = iso.length() >= 10 ? iso.substring(0, 10) : iso;
    return LocalDate.parse(d, DATE);
  }

  /** mappt DTO->TradeParty und hängt bei Seller IBAN/BIC als BankDetails an */
  private static TradeParty mapParty(PartyDTO p, boolean isSeller) {
    if (p == null) return null;
    TradeParty tp = new TradeParty();
    if (notBlank(p.name))  tp.setName(p.name);
    if (notBlank(p.street)) tp.setStreet(p.street);
    if (notBlank(p.zip))    tp.setZIP(p.zip);
    if (notBlank(p.city))   tp.setLocation(p.city);
    if (notBlank(p.country))tp.setCountry(p.country);
    if (notBlank(p.vatId))  tp.addVATID(p.vatId);
    if (notBlank(p.taxNumber)) tp.addTaxID(p.taxNumber);
    if (notBlank(p.email))     tp.setEmail(p.email);

    // Bankverbindung an den Verkäufer hängen (Creditor)
    if (isSeller && (notBlank(p.iban) || notBlank(p.bic))) {
      BankDetails bank = new BankDetails();
      if (notBlank(p.iban)) bank.setIBAN(p.iban);
      if (notBlank(p.bic))  bank.setBIC(p.bic);
      tp.addBankDetails(bank);
    }
    return tp;
  }

  private static String formatDE(LocalDate d) {
    if (d == null) return null;
    return String.format("%02d.%02d.%04d", d.getDayOfMonth(), d.getMonthValue(), d.getYear());
  }
}
