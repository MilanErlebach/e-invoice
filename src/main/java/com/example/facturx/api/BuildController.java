package com.example.facturx.api;

import com.example.facturx.model.InvoiceDTO;
import com.example.facturx.service.FacturxService;
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
            @RequestPart("invoice") InvoiceDTO invoice,
            @RequestPart("pdf") MultipartFile pdf) {
        
        try {
            byte[] result = facturxService.buildFacturX(invoice, pdf);
            
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_PDF);
            headers.setContentDispositionFormData("attachment", "factur-x.pdf");
            
            return new ResponseEntity<>(result, headers, HttpStatus.OK);
            
        } catch (Exception e) {
            return new ResponseEntity<>(HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }
}