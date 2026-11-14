package com.swiftdrop.auth.api;

import com.swiftdrop.auth.service.MerchantKeyService;
import com.swiftdrop.common.security.CurrentUser;

import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/auth/merchant/api-keys")
@PreAuthorize("hasRole('MERCHANT') and principal.claims['merchantStatus'] == 'APPROVED'")
public class MerchantKeyController {
    private final MerchantKeyService keys;

    public MerchantKeyController(MerchantKeyService keys) {
        this.keys = keys;
    }

    @PostMapping
    public ResponseEntity<MerchantKeyService.NewKey> create() {
        return ResponseEntity.status(201)
                .cacheControl(CacheControl.noStore())
                .body(keys.create(CurrentUser.id()));
    }

    @GetMapping
    public List<MerchantKeyService.KeyView> list() {
        return keys.list(CurrentUser.id());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> revoke(@PathVariable UUID id) {
        keys.revoke(CurrentUser.id(), id);
        return ResponseEntity.noContent().build();
    }
}
