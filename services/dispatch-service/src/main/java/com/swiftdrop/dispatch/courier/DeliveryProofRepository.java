package com.swiftdrop.dispatch.courier;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface DeliveryProofRepository extends JpaRepository<DeliveryProof, UUID> {

    Optional<DeliveryProof> findByIdAndParcelIdAndCourierId(UUID id, UUID parcelId, UUID courierId);
}
