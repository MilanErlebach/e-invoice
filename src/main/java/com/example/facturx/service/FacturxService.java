package com.example.facturx.service;

import com.example.facturx.model.InvoiceDTO;
import com.example.facturx.model.InvoiceDTO.Line;
import com.example.facturx.model.InvoiceDTO.PartyDTO;
import org.mustangproject.*;
import org.mustangproject.ZUGFeRD.ZUGFeRDExporterFromPDFA;
import org.mustangproject.ZUGFeRD.ZUGFeRDExporterFromA1;
import org.mustangproject.ZUGFeRD.ZUGFeRDExporterFromA3;
import org.mustangproject.ZUGFeRD.Profiles;
import org.mustangproject.ZUGFeRD.IZUGFeRDExporter;
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
      String invNumber = dto.header != null ? dto.header.number : "INV-001";
      LocalDate issue = parseDate(dto.header != null ? dto.header.issueDate : null);
      if (issue == null) {
        // Mustang 2.19 requires a non-null issue date; fallback to today if not provided
        issue = LocalDate.now();
      }
      inv.setNumber(invNumber)
         .setIssueDate(java.sql.Date.valueOf(issue))
         .setCurrency(dto.header != null && dto.header.currency != null ? dto.header.currency : "EUR");
      
      // Set delivery date to service_from if available, otherwise use issue date
      LocalDate deliveryDate = issue;
      if (dto.header != null && notBlank(dto.header.serviceFrom)) {
        LocalDate serviceFrom = parseDate(dto.header.serviceFrom);
        if (serviceFrom != null) {
          deliveryDate = serviceFrom;
          System.out.println("Using service_from as delivery date: " + serviceFrom);
        }
      } else {
        System.out.println("No service_from found, using issue date as delivery date: " + issue);
      }
      inv.setDeliveryDate(java.sql.Date.valueOf(deliveryDate));
      
      // Document type will be set later when we have the exporter

      // Leistungszeitraum - only set if both dates are valid
      if (dto.header != null && notBlank(dto.header.serviceFrom) && notBlank(dto.header.serviceTo)) {
        LocalDate from = parseDate(dto.header.serviceFrom);
        LocalDate to   = parseDate(dto.header.serviceTo);
        if (from != null && to != null) {
          inv.setDetailedDeliveryPeriod(
              java.sql.Date.valueOf(from),
              java.sql.Date.valueOf(to)
          );
        }
      }

      // Fälligkeit + Text
      if (dto.header != null && notBlank(dto.header.dueDate)) {
        LocalDate due = parseDate(dto.header.dueDate);
        if (due != null) {
          inv.setDueDate(java.sql.Date.valueOf(due));
          inv.setPaymentTermDescription("Please remit until " + formatDE(due));
        }
      }

      // --- Parteien ---
      TradeParty seller = mapParty(dto.seller, true);
      TradeParty buyer  = mapParty(dto.buyer,  false);
      inv.setSender(seller);
      inv.setRecipient(buyer);
      
      // Validate required parties
      if (seller == null) {
        throw new IllegalArgumentException("Seller information is required");
      }
      if (buyer == null) {
        throw new IllegalArgumentException("Buyer information is required");
      }

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

        // Handle negative prices as credits (skip them from line items, will be added as allowances)
        if (unitNet.compareTo(BigDecimal.ZERO) < 0) {
          System.out.println("Skipping negative price line: " + l.description + " (" + unitNet + ")");
          continue; // Skip negative price lines - they should be handled as allowances
        }

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

      // --- Items hinzufügen (ohne Skalierung - verwende Originalpreise) ---
      for (Prep p : preps) {
        String unit = notBlank(p.src.unitCode) ? p.src.unitCode : "C62";
        BigDecimal unitNet = p.unitNetOrig; // Use original price without scaling

        Product prod = new Product();
        prod.setName(p.src.description)
            .setUnit(unit)
            .setVATPercent(p.vatPct);
        if (notBlank(p.src.taxCategory)) {
          prod.setTaxCategoryCode(p.src.taxCategory);
        }

        Item item = new Item(prod, p.qty, unitNet);

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
      
      // Add invoice-level discount if provided
      if (dto.totals != null && notBlank(dto.totals.discountGross)) {
        BigDecimal invoiceDiscount = bd2(dto.totals.discountGross);
        if (invoiceDiscount.compareTo(BigDecimal.ZERO) > 0) {
          // Add as a separate line item with negative amount
          Product discountProd = new Product();
          discountProd.setName("Discount")
                      .setUnit("C62")
                      .setVATPercent(BigDecimal.ZERO);
          
          Item discountItem = new Item(discountProd, BigDecimal.ONE, invoiceDiscount.negate());
          inv.addItem(discountItem);
          System.out.println("Adding invoice discount: " + invoiceDiscount);
        }
      }
      
      // Add negative price lines as separate line items with negative amounts
      for (Line l : dto.lines) {
        if (notBlank(l.description) && notBlank(l.quantity)) {
          BigDecimal qty = bd4(l.quantity);
          BigDecimal unitNet = l.unitNetPriceBD();
          
          if (unitNet.compareTo(BigDecimal.ZERO) < 0) {
            // Add as a separate line item with negative amount
            String unit = notBlank(l.unitCode) ? l.unitCode : "C62";
            BigDecimal vatPct = bd2(defaultIfBlank(l.taxRate, "0"));
            
            Product creditProd = new Product();
            creditProd.setName(l.description + " (Credit)")
                      .setUnit(unit)
                      .setVATPercent(vatPct);
            if (notBlank(l.taxCategory)) {
              creditProd.setTaxCategoryCode(l.taxCategory);
            }
            
            Item creditItem = new Item(creditProd, qty, unitNet.abs().negate());
            inv.addItem(creditItem);
            System.out.println("Adding credit line item: " + l.description + " = " + unitNet.abs().multiply(qty));
          }
        }
      }
      
      // 3) Exporter: Try ZUGFeRDExporterFromPDFA first, fallback to ZUGFeRDExporterFromA3 for invoices
      // CRITICAL: Use ZUGFeRDExporterFromA3 (not DXExporterFromA3) for proper invoice generation
      IZUGFeRDExporter exporter;
      try {
        System.out.println("Attempting to use ZUGFeRDExporterFromPDFA for invoice generation...");
        exporter = new ZUGFeRDExporterFromPDFA()
            .load(tmpPdf.getAbsolutePath())
            .setZUGFeRDVersion(2)
            .setProfile(Profiles.getByName("EN16931"))
            .setProducer("FacturX-Converter")
            .setCreator("Mustangproject");
        System.out.println("Successfully loaded PDF with ZUGFeRDExporterFromPDFA");
      } catch (IllegalArgumentException | IOException e) {
        System.out.println("ZUGFeRDExporterFromPDFA failed, falling back to ZUGFeRDExporterFromA3 for invoice generation...");
        exporter = new ZUGFeRDExporterFromA3()
            .load(tmpPdf.getAbsolutePath())
            .setZUGFeRDVersion(2)
            .setProfile(Profiles.getByName("EN16931"))
            .setProducer("FacturX-Converter")
            .setCreator("Mustangproject");
        System.out.println("Successfully loaded PDF with ZUGFeRDExporterFromA3 (will convert to PDF/A-3 and generate invoice)");
      }

      // Debug: Check invoice dates before setting transaction
      System.out.println("Invoice issue date: " + inv.getIssueDate());
      System.out.println("Invoice due date: " + inv.getDueDate());
      System.out.println("Invoice delivery date: " + inv.getDeliveryDate());
      System.out.println("Invoice number: " + inv.getNumber());
      System.out.println("Invoice currency: " + inv.getCurrency());
      System.out.println("Invoice sender: " + (inv.getSender() != null ? inv.getSender().getName() : "null"));
      System.out.println("Invoice recipient: " + (inv.getRecipient() != null ? inv.getRecipient().getName() : "null"));
      System.out.println("Invoice items count: " + "checking items...");
      
      // CRITICAL: Ensure all required dates are set - Mustang library is very strict about this
      if (inv.getIssueDate() == null) {
        System.out.println("WARNING: Issue date is null, setting to today");
        inv.setIssueDate(java.sql.Date.valueOf(LocalDate.now()));
      }
      if (inv.getDeliveryDate() == null) {
        System.out.println("WARNING: Delivery date is null, setting to issue date");
        inv.setDeliveryDate(inv.getIssueDate());
      }
      if (inv.getDueDate() == null) {
        System.out.println("WARNING: Due date is null, setting to issue date + 14 days");
        LocalDate dueDate = LocalDate.now().plusDays(14);
        inv.setDueDate(java.sql.Date.valueOf(dueDate));
      }
      
      // Ensure we have a valid invoice number
      if (inv.getNumber() == null || inv.getNumber().trim().isEmpty()) {
        System.out.println("WARNING: Invoice number is null or empty, setting default");
        inv.setNumber("INV-" + System.currentTimeMillis());
      }
      
      // Ensure we have a valid currency
      if (inv.getCurrency() == null || inv.getCurrency().trim().isEmpty()) {
        System.out.println("WARNING: Currency is null or empty, setting to EUR");
        inv.setCurrency("EUR");
      }
      
      // Set the invoice transaction directly - ZUGFeRDExporterFromA3 will generate proper invoice XML
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
