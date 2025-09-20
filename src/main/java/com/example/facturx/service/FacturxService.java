package com.example.facturx.service;

import com.example.facturx.model.InvoiceDTO;
import com.example.facturx.model.InvoiceDTO.Line;
import com.example.facturx.model.InvoiceDTO.PartyDTO;
import com.example.facturx.model.InvoiceDTO.TotalsDTO;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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

      // Payment Means Logic with proper TypeCode setting via BankDetails
      boolean isPaid = dto.payment != null && "paid".equals(dto.payment.paymentStatus);
      
      // Ensure seller has a BankDetails entry; create if missing
      List<BankDetails> banks = seller.getBankDetails();
      BankDetails bank;
      if (banks == null || banks.isEmpty()) {
        bank = new BankDetails();
        // Set IBAN/BIC from seller if present
        if (notBlank(dto.seller.iban)) bank.setIBAN(dto.seller.iban);
        if (notBlank(dto.seller.bic))  bank.setBIC(dto.seller.bic);
        seller.addBankDetails(bank);
      } else {
        bank = banks.get(0);
      }
      
      // Set payment means code + information
      if (isPaid) {
        bank.setPaymentMeansCode("ZZZ");                      // Sonstige
        bank.setPaymentMeansInformation("Bereits bezahlt");   // free text
        inv.setPaymentTermDescription("Bereits bezahlt");
        System.out.println("DEBUG: Payment status: paid - setting TypeCode ZZZ (already paid)");
      } else {
        bank.setPaymentMeansCode("58");                       // SEPA Credit Transfer
        bank.setPaymentMeansInformation("SEPA Credit Transfer");
        // Make sure IBAN/BIC are present for unpaid case
        if (dto.payment != null) {
          if (notBlank(dto.payment.iban)) bank.setIBAN(dto.payment.iban);
          if (notBlank(dto.payment.bic))  bank.setBIC(dto.payment.bic);
        }
        System.out.println("DEBUG: Payment status: " + (dto.payment != null ? dto.payment.paymentStatus : "null") + " - setting TypeCode 58 (SEPA Credit Transfer)");
      }

      // --- Positionen vorbereiten (Skalierung auf gewünschtes Grand Total) ---
      if (dto.lines == null || dto.lines.isEmpty()) {
        throw new IllegalArgumentException("At least one line is required");
      }


      List<Prep> preps = new ArrayList<>();
      BigDecimal grossSumCalc = BigDecimal.ZERO;

      for (Line l : dto.lines) {
        if (!notBlank(l.description)) throw new IllegalArgumentException("Line: description required");
        if (!notBlank(l.quantity))    throw new IllegalArgumentException("Line: quantity required");

        BigDecimal qty     = bd4(l.quantity);
        BigDecimal vatPct  = bd2(defaultIfBlank(l.taxRate, "0"));
        BigDecimal unitNet = l.unitNetPriceBD();

        // Handle negative prices as credits (still add to preps for rounding calculation)
        if (unitNet.compareTo(BigDecimal.ZERO) < 0) {
          System.out.println("Skipping negative price line: " + l.description + " (" + unitNet + ")");
          // Don't skip - add to preps for rounding calculation, but skip from line items
        }

        // Brutto zur Skalierung (2 Dezimalstellen)
        BigDecimal unitGross = unitNet.multiply(BigDecimal.ONE.add(vatPct.movePointLeft(2))).setScale(2, RoundingMode.HALF_UP);
        BigDecimal lineGross = unitGross.multiply(qty).setScale(2, RoundingMode.HALF_UP);

        // Positionsrabatt (netto) berücksichtigen
        if (notBlank(l.discount)) {
          BigDecimal discNet = bd2(l.discount);
          if (discNet.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal lineNet = unitNet.multiply(qty).subtract(discNet).setScale(2, RoundingMode.HALF_UP);
            if (lineNet.compareTo(BigDecimal.ZERO) < 0) lineNet = BigDecimal.ZERO;
            unitNet = lineNet.divide(qty, 2, RoundingMode.HALF_UP);
            unitGross = unitNet.multiply(BigDecimal.ONE.add(vatPct.movePointLeft(2))).setScale(2, RoundingMode.HALF_UP);
            lineGross = unitGross.multiply(qty).setScale(2, RoundingMode.HALF_UP);
          }
        }

        grossSumCalc = grossSumCalc.add(lineGross);

        Prep p = new Prep();
        p.src = l; p.qty = qty; p.vatPct = vatPct; p.unitNetOrig = unitNet;
        preps.add(p);
      }

      // --- Items hinzufügen (korrigiere Einzelpreise für korrekte Positionssummen) ---
      for (Prep p : preps) {
        // Skip negative prices from item creation (they will be handled as credit items)
        if (p.unitNetOrig.compareTo(BigDecimal.ZERO) < 0) {
          continue;
        }
        
        String unit = notBlank(p.src.unitCode) ? p.src.unitCode : "C62";
        
        // Berechne die gewünschte Positionssumme (wie in der Rundungsberechnung)
        BigDecimal originalUnitNet = p.src.unitNetPriceBD();
        BigDecimal lineNet = originalUnitNet.multiply(p.qty).setScale(2, RoundingMode.HALF_UP);
        
        // Positionsrabatt berücksichtigen
        if (notBlank(p.src.discount)) {
          BigDecimal discNet = bd2(p.src.discount);
          if (discNet.compareTo(BigDecimal.ZERO) > 0) {
            lineNet = lineNet.subtract(discNet).setScale(2, RoundingMode.HALF_UP);
            if (lineNet.compareTo(BigDecimal.ZERO) < 0) lineNet = BigDecimal.ZERO;
          }
        }
        
        // Berechne Einzelpreis so, dass Mustang Library auf die gewünschte Brutto-Positionssumme kommt
        // Mustang Library berechnet: (Einzelpreis × Menge) × (1 + MwSt)
        // Wir wollen: Brutto-Positionssumme = Netto-Positionssumme × (1 + MwSt)
        // Also: Einzelpreis = Brutto-Positionssumme / (Menge × (1 + MwSt))
        BigDecimal targetGrossLine = lineNet.multiply(BigDecimal.ONE.add(p.vatPct.movePointLeft(2))).setScale(2, RoundingMode.HALF_UP);
        BigDecimal adjustedUnitNet = targetGrossLine.divide(p.qty.multiply(BigDecimal.ONE.add(p.vatPct.movePointLeft(2))), 2, RoundingMode.HALF_UP);
        
        // Speichere den angepassten Einzelpreis in der Prep-Instanz
        p.unitNetAdjusted = adjustedUnitNet;
        
        System.out.println("DEBUG: Line " + p.src.description + 
                          " - Original unit net: " + originalUnitNet + 
                          ", Target line net: " + lineNet + 
                          ", Adjusted unit net: " + adjustedUnitNet);

        Product prod = new Product();
        prod.setName(p.src.description)
            .setUnit(unit)
            .setVATPercent(p.vatPct);
        if (notBlank(p.src.taxCategory)) {
          prod.setTaxCategoryCode(p.src.taxCategory);
        }

        Item item = new Item(prod, adjustedUnitNet, p.qty);

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
          
          Item discountItem = new Item(discountProd, invoiceDiscount.negate(), BigDecimal.ONE);
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
            
            Item creditItem = new Item(creditProd, unitNet.abs().negate(), qty);
            inv.addItem(creditItem);
            System.out.println("Adding credit line item: " + l.description + " = " + unitNet.abs().multiply(qty));
          }
        }
      }
      
      // --- Rundungsausgleich je MwSt-Kategorie ---
      applyRoundingAdjustment(inv, dto.lines, dto.totals, preps);
      
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

  /**
   * Wendet Rundungsausgleich je MwSt-Kategorie an, um Differenzen zwischen
   * Brutto→Netto Umrechnung zu eliminieren.
   */
  private static void applyRoundingAdjustment(Invoice inv, List<Line> lines, TotalsDTO totals, List<Prep> preps) {
    if (lines == null || lines.isEmpty() || totals == null) {
      return;
    }

    System.out.println("DEBUG: Starting rounding adjustment analysis...");

    // Vereinfachter Ansatz: Verwende die Totals aus der Eingabe
    if (notBlank(totals.grandTotalGross)) {
      BigDecimal expectedGrossTotal = bd2(totals.grandTotalGross);
      System.out.println("DEBUG: Expected gross total from input: " + expectedGrossTotal);
      
      // Berechne die tatsächliche Brutto-Summe aus den Items
      // WICHTIG: Berechne erst die Gesamtsumme, dann runde (vermeidet Rundungsfehler)
      BigDecimal totalNetAmount = BigDecimal.ZERO;
      BigDecimal totalGrossAmount = BigDecimal.ZERO;
      BigDecimal mostCommonVatRate = BigDecimal.ZERO;
      
      // Verwende die gleichen angepassten Einzelpreise wie für die Mustang Library
      for (Prep p : preps) {
        if (!notBlank(p.src.description) || !notBlank(p.src.quantity)) {
          continue;
        }
        
        // WICHTIG: Berücksichtige auch negative Preise in der Rundungsberechnung
        // Diese werden als Credit Items behandelt und müssen in der Gesamtberechnung enthalten sein
        
      // Verwende die gleiche Logik wie in der Item-Erstellung
      // Berechne die gewünschte Netto-Positionssumme
      BigDecimal originalUnitNet = p.src.unitNetPriceBD();
      BigDecimal lineNet = originalUnitNet.multiply(p.qty).setScale(2, RoundingMode.HALF_UP);
      
      // Positionsrabatt berücksichtigen
      if (notBlank(p.src.discount)) {
        BigDecimal discNet = bd2(p.src.discount);
        if (discNet.compareTo(BigDecimal.ZERO) > 0) {
          lineNet = lineNet.subtract(discNet).setScale(2, RoundingMode.HALF_UP);
          if (lineNet.compareTo(BigDecimal.ZERO) < 0) lineNet = BigDecimal.ZERO;
        }
      }
      
      // WICHTIG: Verwende den bereits berechneten adjustedUnitNet für die korrekte Positionssumme
      // Das ist der Wert, der tatsächlich an Mustang übergeben wird
      // Für negative Preise verwende den ursprünglichen Wert (da sie als Credit Items behandelt werden)
      BigDecimal actualLineNet;
      if (p.unitNetOrig.compareTo(BigDecimal.ZERO) < 0) {
        // Für negative Preise: verwende den ursprünglichen Wert (wird als Credit Item hinzugefügt)
        actualLineNet = originalUnitNet.multiply(p.qty).setScale(2, RoundingMode.HALF_UP);
      } else {
        // Für positive Preise: verwende den angepassten Wert
        actualLineNet = p.unitNetAdjusted.multiply(p.qty).setScale(2, RoundingMode.HALF_UP);
      }
        
      // Berechne Brutto-Betrag für diese Zeile (2 Dezimalstellen) mit dem korrekten Netto-Betrag
      BigDecimal lineGross = actualLineNet.multiply(BigDecimal.ONE.add(p.vatPct.movePointLeft(2))).setScale(2, RoundingMode.HALF_UP);
      
      // Addiere zu Gesamtsummen
      totalNetAmount = totalNetAmount.add(actualLineNet);
      totalGrossAmount = totalGrossAmount.add(lineGross);
        
        // Speichere die häufigste MwSt-Rate (für die Gesamtberechnung)
        if (p.vatPct.compareTo(mostCommonVatRate) > 0) {
          mostCommonVatRate = p.vatPct;
        }
        
        System.out.println("DEBUG: Line " + p.src.description + " -> Net: " + actualLineNet + ", Gross: " + lineGross);
      }
      
      // Die Netto-Gesamtsumme ist bereits auf 2 Dezimalstellen gerundet
      BigDecimal actualNetTotal = totalNetAmount;
      
      // WICHTIG: Berechne Brutto-Gesamtsumme aus der Netto-Gesamtsumme (nicht durch Addition der Zeilen)
      // Verwende die häufigste MwSt-Rate (Standard: 19% falls keine gefunden)
      if (mostCommonVatRate.compareTo(BigDecimal.ZERO) == 0) {
        mostCommonVatRate = new BigDecimal("19");
      }
      BigDecimal actualGrossTotal = actualNetTotal.multiply(BigDecimal.ONE.add(mostCommonVatRate.movePointLeft(2)));
      actualGrossTotal = actualGrossTotal.setScale(2, RoundingMode.HALF_UP);
      
      System.out.println("DEBUG: Total Net: " + actualNetTotal + ", Total Gross: " + actualGrossTotal);
      
      System.out.println("DEBUG: Calculated gross total: " + actualGrossTotal + ", Expected: " + expectedGrossTotal);
      
      // Berechne die Differenz
      BigDecimal grossDelta = expectedGrossTotal.subtract(actualGrossTotal);
      System.out.println("DEBUG: Gross delta: " + grossDelta);
      
      // Wenn es eine signifikante Differenz gibt, füge einen Rundungsausgleich hinzu
      // Threshold für 2-Dezimalstellen-Rundung (mindestens 0.01 EUR Unterschied)
      if (grossDelta.abs().compareTo(new BigDecimal("0.01")) >= 0) {
        System.out.println("DEBUG: Applying gross total adjustment: " + grossDelta);
        
        // Runde den Anpassungsbetrag auf 2 Dezimalstellen für Konsistenz
        BigDecimal adjustmentAmount = grossDelta.setScale(2, RoundingMode.HALF_UP);
        
        VatCategoryInfo info = new VatCategoryInfo();
        info.vatPercent = new BigDecimal("19"); // Standard MwSt-Satz
        info.taxCategory = "S";
        
        if (adjustmentAmount.compareTo(BigDecimal.ZERO) > 0) {
          // Positive Differenz: Charge (Zuschlag) hinzufügen
          addRoundingAdjustment(inv, info, adjustmentAmount, false);
        } else {
          // Negative Differenz: Allowance (Rabatt) hinzufügen
          addRoundingAdjustment(inv, info, adjustmentAmount.abs(), true);
        }
      } else {
        System.out.println("DEBUG: No gross total adjustment needed");
      }
    }
  }
  
  /**
   * Berechnet das tatsächliche Netto für eine MwSt-Kategorie aus den bereits hinzugefügten Items.
   * Da die Mustang Library keine getItems() Methode hat, verwenden wir eine alternative Berechnung.
   */
  private static BigDecimal calculateActualNetForVatCategory(Invoice inv, BigDecimal vatPercent, String taxCategory) {
    // Da wir keinen direkten Zugriff auf die Items haben, verwenden wir eine vereinfachte Berechnung
    // Die tatsächliche Netto-Summe wird durch die Mustang Library intern berechnet
    // Für den Rundungsausgleich verwenden wir eine Schätzung basierend auf der Brutto-Summe
    
    System.out.println("DEBUG: Cannot access items directly from Mustang Invoice, using alternative calculation");
    return BigDecimal.ZERO; // Placeholder - wird durch die Mustang Library intern berechnet
  }
  
  /**
   * Fügt einen Rundungsausgleich als Allowance/Charge oder Fallback-Item hinzu.
   */
  private static void addRoundingAdjustment(Invoice inv, VatCategoryInfo info, BigDecimal amount, boolean isAllowance) {
    // Use fallback approach directly since Document-level Allowance/Charge has issues with VAT percentage
    System.out.println("INFO: Using fallback item approach for rounding adjustment");
    
    Product adjustmentProd = new Product();
    adjustmentProd.setName("Rundungsausgleich")
                 .setUnit("C62")
                 .setVATPercent(BigDecimal.ZERO); // Rundungsausgleich ist steuerfrei
    adjustmentProd.setTaxCategoryCode("E"); // E = Exempt (steuerfrei)
    
    // Verwende den exakten Betrag (bereits auf 2 Dezimalstellen gerundet)
    BigDecimal itemAmount = isAllowance ? amount.negate() : amount;
    
    Item adjustmentItem = new Item(adjustmentProd, itemAmount, BigDecimal.ONE);
    inv.addItem(adjustmentItem);
    
    System.out.println("INFO: Rundungsausgleich hinzugefügt - Kategorie: " + info.vatPercent + "%/" + info.taxCategory + 
                      ", Delta: " + (isAllowance ? "-" : "+") + amount + " EUR, Methode: Fallback-Item");
  }
  

  /**
   * Hilfsklasse für MwSt-Kategorie-Informationen.
   */
  private static class VatCategoryInfo {
    BigDecimal vatPercent = BigDecimal.ZERO;
    String taxCategory = "S";
    BigDecimal grossSum = BigDecimal.ZERO;
  }

  static class Prep {
    Line src;
    BigDecimal qty;
    BigDecimal vatPct;      // z.B. 19
    BigDecimal unitNetOrig; // aus net_price oder aus gross_price abgeleitet
    BigDecimal unitNetAdjusted; // der angepasste Einzelpreis für Mustang
  }
}
