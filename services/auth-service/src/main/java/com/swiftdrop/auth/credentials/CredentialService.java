package com.swiftdrop.auth.credentials;

import com.swiftdrop.auth.domain.MerchantAccount;
import com.swiftdrop.auth.domain.MerchantStatus;
import com.swiftdrop.auth.service.SecretTokens;
import com.swiftdrop.auth.service.TokenService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.UUID;

/**
 * Merchant API credentials: issuing (secret returned once, SHA-256 stored) and turning an API key into a short-lived
 * merchant JWT for the gateway.
 */
@Service
public class CredentialService {

    /** Every API key starts with this, so leaked keys are easy to recognise in logs and secret scanners. */
    public static final String API_KEY_PREFIX = "sd_live_";
    /** Characters of the random part kept in key_prefix and shown in the UI, e.g. sd_live_4f2aQx. */
    static final int VISIBLE_CHARS = 6;
    static final String ORDERS_WRITE = "orders:write";
    /** Reported instead of the merchant status when the merchant's login has been disabled by an admin. */
    static final String DISABLED = "DISABLED";

    /** The credential as stored plus the raw secret, which the caller shows once and never again. */
    public record Issued(ApiCredential credential, String secret) {
    }

    /** Gateway contract: merchantId, merchant status, and a JWT only when the merchant may call the API. */
    public record Introspection(UUID merchantId, String status, String token) {
    }

    private final ApiCredentialRepository credentials;
    private final TokenService tokens;

    public CredentialService(ApiCredentialRepository credentials, TokenService tokens) {
        this.credentials = credentials;
        this.tokens = tokens;
    }

    /** Creates an API key for {@code merchant}. Authorisation (approved merchant only) is the caller's job. */
    @Transactional
    Issued issueApiKey(MerchantAccount merchant, String name) {
        String random = SecretTokens.random();
        String secret = API_KEY_PREFIX + random;
        String visiblePrefix = API_KEY_PREFIX + random.substring(0, VISIBLE_CHARS);
        ApiCredential saved = credentials.save(new ApiCredential(merchant, ApiCredentialType.API_KEY, name.trim(),
                visiblePrefix, SecretTokens.sha256(secret), ORDERS_WRITE));
        return new Issued(saved, secret);
    }

    /**
     * 401 for an unknown or revoked key. A known key always gets 200 with the merchant's status, so the gateway can
     * answer 403 for PENDING/SUSPENDED/REJECTED/DISABLED; the token is only issued for an active, approved merchant.
     */
    @Transactional
    public Introspection introspect(String apiKey) {
        ApiCredential credential = credentials.findBySecretHashAndType(SecretTokens.sha256(apiKey), ApiCredentialType.API_KEY)
                .filter(c -> !c.isRevoked())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid API key"));
        credential.markUsed(Instant.now());

        MerchantAccount merchant = credential.getMerchant();
        boolean active = merchant.getUser().isActive();
        String status = active ? merchant.getStatus().name() : DISABLED;
        String token = active && merchant.getStatus() == MerchantStatus.APPROVED
                ? tokens.issueMerchantApiToken(merchant, credential.getScopes())
                : null;
        return new Introspection(MerchantIdentity.of(merchant), status, token);
    }
}
