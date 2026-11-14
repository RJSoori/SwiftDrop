package com.swiftdrop.parcel.api;

import com.swiftdrop.parcel.dto.QuoteRequest;
import com.swiftdrop.parcel.dto.QuoteResponse;
import com.swiftdrop.parcel.service.ParcelService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Public quote calculation endpoint mapped to /api/v1/quotes.
 */
@RestController
@RequestMapping("/api/v1/quotes")
public class QuoteController {

    private final ParcelService parcelService;

    public QuoteController(ParcelService parcelService) {
        this.parcelService = parcelService;
    }

    @PostMapping
    public ResponseEntity<QuoteResponse> getQuote(@Valid @RequestBody QuoteRequest request) {
        QuoteResponse quote = parcelService.calculateQuote(request);
        return ResponseEntity.ok(quote);
    }
}
