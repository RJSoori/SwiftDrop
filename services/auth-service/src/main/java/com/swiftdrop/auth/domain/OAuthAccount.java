package com.swiftdrop.auth.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

/** Links an external identity (e.g. Google {@code sub}) to a SwiftDrop user. */
@Entity
@Table(name = "oauth_accounts")
public class OAuthAccount {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private UUID userId;

    @Column(nullable = false)
    private String provider;

    @Column(nullable = false)
    private String providerSubject;

    @CreationTimestamp
    @Column(updatable = false)
    private Instant createdAt;

    protected OAuthAccount() {
    }

    public OAuthAccount(UUID userId, String provider, String providerSubject) {
        this.userId = userId;
        this.provider = provider;
        this.providerSubject = providerSubject;
    }

    public UUID getUserId() {
        return userId;
    }

    public String getProvider() {
        return provider;
    }

    public String getProviderSubject() {
        return providerSubject;
    }
}
