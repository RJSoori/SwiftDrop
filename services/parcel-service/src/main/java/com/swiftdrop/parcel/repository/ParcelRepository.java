package com.swiftdrop.parcel.repository;

import com.swiftdrop.parcel.domain.Parcel;
import com.swiftdrop.parcel.domain.ParcelStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ParcelRepository extends JpaRepository<Parcel, UUID> {

    Optional<Parcel> findByTrackingNo(String trackingNo);

    List<Parcel> findByCustomerIdOrderByCreatedAtDesc(UUID customerId);

    List<Parcel> findByCourierIdOrderByCreatedAtDesc(UUID courierId);

    List<Parcel> findByCourierIdAndCurrentStatus(UUID courierId, ParcelStatus currentStatus);

    List<Parcel> findByCurrentStatus(ParcelStatus currentStatus);

    List<Parcel> findByCourierIdIsNullAndCurrentStatus(ParcelStatus currentStatus);

    boolean existsByTrackingNo(String trackingNo);
}
