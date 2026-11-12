package com.swiftdrop.parcel.repository;

import com.swiftdrop.parcel.domain.ParcelStatusHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ParcelStatusHistoryRepository extends JpaRepository<ParcelStatusHistory, UUID> {

    List<ParcelStatusHistory> findByParcelIdOrderByOccurredAtAsc(UUID parcelId);
}
