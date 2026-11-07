package com.swiftdrop.parcel.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.swiftdrop.parcel.domain.*;
import com.swiftdrop.parcel.domain.pricing.PricingStrategy;
import com.swiftdrop.parcel.domain.state.ParcelStateMachine;
import com.swiftdrop.parcel.dto.*;
import com.swiftdrop.parcel.repository.OutboxEventRepository;
import com.swiftdrop.parcel.repository.ParcelRepository;
import com.swiftdrop.parcel.repository.ParcelStatusHistoryRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.security.SecureRandom;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Service
public class ParcelService {

    private static final Logger log = LoggerFactory.getLogger(ParcelService.class);
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final String ALPHANUMERIC = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";

    private final ParcelRepository parcelRepository;
    private final ParcelStatusHistoryRepository historyRepository;
    private final OutboxEventRepository outboxEventRepository;
    private final ParcelStateMachine parcelStateMachine;
    private final Map<String, PricingStrategy> pricingStrategies;
    private final ObjectMapper objectMapper;

    // Default rates if zone rules are not in DB
    private static final BigDecimal DEFAULT_BASE_FEE = new BigDecimal("350.00");
    private static final BigDecimal DEFAULT_PER_KG_FEE = new BigDecimal("80.00");

    public ParcelService(ParcelRepository parcelRepository,
                         ParcelStatusHistoryRepository historyRepository,
                         OutboxEventRepository outboxEventRepository,
                         ParcelStateMachine parcelStateMachine,
                         Map<String, PricingStrategy> pricingStrategies,
                         ObjectMapper objectMapper) {
        this.parcelRepository = parcelRepository;
        this.historyRepository = historyRepository;
        this.outboxEventRepository = outboxEventRepository;
        this.parcelStateMachine = parcelStateMachine;
        this.pricingStrategies = pricingStrategies;
        this.objectMapper = objectMapper;
    }

    /**
     * Calculates delivery fee estimate based on selected strategy, weight, and dimensions.
     */
    public QuoteResponse calculateQuote(QuoteRequest request) {
        Dimensions dims = resolveDimensions(request.lengthCm(), request.widthCm(), request.heightCm());
        BigDecimal actualWeight = request.weightKg() != null ? request.weightKg() : BigDecimal.ONE;
        BigDecimal volWeight = dims.calculateVolumetricWeight();
        BigDecimal chargeableWeight = actualWeight.max(volWeight);

        PricingStrategy strategy = resolveStrategy(request.strategy());
        BigDecimal fee = strategy.calculateFee(actualWeight, dims, DEFAULT_BASE_FEE, DEFAULT_PER_KG_FEE);

        return QuoteResponse.of(fee, actualWeight, volWeight, chargeableWeight, request.strategy());
    }

    /**
     * Creates a new parcel delivery, saves history record, and publishes an outbox event.
     */
    @Transactional
    public ParcelResponse createParcel(CreateParcelRequest request, UUID callerId) {
        QuoteRequest quoteReq = new QuoteRequest(
                request.weightKg(), request.lengthCm(), request.widthCm(), request.heightCm(),
                request.strategy(), request.pickupZoneId(), request.dropoffZoneId()
        );
        QuoteResponse quote = calculateQuote(quoteReq);

        String trackingNo = generateUniqueTrackingNumber();
        UUID customerId = callerId;
        UUID merchantId = request.merchantId();

        Parcel parcel = new Parcel(
                trackingNo, customerId, quote.actualWeightKg(), request.size(),
                quote.feeLkr(), request.pickupAddress(), request.dropoffAddress()
        );
        parcel.setMerchantId(merchantId);
        parcel.setPickupZoneId(request.pickupZoneId());
        parcel.setDropoffZoneId(request.dropoffZoneId());
        parcel = parcelRepository.save(parcel);

        // Append initial audit record to status_history
        ParcelStatusHistory initialHistory = new ParcelStatusHistory(
                parcel.getId(), null, ParcelStatus.CREATED, callerId,
                "Parcel booking created via SwiftDrop API", null, null
        );
        historyRepository.save(initialHistory);

        // Write transactional outbox event
        recordOutboxEvent(parcel.getId(), "parcel.created", Map.of(
                "parcelId", parcel.getId().toString(),
                "trackingNo", parcel.getTrackingNo(),
                "customerId", customerId != null ? customerId.toString() : "",
                "merchantId", merchantId != null ? merchantId.toString() : "",
                "status", parcel.getCurrentStatus().name(),
                "feeLkr", parcel.getFeeLkr().toString()
        ));

        log.info("Created parcel ID {} with tracking number {}", parcel.getId(), parcel.getTrackingNo());
        return ParcelResponse.from(parcel);
    }

    /**
     * Updates parcel status according to the finite state machine transitions.
     */
    @Transactional
    public ParcelResponse updateStatus(UUID parcelId, UpdateStatusRequest request, UUID actorId) {
        Parcel parcel = parcelRepository.findById(parcelId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Parcel not found: " + parcelId));

        ParcelStatus current = parcel.getCurrentStatus();
        ParcelStatus target = request.status();

        if (target == null) {
            throw new IllegalArgumentException("Target status must not be null");
        }

        // Validate state transition through ParcelStateMachine
        parcelStateMachine.transition(current, target);

        parcel.setCurrentStatus(target);
        parcel = parcelRepository.save(parcel);

        // Append to history table
        ParcelStatusHistory history = new ParcelStatusHistory(
                parcel.getId(), current, target, actorId,
                request.note(), request.proofUrl(), request.failureReason()
        );
        historyRepository.save(history);

        // Write transactional outbox event
        recordOutboxEvent(parcel.getId(), "parcel.status.changed", Map.of(
                "parcelId", parcel.getId().toString(),
                "trackingNo", parcel.getTrackingNo(),
                "fromStatus", current.name(),
                "toStatus", target.name(),
                "actorId", actorId != null ? actorId.toString() : ""
        ));

        log.info("Updated parcel {} status from {} to {}", parcel.getTrackingNo(), current, target);
        return ParcelResponse.from(parcel);
    }

    /**
     * Assigns a courier to an unassigned parcel.
     */
    @Transactional
    public ParcelResponse assignCourier(UUID parcelId, UUID courierId, UUID actorId) {
        Parcel parcel = parcelRepository.findById(parcelId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Parcel not found: " + parcelId));

        parcel.setCourierId(courierId);
        parcel = parcelRepository.save(parcel);

        ParcelStatusHistory history = new ParcelStatusHistory(
                parcel.getId(), parcel.getCurrentStatus(), parcel.getCurrentStatus(), actorId,
                "Assigned to courier: " + courierId, null, null
        );
        historyRepository.save(history);

        recordOutboxEvent(parcel.getId(), "parcel.assigned", Map.of(
                "parcelId", parcel.getId().toString(),
                "courierId", courierId.toString(),
                "trackingNo", parcel.getTrackingNo()
        ));

        log.info("Assigned parcel {} to courier {}", parcel.getTrackingNo(), courierId);
        return ParcelResponse.from(parcel);
    }

    @Transactional(readOnly = true)
    public ParcelDetailResponse getParcelDetails(String trackingNo) {
        Parcel parcel = parcelRepository.findByTrackingNo(trackingNo)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Parcel with tracking number not found: " + trackingNo));
        List<ParcelStatusHistory> history = historyRepository.findByParcelIdOrderByOccurredAtAsc(parcel.getId());
        return ParcelDetailResponse.of(parcel, history);
    }

    @Transactional(readOnly = true)
    public ParcelResponse getParcelById(UUID id) {
        return parcelRepository.findById(id)
                .map(ParcelResponse::from)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Parcel not found: " + id));
    }

    @Transactional(readOnly = true)
    public List<ParcelResponse> getParcelsByCustomer(UUID customerId) {
        return parcelRepository.findByCustomerIdOrderByCreatedAtDesc(customerId)
                .stream().map(ParcelResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public List<ParcelResponse> getParcelsByCourier(UUID courierId) {
        return parcelRepository.findByCourierIdOrderByCreatedAtDesc(courierId)
                .stream().map(ParcelResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public List<ParcelResponse> getUnassignedParcels() {
        return parcelRepository.findByCourierIdIsNullAndCurrentStatus(ParcelStatus.CREATED)
                .stream().map(ParcelResponse::from).toList();
    }

    private PricingStrategy resolveStrategy(PricingStrategyType type) {
        if (type == null) {
            type = PricingStrategyType.WEIGHT_TIERED;
        }
        return switch (type) {
            case FLAT_RATE -> pricingStrategies.getOrDefault("flatRateStrategy",
                    pricingStrategies.get("weightTieredStrategy"));
            case EXPRESS -> pricingStrategies.getOrDefault("expressStrategy",
                    pricingStrategies.get("weightTieredStrategy"));
            case WEIGHT_TIERED, DISTANCE_BASED -> pricingStrategies.getOrDefault("weightTieredStrategy",
                    pricingStrategies.values().iterator().next());
        };
    }

    private Dimensions resolveDimensions(BigDecimal l, BigDecimal w, BigDecimal h) {
        BigDecimal length = (l != null && l.compareTo(BigDecimal.ZERO) > 0) ? l : new BigDecimal("20.0");
        BigDecimal width = (w != null && w.compareTo(BigDecimal.ZERO) > 0) ? w : new BigDecimal("15.0");
        BigDecimal height = (h != null && h.compareTo(BigDecimal.ZERO) > 0) ? h : new BigDecimal("10.0");
        return new Dimensions(length, width, height);
    }

    private String generateUniqueTrackingNumber() {
        String datePrefix = LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE);
        for (int attempt = 0; attempt < 10; attempt++) {
            StringBuilder sb = new StringBuilder(6);
            for (int i = 0; i < 6; i++) {
                sb.append(ALPHANUMERIC.charAt(RANDOM.nextInt(ALPHANUMERIC.length())));
            }
            String candidate = "SD-" + datePrefix + "-" + sb;
            if (!parcelRepository.existsByTrackingNo(candidate)) {
                return candidate;
            }
        }
        return "SD-" + datePrefix + "-" + UUID.randomUUID().toString().substring(0, 6).toUpperCase();
    }

    private void recordOutboxEvent(UUID aggregateId, String eventType, Object payloadObj) {
        try {
            String payloadJson = objectMapper.writeValueAsString(payloadObj);
            OutboxEvent event = new OutboxEvent(aggregateId, eventType, payloadJson);
            outboxEventRepository.save(event);
        } catch (Exception e) {
            log.error("Failed to serialize outbox event payload for {}", eventType, e);
        }
    }
}
