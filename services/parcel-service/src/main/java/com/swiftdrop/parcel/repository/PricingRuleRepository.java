package com.swiftdrop.parcel.repository;

import com.swiftdrop.parcel.domain.PricingRule;
import com.swiftdrop.parcel.domain.PricingStrategyType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface PricingRuleRepository extends JpaRepository<PricingRule, UUID> {

    Optional<PricingRule> findFirstByZoneIdOrderByValidFromDesc(UUID zoneId);

    Optional<PricingRule> findFirstByStrategyOrderByValidFromDesc(PricingStrategyType strategy);
}
