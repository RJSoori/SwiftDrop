package com.swiftdrop.auth.credentials;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Service-to-service only (X-Internal-Token). Called by the gateway's MerchantApiKeyFilter. */
@RestController
@RequestMapping("/internal/v1/api-keys")
public class InternalApiKeyController {

    public record IntrospectRequest(@NotBlank @Size(max = 200) String apiKey) {
    }

    private final CredentialService credentialService;

    public InternalApiKeyController(CredentialService credentialService) {
        this.credentialService = credentialService;
    }

    @PostMapping("/introspect")
    public CredentialService.Introspection introspect(@Valid @RequestBody IntrospectRequest request) {
        return credentialService.introspect(request.apiKey());
    }
}
