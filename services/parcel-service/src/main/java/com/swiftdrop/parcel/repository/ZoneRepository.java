package com.swiftdrop.parcel.repository;

import com.swiftdrop.parcel.domain.Zone;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ZoneRepository extends JpaRepository<Zone, UUID> {

    Optional<Zone> findByName(String name);

    List<Zone> findByActiveTrue();
}
