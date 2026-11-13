package com.swiftdrop.auth.credentials;

import com.swiftdrop.auth.credentials.CredentialDtos.CreatedCredential;
import com.swiftdrop.auth.credentials.CredentialDtos.CredentialSummary;
import com.swiftdrop.auth.credentials.CredentialDtos.TokenResponse;
import com.swiftdrop.auth.domain.AuditLogEntry;
import com.swiftdrop.auth.domain.MerchantAccount;
import com.swiftdrop.auth.domain.MerchantStatus;
import com.swiftdrop.auth.repository.AuditLogRepository;
import com.swiftdrop.auth.repository.MerchantAccountRepository;
import com.swiftdrop.auth.service.SecretTokens;
import com.swiftdrop.auth.service.TokenService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.ErrorResponseException;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Merchant API credentials: issuing (secret returned once, SHA-256 stored), listing, revoking, turning an API key
 * into a short-lived merchant JWT for the gateway, and the OAuth 2.0 client-credentials grant.
 * API keys authenticate a merchant's system to the gateway; the JWTs issued here are what authorise each request.
 */
@Service
public class CredentialService {

    /** Every API key starts with this, so leaked keys are easy to recognise in logs and secret scanners. */
    public static final String API_KEY_PREFIX = "sd_live_";
    public static final String CLIENT_ID_PREFIX = "sd_client_";
    public static final String CLIENT_SECRET_PREFIX = "sd_secret_";
    /** Characters of the random part kept in key_prefix and shown in the UI, e.g. sd_live_4f2aQx. */
    static final int VISIBLE_CHARS = 6;
    static final int CLIENT_ID_RANDOM_CHARS = 16;
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
    private final MerchantAccountRepository merchants;
    private final AuditLogRepository auditLog;
    private final TokenService tokens;

    public CredentialService(ApiCredentialRepository credentials, MerchantAccountRepository merchants,
                             AuditLogRepository auditLog, TokenService tokens) {
        this.credentials = credentials;
        this.merchants = merchants;
        this.auditLog = auditLog;
        this.tokens = tokens;
    }

    // ---------- merchant self-service ----------

    /** 403 unless {@code userId} is an active, approved merchant (checked in the database, not the token claim). */
    @Transactional
    public CreatedCredential create(UUID userId, ApiCredentialType type, String name) {
        Issued issued = issue(approvedMerchant(userId), type, name);
        auditLog.save(new AuditLogEntry(userId, "API_CREDENTIAL_CREATED",
                "api_credential:" + issued.credential().getId() + ":" + type));
        return new CreatedCredential(CredentialSummary.of(issued.credential()), issued.secret());
    }

    /** Newest first, revoked ones included so the merchant can see what was revoked and when. */
    @Transactional(readOnly = true)
    public List<CredentialSummary> list(UUID userId) {
        return credentials.findByMerchant_IdOrderByCreatedAtDesc(approvedMerchant(userId).getId()).stream()
                .map(CredentialSummary::of)
                .toList();
    }

    /** Sets revoked_at (the row is kept). Revoking twice is harmless; another merchant's id is 404. */
    @Transactional
    public void revoke(UUID userId, UUID credentialId) {
        ApiCredential credential = credentials.findByIdAndMerchant_Id(credentialId, approvedMerchant(userId).getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Credential not found"));
        if (!credential.isRevoked()) {
            credential.revoke(Instant.now());
            auditLog.save(new AuditLogEntry(userId, "API_CREDENTIAL_REVOKED", "api_credential:" + credentialId));
        }
    }

    /** Creates an API key for {@code merchant}. Authorisation is the caller's job. */
    @Transactional
    Issued issueApiKey(MerchantAccount merchant, String name) {
        return issue(merchant, ApiCredentialType.API_KEY, name);
    }

    /** Creates a credential of either type for {@code merchant}. Authorisation is the caller's job. */
    @Transactional
    Issued issue(MerchantAccount merchant, ApiCredentialType type, String name) {
        String random = SecretTokens.random();
        String secret;
        String keyPrefix;
        if (type == ApiCredentialType.API_KEY) {
            secret = API_KEY_PREFIX + random;
            keyPrefix = API_KEY_PREFIX + random.substring(0, VISIBLE_CHARS);
        } else {
            secret = CLIENT_SECRET_PREFIX + random;
            keyPrefix = CLIENT_ID_PREFIX + SecretTokens.random().substring(0, CLIENT_ID_RANDOM_CHARS);
        }
        ApiCredential saved = credentials.save(new ApiCredential(merchant, type, name.trim(), keyPrefix,
                SecretTokens.sha256(secret), ORDERS_WRITE));
        return new Issued(saved, secret);
    }

    // ---------- gateway introspection ----------

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
        String status = statusOf(merchant);
        String token = MerchantStatus.APPROVED.name().equals(status)
                ? tokens.issueMerchantApiToken(merchant, credential.getScopes())
                : null;
        return new Introspection(MerchantIdentity.of(merchant), status, token);
    }

    // ---------- OAuth 2.0 client credentials (RFC 6749 section 4.4) ----------

    /**
     * Exchanges a client_id and secret for a 15-minute merchant JWT. Errors are RFC 7807 problems that also carry the
     * RFC 6749 {@code error} code: invalid_client (401) or unauthorized_client (403, merchant not active and approved).
     */
    @Transactional
    public TokenResponse clientCredentialsToken(String clientId, String clientSecret) {
        Optional<ApiCredential> found = credentials.findClient(clientId);
        // Hash even when the client is unknown, so a wrong client_id and a wrong secret take the same time.
        byte[] given = SecretTokens.sha256(clientSecret).getBytes(StandardCharsets.UTF_8);
        ApiCredential credential = found
                .filter(c -> MessageDigest.isEqual(given, c.getSecretHash().getBytes(StandardCharsets.UTF_8)))
                .filter(c -> !c.isRevoked())
                .orElseThrow(() -> oauthError(HttpStatus.UNAUTHORIZED, "invalid_client", "Invalid client credentials"));

        MerchantAccount merchant = credential.getMerchant();
        if (!MerchantStatus.APPROVED.name().equals(statusOf(merchant))) {
            throw oauthError(HttpStatus.FORBIDDEN, "unauthorized_client", "Merchant account is not active");
        }
        credential.markUsed(Instant.now());
        String token = tokens.issueMerchantApiToken(merchant, credential.getScopes());
        return new TokenResponse(token, "Bearer", TokenService.MERCHANT_API_TOKEN_TTL.toSeconds(), credential.getScopes());
    }

    /** RFC 7807 problem with the RFC 6749 error code as an extra {@code error} property. */
    static ErrorResponseException oauthError(HttpStatus status, String error, String detail) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setProperty("error", error);
        ErrorResponseException exception = new ErrorResponseException(status, problem, null);
        if (status == HttpStatus.UNAUTHORIZED) {
            exception.getHeaders().set(HttpHeaders.WWW_AUTHENTICATE, "Basic realm=\"swiftdrop\"");
        }
        return exception;
    }

    // ---------- helpers ----------

    private MerchantAccount approvedMerchant(UUID userId) {
        MerchantAccount merchant = merchants.findByUserId(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN, "Only merchants can manage API credentials"));
        if (!MerchantStatus.APPROVED.name().equals(statusOf(merchant))) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Merchant account is not approved");
        }
        return merchant;
    }

    /** The merchant's status, or DISABLED when an admin has disabled the merchant's login. */
    private static String statusOf(MerchantAccount merchant) {
        return merchant.getUser().isActive() ? merchant.getStatus().name() : DISABLED;
    }
}
