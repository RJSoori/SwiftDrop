package com.swiftdrop.auth.api;

import com.swiftdrop.auth.service.MerchantKeyService;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/internal/v1/api-keys")
public class ApiKeyIntrospectionController {
    public record Request(@NotBlank @Size(max = 200) String apiKey) {}

    private final MerchantKeyService keys;

    public ApiKeyIntrospectionController(MerchantKeyService keys) {
        this.keys = keys;
    }

    @PostMapping("/introspect")
    public ResponseEntity<MerchantKeyService.Introspection> introspect(
            @Valid @RequestBody Request request) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(keys.introspect(request.apiKey()));
    }
}
