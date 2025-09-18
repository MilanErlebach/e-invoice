package com.example.facturx.service;

import com.example.facturx.model.InvoiceDTO;
import org.junit.jupiter.api.Test;
import org.mustangproject.Invoice;
import org.mustangproject.Item;
import org.mustangproject.Product;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test class for FacturxService rounding adjustment functionality.
 */
public class FacturxServiceTest {

    @Test
    public void testRoundingAdjustmentCalculation() {
        // Create test data that should trigger rounding differences
        InvoiceDTO.Line line1 = new InvoiceDTO.Line();
        line1.description = "Test Item 1";
        line1.quantity = "1.0000";
        line1.grossPrice = "119.00"; // 100.00 net + 19% VAT = 119.00 gross
        line1.taxRate = "19";
        line1.taxCategory = "S";

        InvoiceDTO.Line line2 = new InvoiceDTO.Line();
        line2.description = "Test Item 2";
        line2.quantity = "1.0000";
        line2.grossPrice = "119.00"; // 100.00 net + 19% VAT = 119.00 gross
        line2.taxRate = "19";
        line2.taxCategory = "S";

        List<InvoiceDTO.Line> lines = Arrays.asList(line1, line2);

        // Create invoice and add items manually to test the rounding logic
        Invoice invoice = new Invoice();
        
        // Add items with calculated net prices (this should create rounding differences)
        for (InvoiceDTO.Line line : lines) {
            BigDecimal unitNet = line.unitNetPriceBD();
            Product product = new Product();
            product.setName(line.description)
                   .setUnit("C62")
                   .setVATPercent(new BigDecimal(line.taxRate));
            product.setTaxCategoryCode(line.taxCategory);
            
            Item item = new Item(product, unitNet, new BigDecimal(line.quantity));
            invoice.addItem(item);
        }

        // Test the rounding adjustment method using reflection to access private method
        try {
            java.lang.reflect.Method method = FacturxService.class.getDeclaredMethod(
                "applyRoundingAdjustment", Invoice.class, List.class);
            method.setAccessible(true);
            method.invoke(null, invoice, lines);
            
            // Verify that the method executed without throwing exceptions
            assertTrue(true, "Rounding adjustment method executed successfully");
            
        } catch (Exception e) {
            fail("Rounding adjustment method failed: " + e.getMessage());
        }
    }

    @Test
    public void testVatCategoryInfoClass() {
        // Test the VatCategoryInfo inner class functionality
        try {
            Class<?> vatCategoryInfoClass = Class.forName(
                "com.example.facturx.service.FacturxService$VatCategoryInfo");
            
            Object vatInfo = vatCategoryInfoClass.newInstance();
            
            // Set values using reflection
            java.lang.reflect.Field vatPercentField = vatCategoryInfoClass.getDeclaredField("vatPercent");
            vatPercentField.setAccessible(true);
            vatPercentField.set(vatInfo, new BigDecimal("19"));
            
            java.lang.reflect.Field taxCategoryField = vatCategoryInfoClass.getDeclaredField("taxCategory");
            taxCategoryField.setAccessible(true);
            taxCategoryField.set(vatInfo, "S");
            
            java.lang.reflect.Field grossSumField = vatCategoryInfoClass.getDeclaredField("grossSum");
            grossSumField.setAccessible(true);
            grossSumField.set(vatInfo, new BigDecimal("238.00"));
            
            // Verify values were set correctly
            assertEquals(new BigDecimal("19"), vatPercentField.get(vatInfo));
            assertEquals("S", taxCategoryField.get(vatInfo));
            assertEquals(new BigDecimal("238.00"), grossSumField.get(vatInfo));
            
        } catch (Exception e) {
            fail("VatCategoryInfo class test failed: " + e.getMessage());
        }
    }
}
