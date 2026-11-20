package com.swiftdrop.auth.credentials;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.UUID;

/** Request and response bodies of the merchant credentials and OAuth token endpoints. */
public final class CredentialDtos {

    private CredentialDtos() {
    }

    public record CreateCredentialRequest(
            @NotNull ApiCredentialType type,
            @NotBlank @Size(max = 80) String name) {
    }

    /**
     * One credential as listed. {@code prefix} is the visible start of an API key; for a CLIENT it is the full
     * client_id (also given as {@code clientId}). Never contains the secret or its hash.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record CredentialSummary(UUID id, ApiCredentialType type, String name, String prefix, String clientId,
                                    String scopes, Instant createdAt, Instant lastUsedAt, Instant revokedAt) {

        static CredentialSummary of(ApiCredential c) {
            String clientId = c.getType() == ApiCredentialType.CLIENT ? c.getKeyPrefix() : null;
            return new CredentialSummary(c.getId(), c.getType(), c.getName(), c.getKeyPrefix(), clientId, c.getScopes(),
                    c.getCreatedAt(), c.getLastUsedAt(), c.getRevokedAt());
        }
    }

    /** Returned once by POST: the summary plus the raw secret, which cannot be retrieved again. */
    public record CreatedCredential(CredentialSummary credential, String secret) {
    }

    /** RFC 6749 section 5.1 token response. */
    public record TokenResponse(
            @JsonProperty("access_token") String accessToken,
            @JsonProperty("token_type") String tokenType,
            @JsonProperty("expires_in") long expiresIn,
            String scope) {
    }
}
