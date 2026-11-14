package com.swiftdrop.auth.credentials;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ApiCredentialRepository extends JpaRepository<ApiCredential, UUID> {

    /** Loads the owning merchant and its user too, since introspection needs both statuses. */
    @Query("select c from ApiCredential c join fetch c.merchant m join fetch m.user "
            + "where c.secretHash = :secretHash and c.type = :type")
    Optional<ApiCredential> findBySecretHashAndType(String secretHash, ApiCredentialType type);

    /** A CLIENT by its client_id (stored in key_prefix), with merchant and user, for the token endpoint. */
    @Query("select c from ApiCredential c join fetch c.merchant m join fetch m.user "
            + "where c.keyPrefix = :clientId and c.type = com.swiftdrop.auth.credentials.ApiCredentialType.CLIENT")
    Optional<ApiCredential> findClient(String clientId);

    List<ApiCredential> findByMerchant_IdOrderByCreatedAtDesc(UUID merchantAccountId);

    Optional<ApiCredential> findByIdAndMerchant_Id(UUID id, UUID merchantAccountId);
}
