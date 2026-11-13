package com.swiftdrop.auth.credentials;

import com.swiftdrop.auth.credentials.CredentialDtos.CreateCredentialRequest;
import com.swiftdrop.auth.credentials.CredentialDtos.CreatedCredential;
import com.swiftdrop.auth.credentials.CredentialDtos.CredentialSummary;
import com.swiftdrop.common.security.CurrentUser;
import jakarta.validation.Valid;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

/**
 * A signed-in, approved merchant manages the API keys and OAuth clients its own shop system uses.
 * Machine tokens (those with a {@code scope} claim) are refused, so a leaked orders:write token cannot mint more keys.
 */
@RestController
@RequestMapping("/api/v1/auth/merchant/credentials")
@PreAuthorize("hasRole('MERCHANT')")
public class CredentialController {

    private final CredentialService credentialService;

    public CredentialController(CredentialService credentialService) {
        this.credentialService = credentialService;
    }

    /** 201 with the secret. It is shown only in this response. */
    @PostMapping
    public ResponseEntity<CreatedCredential> create(@Valid @RequestBody CreateCredentialRequest request) {
        CreatedCredential created = credentialService.create(humanMerchant(), request.type(), request.name());
        return ResponseEntity.status(HttpStatus.CREATED).cacheControl(CacheControl.noStore()).body(created);
    }

    @GetMapping
    public List<CredentialSummary> list() {
        return credentialService.list(humanMerchant());
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void revoke(@PathVariable UUID id) {
        credentialService.revoke(humanMerchant(), id);
    }

    private static UUID humanMerchant() {
        if (CurrentUser.jwt().hasClaim("scope")) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "API credentials can only be managed from a signed-in merchant account");
        }
        return CurrentUser.id();
    }
}
