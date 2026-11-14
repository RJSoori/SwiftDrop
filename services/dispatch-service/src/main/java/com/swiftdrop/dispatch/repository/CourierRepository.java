package com.swiftdrop.dispatch.repository;

import com.swiftdrop.dispatch.domain.Courier;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface CourierRepository extends JpaRepository<Courier, UUID> {
    List<Courier> findByActiveTrueOrderByFullNameAsc();
}
