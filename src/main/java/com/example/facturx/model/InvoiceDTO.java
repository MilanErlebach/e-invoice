package com.example.facturx.model;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public class InvoiceDTO {

  @JsonIgnoreProperties(ignoreUnknown = true)
  public static class PartyDTO {
    public String name;
    public String street;
    public String zip;
    public String city;
    public String country;

    @JsonProperty("vat_id")      public String vatId;
    @JsonProperty("tax_number")  public String taxNumber;

    public String iban;
    public String bic;
    public String email;

    @JsonProperty("buyer_reference") public String buyerReference;
    @JsonProperty("leitweg_id")      public String leitwegId;
  }

  @JsonIgnoreProperties(ignoreUnknown = true)
  public static class HeaderDTO {
    public String number;

    @JsonProperty("issue_date")   public String issueDate;   // YYYY-MM-DD
    @JsonProperty("service_from") public String serviceFrom; // YYYY-MM-DD (oder ISO-8601, wir schneiden auf Datum)
    @JsonProperty("service_to")   public String serviceTo;   // YYYY-MM-DD
    @JsonProperty("due_date")     public String dueDate;     // YYYY-MM-DD

    public String currency;
  }

  @JsonIgnoreProperties(ignoreUnknown = true)
  public static class PaymentDTO {
    public String method; // optional
    public String iban;
    public String bic;

    @JsonProperty("payment_status")
    public String paymentStatus; // "paid" or other values

    @JsonProperty("remittance_information")
    @JsonAlias({"remittance_info"})
    public String remittanceInformation;
  }

  @JsonIgnoreProperties(ignoreUnknown = true)
  public static class TotalsDTO {
    @JsonProperty("subtotal_gross")
    @JsonAlias({"subtotalGross","subtotal"})
    public String subtotalGross;      // inkl. MwSt vor Rabatt

    @JsonProperty("discount_gross")
    @JsonAlias({"discountGross","discount"})
    public String discountGross;      // absolut, inkl. MwSt

    @JsonProperty("grand_total_gross")
    @JsonAlias({"grandTotalGross","grand_total","total"})
    public String grandTotalGross;    // inkl. MwSt nach Rabatt
  }

  @JsonIgnoreProperties(ignoreUnknown = true)
  public static class Line {
    public String description;
    public String quantity;

    @JsonProperty("unit_code")
    public String unitCode;

    @JsonProperty("net_price")
    public String netPrice;     // optional (Netto-Einzelpreis)

    @JsonProperty("gross_price")
    public String grossPrice;   // optional (Brutto-Einzelpreis)

    @JsonProperty("tax_rate")
    public String taxRate;      // z.B. "19"

    @JsonProperty("tax_category")
    public String taxCategory;  // z.B. "S"

    public String discount;     // optional, Netto-Betrag pro Position

    /* Helpers */
    public BigDecimal quantityBD() {
      return new BigDecimal(quantity.replace(',', '.'));
    }
    public BigDecimal taxRatePercentBD() {
      return new BigDecimal(taxRate.replace(',', '.')); // 19
    }
    /** Netto-Einzelpreis: nimmt net_price, sonst berechnet aus gross_price. */
    public BigDecimal unitNetPriceBD() {
      if (netPrice != null && !netPrice.isBlank()) {
        return new BigDecimal(netPrice.replace(',', '.'));
      }
      if (grossPrice != null && !grossPrice.isBlank()) {
        BigDecimal gross = new BigDecimal(grossPrice.replace(',', '.'));
        BigDecimal divider = BigDecimal.ONE.add(taxRatePercentBD().divide(new BigDecimal("100")));
        return gross.divide(divider, 4, java.math.RoundingMode.HALF_UP);
      }
      throw new IllegalArgumentException("Line requires either net_price or gross_price");
    }
  }

  public PartyDTO seller;
  public PartyDTO buyer;

  @JsonProperty("invoice")
  public HeaderDTO header;

  public List<Line> lines;

  public PaymentDTO payment;

  public TotalsDTO totals;
}
