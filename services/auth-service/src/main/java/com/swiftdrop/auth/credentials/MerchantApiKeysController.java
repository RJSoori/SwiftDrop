package com.swiftdrop.auth.credentials;

import com.swiftdrop.auth.credentials.CredentialDtos.CreatedCredential;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * API-key endpoints used by the merchant Webhooks page (M5/M6): same paths and response shapes as before, now backed
 * by {@link CredentialService}, so keys created here are the same keys the gateway introspects and that show on the
 * API keys page. Only API_KEY credentials are listed; OAuth clients are managed through
 * /api/v1/auth/merchant/credentials (the API keys page).
 */
@RestController
@RequestMapping("/api/v1/auth/merchant/api-keys")
@PreAuthorize("hasRole('MERCHANT')")
public class MerchantApiKeysController {

    static final String DEFAULT_NAME = "API key";

    /** POST response: the raw key appears here once. */
    public record NewKey(UUID id, String apiKey, String prefix) {
    }

    public record KeyView(UUID id, String prefix, Instant createdAt, Instant revokedAt) {
    }

    private final CredentialService credentialService;

    public MerchantApiKeysController(CredentialService credentialService) {
        this.credentialService = credentialService;
    }

    @PostMapping
    public ResponseEntity<NewKey> create() {
        CreatedCredential created = credentialService.create(CredentialController.humanMerchant(),
                ApiCredentialType.API_KEY, DEFAULT_NAME);
        NewKey body = new NewKey(created.credential().id(), created.secret(), created.credential().prefix());
        return ResponseEntity.status(HttpStatus.CREATED).cacheControl(CacheControl.noStore()).body(body);
    }

    @GetMapping
    public List<KeyView> list() {
        return credentialService.list(CredentialController.humanMerchant()).stream()
                .filter(c -> c.type() == ApiCredentialType.API_KEY)
                .map(c -> new KeyView(c.id(), c.prefix(), c.createdAt(), c.revokedAt()))
                .toList();
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void revoke(@PathVariable UUID id) {
        credentialService.revoke(CredentialController.humanMerchant(), id);
    }
}
