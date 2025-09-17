package com.example.facturx.api;

import com.example.facturx.model.InvoiceDTO;
import com.example.facturx.service.FacturxService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api")
public class BuildController {

    @Autowired
    private FacturxService facturxService;

    @PostMapping(value = "/build", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<byte[]> buildFacturX(
            @RequestPart("invoice") String invoiceJson,
            @RequestPart("pdf") MultipartFile pdf) {
        
        try {
            // Parse invoice JSON from text or file part
            ObjectMapper mapper = new ObjectMapper();
            InvoiceDTO invoice = mapper.readValue(invoiceJson, InvoiceDTO.class);

            System.out.println("Received invoice JSON length: " + (invoiceJson != null ? invoiceJson.length() : 0));
            System.out.println("Received PDF: " + pdf.getOriginalFilename() + ", size: " + pdf.getSize());
            
            byte[] result = facturxService.buildFacturX(invoice, pdf);
            
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_PDF);
            headers.setContentDispositionFormData("attachment", "factur-x.pdf");
            
            return new ResponseEntity<>(result, headers, HttpStatus.OK);
            
        } catch (JsonProcessingException e) {
            System.err.println("Invalid invoice JSON: " + e.getMessage());
            return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
        } catch (Exception e) {
            System.err.println("Error in buildFacturX: " + e.getMessage());
            e.printStackTrace();
            return new ResponseEntity<>(HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }
}