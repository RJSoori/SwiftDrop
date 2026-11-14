package com.swiftdrop.parcel.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.swiftdrop.parcel.domain.*;
import com.swiftdrop.parcel.domain.pricing.PricingStrategy;
import com.swiftdrop.parcel.domain.state.ParcelStateMachine;
import com.swiftdrop.parcel.dto.*;
import com.swiftdrop.parcel.repository.IdempotencyKeyRepository;
import com.swiftdrop.common.outbox.OutboxEvent;
import com.swiftdrop.common.outbox.OutboxEventRepository;
import com.swiftdrop.parcel.repository.ParcelRepository;
import com.swiftdrop.parcel.repository.ParcelStatusHistoryRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.security.SecureRandom;
import java.time.Instant;
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
    private final IdempotencyKeyRepository idempotencyKeyRepository;
    private final ParcelStateMachine parcelStateMachine;
    private final Map<String, PricingStrategy> pricingStrategies;
    private final ObjectMapper objectMapper;

    // Default rates if zone rules are not in DB
    private static final BigDecimal DEFAULT_BASE_FEE = new BigDecimal("350.00");
    private static final BigDecimal DEFAULT_PER_KG_FEE = new BigDecimal("80.00");

    public ParcelService(ParcelRepository parcelRepository,
                         ParcelStatusHistoryRepository historyRepository,
                         OutboxEventRepository outboxEventRepository,
                         IdempotencyKeyRepository idempotencyKeyRepository,
                         ParcelStateMachine parcelStateMachine,
                         Map<String, PricingStrategy> pricingStrategies,
                         ObjectMapper objectMapper) {
        this.parcelRepository = parcelRepository;
        this.historyRepository = historyRepository;
        this.outboxEventRepository = outboxEventRepository;
        this.idempotencyKeyRepository = idempotencyKeyRepository;
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
     * Creates a customer parcel delivery order without idempotency key.
     */
    @Transactional
    public ParcelResponse createParcel(CreateParcelRequest request, UUID callerId) {
        return createParcel(request, callerId, null);
    }

    /**
     * Creates a customer parcel delivery order, optionally idempotent.
     */
    @Transactional
    public ParcelResponse createParcel(CreateParcelRequest request, UUID callerId, String idempotencyKey) {
        // Idempotency check: if key already exists, return cached response
        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            Optional<IdempotencyKey> existing = idempotencyKeyRepository.findById(idempotencyKey);
            if (existing.isPresent()) {
                try {
                    log.info("Idempotency hit for key {}", idempotencyKey);
                    return objectMapper.readValue(existing.get().getResponseBody(), ParcelResponse.class);
                } catch (Exception e) {
                    log.warn("Failed to deserialize cached idempotency response for key {}", idempotencyKey, e);
                }
            }
        }

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
        parcel.setOrigin(merchantId != null ? "MERCHANT" : "CUSTOMER");
        parcel.setMerchantId(merchantId);
        parcel.setPickupZoneId(request.pickupZoneId());
        parcel.setDropoffZoneId(request.dropoffZoneId());
        parcel.setPickupCity(request.pickupCity());
        parcel.setPickupLat(request.pickupLat());
        parcel.setPickupLng(request.pickupLng());
        parcel.setDropoffCity(request.dropoffCity());
        parcel.setDropoffLat(request.dropoffLat());
        parcel.setDropoffLng(request.dropoffLng());
        parcel.setRecipientName(request.recipientName());
        parcel.setRecipientPhone(request.recipientPhone());
        parcel.setLengthCm(request.lengthCm());
        parcel.setWidthCm(request.widthCm());
        parcel.setHeightCm(request.heightCm());
        parcel.setServiceType(request.serviceType() != null ? request.serviceType() : "STANDARD");

        parcel = parcelRepository.save(parcel);

        // Append initial audit record to status_history
        ParcelStatusHistory initialHistory = new ParcelStatusHistory(
                parcel.getId(), null, ParcelStatus.CREATED, callerId,
                "Parcel booking created via SwiftDrop API", null, null
        );
        historyRepository.save(initialHistory);

        // Write transactional outbox event (Section 4 Contract)
        recordOutboxEvent(parcel.getId(), "parcel.created", Map.of(
                "parcelId", parcel.getId().toString(),
                "trackingNo", parcel.getTrackingNo(),
                "origin", parcel.getOrigin(),
                "occurredAt", parcel.getCreatedAt().toString(),
                "merchantId", merchantId != null ? merchantId.toString() : "",
                "pickupCity", parcel.getPickupCity() != null ? parcel.getPickupCity() : "",
                "dropoffCity", parcel.getDropoffCity() != null ? parcel.getDropoffCity() : "",
                "pickupZoneId", parcel.getPickupZoneId() != null ? parcel.getPickupZoneId().toString() : "",
                "weightKg", parcel.getWeightKg().toString(),
                "feeLkr", parcel.getFeeLkr().toString()
        ));

        ParcelResponse response = ParcelResponse.from(parcel);

        // Save idempotency key if provided
        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            try {
                String responseBody = objectMapper.writeValueAsString(response);
                idempotencyKeyRepository.save(new IdempotencyKey(idempotencyKey, responseBody, 201));
            } catch (Exception e) {
                log.warn("Failed to persist idempotency key {}", idempotencyKey, e);
            }
        }

        log.info("Created parcel ID {} with tracking number {}", parcel.getId(), parcel.getTrackingNo());
        return response;
    }

    /**
     * Creates a parcel for merchants via internal REST API (POST /internal/v1/parcels).
     */
    @Transactional
    public ParcelResponse createMerchantParcel(InternalCreateParcelRequest request) {
        QuoteRequest quoteReq = new QuoteRequest(
                request.weightKg(), request.lengthCm(), request.widthCm(), request.heightCm(),
                request.strategy(), request.pickupZoneId(), request.dropoffZoneId()
        );
        QuoteResponse quote = calculateQuote(quoteReq);

        String trackingNo = generateUniqueTrackingNumber();

        Parcel parcel = new Parcel(
                trackingNo, null, quote.actualWeightKg(), request.size(),
                quote.feeLkr(), request.pickupAddress(), request.dropoffAddress()
        );
        parcel.setOrigin("MERCHANT");
        parcel.setMerchantId(request.merchantId());
        parcel.setMerchantOrderRef(request.merchantOrderRef());
        parcel.setPickupZoneId(request.pickupZoneId());
        parcel.setDropoffZoneId(request.dropoffZoneId());
        parcel.setPickupCity(request.pickupCity());
        parcel.setPickupLat(request.pickupLat());
        parcel.setPickupLng(request.pickupLng());
        parcel.setDropoffCity(request.dropoffCity());
        parcel.setDropoffLat(request.dropoffLat());
        parcel.setDropoffLng(request.dropoffLng());
        parcel.setRecipientName(request.recipientName());
        parcel.setRecipientPhone(request.recipientPhone());
        parcel.setLengthCm(request.lengthCm());
        parcel.setWidthCm(request.widthCm());
        parcel.setHeightCm(request.heightCm());
        parcel.setServiceType(request.serviceType() != null ? request.serviceType() : "STANDARD");

        parcel = parcelRepository.save(parcel);

        ParcelStatusHistory initialHistory = new ParcelStatusHistory(
                parcel.getId(), null, ParcelStatus.CREATED, request.merchantId(),
                "Merchant order created via internal API", null, null
        );
        historyRepository.save(initialHistory);

        recordOutboxEvent(parcel.getId(), "parcel.created", Map.ofEntries(
                Map.entry("parcelId", parcel.getId().toString()),
                Map.entry("trackingNo", parcel.getTrackingNo()),
                Map.entry("origin", "MERCHANT"),
                Map.entry("occurredAt", parcel.getCreatedAt().toString()),
                Map.entry("merchantId", request.merchantId() != null ? request.merchantId().toString() : ""),
                Map.entry("merchantOrderRef", request.merchantOrderRef() != null ? request.merchantOrderRef() : ""),
                Map.entry("pickupCity", parcel.getPickupCity() != null ? parcel.getPickupCity() : ""),
                Map.entry("dropoffCity", parcel.getDropoffCity() != null ? parcel.getDropoffCity() : ""),
                Map.entry("pickupZoneId", parcel.getPickupZoneId() != null ? parcel.getPickupZoneId().toString() : ""),
                Map.entry("weightKg", parcel.getWeightKg().toString()),
                Map.entry("feeLkr", parcel.getFeeLkr().toString())
        ));

        log.info("Created merchant parcel ID {} with tracking number {}", parcel.getId(), parcel.getTrackingNo());
        return ParcelResponse.from(parcel);
    }

    /**
     * Internal status transition endpoint (POST /internal/v1/parcels/{id}/status).
     * Used by M9 Courier and other services. Throws 409 Conflict on illegal moves.
     */
    @Transactional
    public ParcelResponse updateStatusInternal(UUID parcelId, InternalUpdateStatusRequest request) {
        Parcel parcel = parcelRepository.findById(parcelId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Parcel not found: " + parcelId));

        if (request.expectedVersion() != null && !Objects.equals(request.expectedVersion(), parcel.getVersion())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Version conflict: expected " + request.expectedVersion() + " but current is " + parcel.getVersion());
        }

        ParcelStatus current = parcel.getCurrentStatus();
        ParcelStatus target = request.toStatus();

        if (target == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Target status must not be null");
        }

        // Validate state transition through ParcelStateMachine
        try {
            parcelStateMachine.transition(current, target);
        } catch (IllegalStateException e) {
            log.warn("Illegal status transition attempt for parcel {} from {} to {}", parcelId, current, target);
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Illegal status transition from " + current + " to " + target);
        }

        parcel.setCurrentStatus(target);
        parcel = parcelRepository.save(parcel);

        ParcelStatusHistory history = new ParcelStatusHistory(
                parcel.getId(), current, target, request.actorId(),
                request.note(), request.proofUrl(), request.reasonCode()
        );
        historyRepository.save(history);

        // Section 4 Event Contract: parcel.status.changed
        recordOutboxEvent(parcel.getId(), "parcel.status.changed", Map.of(
                "parcelId", parcel.getId().toString(),
                "trackingNo", parcel.getTrackingNo(),
                "merchantId", parcel.getMerchantId() != null ? parcel.getMerchantId().toString() : "",
                "courierId", parcel.getCourierId() != null ? parcel.getCourierId().toString() : "",
                "fromStatus", current.name(),
                "toStatus", target.name(),
                "aggregateVersion", parcel.getVersion() != null ? parcel.getVersion().toString() : "0",
                "reasonCode", request.reasonCode() != null ? request.reasonCode() : "",
                "occurredAt", Instant.now().toString()
        ));

        log.info("Internal updated parcel {} status from {} to {}", parcel.getTrackingNo(), current, target);
        return ParcelResponse.from(parcel);
    }

    /**
     * Returns recipient contact details (name and phone) for M5 and M9.
     */
    @Transactional(readOnly = true)
    public RecipientContactResponse getRecipientContact(UUID parcelId) {
        Parcel parcel = parcelRepository.findById(parcelId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Parcel not found: " + parcelId));

        return new RecipientContactResponse(
                parcel.getRecipientName() != null ? parcel.getRecipientName() : "Customer",
                parcel.getRecipientPhone() != null ? parcel.getRecipientPhone() : ""
        );
    }

    /**
     * Sets assigned courier from RabbitMQ parcel.assigned event.
     */
    @Transactional
    public void setAssignedCourier(UUID parcelId, UUID courierId) {
        Optional<Parcel> opt = parcelRepository.findById(parcelId);
        if (opt.isEmpty()) {
            log.warn("Received parcel.assigned for non-existent parcel {}", parcelId);
            return;
        }

        Parcel parcel = opt.get();
        parcel.setCourierId(courierId);
        parcelRepository.save(parcel);

        ParcelStatusHistory history = new ParcelStatusHistory(
                parcel.getId(), parcel.getCurrentStatus(), parcel.getCurrentStatus(), courierId,
                "Assigned to courier: " + courierId, null, null
        );
        historyRepository.save(history);

        log.info("Updated parcel {} courier assignment to {}", parcel.getTrackingNo(), courierId);
    }

    /**
     * Updates parcel status according to the finite state machine transitions (Public/User API).
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
        try {
            parcelStateMachine.transition(current, target);
        } catch (IllegalStateException e) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Illegal status transition: " + e.getMessage());
        }

        parcel.setCurrentStatus(target);
        parcel = parcelRepository.save(parcel);

        ParcelStatusHistory history = new ParcelStatusHistory(
                parcel.getId(), current, target, actorId,
                request.note(), request.proofUrl(), request.failureReason()
        );
        historyRepository.save(history);

        recordOutboxEvent(parcel.getId(), "parcel.status.changed", Map.of(
                "parcelId", parcel.getId().toString(),
                "trackingNo", parcel.getTrackingNo(),
                "fromStatus", current.name(),
                "toStatus", target.name(),
                "courierId", parcel.getCourierId() != null ? parcel.getCourierId().toString() : "",
                "actorId", actorId != null ? actorId.toString() : ""
        ));

        log.info("Updated parcel {} status from {} to {}", parcel.getTrackingNo(), current, target);
        return ParcelResponse.from(parcel);
    }

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
    public boolean canAccessParcel(UUID parcelId, UUID userId, String role) {
        Parcel parcel = parcelRepository.findById(parcelId).orElse(null);
        if (parcel == null) {
            return false;
        }
        return switch (role) {
            case "CUSTOMER" -> userId.equals(parcel.getCustomerId());
            case "MERCHANT" -> userId.equals(parcel.getMerchantId());
            case "COURIER" -> userId.equals(parcel.getCourierId());
            default -> false;
        };
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
            com.fasterxml.jackson.databind.node.ObjectNode payload = objectMapper.valueToTree(payloadObj);
            payload.put("eventId", UUID.randomUUID().toString());
            String payloadJson = objectMapper.writeValueAsString(payload);
            OutboxEvent event = new OutboxEvent(aggregateId, eventType, payloadJson);
            outboxEventRepository.save(event);
        } catch (Exception e) {
            log.error("Failed to serialize outbox event payload for {}", eventType, e);
        }
    }
}
