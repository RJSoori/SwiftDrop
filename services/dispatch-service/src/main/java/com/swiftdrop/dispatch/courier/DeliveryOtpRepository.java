package com.swiftdrop.dispatch.courier;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface DeliveryOtpRepository extends JpaRepository<DeliveryOtp, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select otp from DeliveryOtp otp where otp.parcelId = :parcelId")
    Optional<DeliveryOtp> findByParcelIdForUpdate(@Param("parcelId") UUID parcelId);
}
