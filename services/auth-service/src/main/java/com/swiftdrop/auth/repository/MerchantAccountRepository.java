package com.swiftdrop.auth.repository;

import com.swiftdrop.auth.domain.MerchantAccount;
import com.swiftdrop.auth.domain.MerchantStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MerchantAccountRepository extends JpaRepository<MerchantAccount, UUID> {

    Optional<MerchantAccount> findByUserId(UUID userId);

    @Query("select m from MerchantAccount m join fetch m.user where m.status = :status order by m.createdAt")
    List<MerchantAccount> findByStatusWithUser(MerchantStatus status);
}
