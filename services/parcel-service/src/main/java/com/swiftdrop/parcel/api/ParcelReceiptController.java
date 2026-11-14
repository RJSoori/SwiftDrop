package com.swiftdrop.parcel.api;

import com.swiftdrop.common.security.CurrentUser;
import com.swiftdrop.parcel.service.ParcelDocumentService;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/parcels")
public class ParcelReceiptController {

    private final ParcelDocumentService documentService;

    public ParcelReceiptController(ParcelDocumentService documentService) {
        this.documentService = documentService;
    }

    @GetMapping("/{id}/receipt.pdf")
    @PreAuthorize("hasAnyRole('CUSTOMER', 'MERCHANT')")
    public ResponseEntity<byte[]> downloadReceipt(@PathVariable UUID id) {
        byte[] receipt = documentService.createReceipt(id, CurrentUser.id(), CurrentUser.roles());
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename("receipt-" + id + ".pdf").build().toString())
                .body(receipt);
    }

    @GetMapping("/{id}/label.pdf")
    @PreAuthorize("hasAnyRole('CUSTOMER', 'MERCHANT')")
    public ResponseEntity<byte[]> downloadLabel(@PathVariable UUID id) {
        byte[] label = documentService.createLabel(id, CurrentUser.id(), CurrentUser.roles());
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename("label-" + id + ".pdf").build().toString())
                .body(label);
    }
}
