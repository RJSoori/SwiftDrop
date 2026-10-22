package com.swiftdrop.auth.repository;

import com.swiftdrop.auth.domain.OAuthAccount;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface OAuthAccountRepository extends JpaRepository<OAuthAccount, UUID> {

    Optional<OAuthAccount> findByProviderAndProviderSubject(String provider, String providerSubject);
}
