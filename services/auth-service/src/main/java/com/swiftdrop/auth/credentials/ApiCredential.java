package com.swiftdrop.auth.credentials;

import com.swiftdrop.auth.domain.MerchantAccount;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

/** One merchant API key or client. Only the SHA-256 of the secret is stored; revoking keeps the row. */
@Entity
@Table(name = "api_credentials")
public class ApiCredential {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "merchant_id")
    private MerchantAccount merchant;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ApiCredentialType type;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String keyPrefix;

    @Column(nullable = false, unique = true)
    private String secretHash;

    @Column(nullable = false)
    private String scopes;

    @CreationTimestamp
    @Column(updatable = false)
    private Instant createdAt;

    private Instant lastUsedAt;

    private Instant revokedAt;

    protected ApiCredential() {
    }

    public ApiCredential(MerchantAccount merchant, ApiCredentialType type, String name, String keyPrefix,
                         String secretHash, String scopes) {
        this.merchant = merchant;
        this.type = type;
        this.name = name;
        this.keyPrefix = keyPrefix;
        this.secretHash = secretHash;
        this.scopes = scopes;
    }

    public boolean isRevoked() {
        return revokedAt != null;
    }

    public void markUsed(Instant now) {
        this.lastUsedAt = now;
    }

    public void revoke(Instant now) {
        if (revokedAt == null) {
            this.revokedAt = now;
        }
    }

    public UUID getId() {
        return id;
    }

    public MerchantAccount getMerchant() {
        return merchant;
    }

    public ApiCredentialType getType() {
        return type;
    }

    public String getName() {
        return name;
    }

    public String getKeyPrefix() {
        return keyPrefix;
    }

    public String getSecretHash() {
        return secretHash;
    }

    public String getScopes() {
        return scopes;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getLastUsedAt() {
        return lastUsedAt;
    }

    public Instant getRevokedAt() {
        return revokedAt;
    }
}
