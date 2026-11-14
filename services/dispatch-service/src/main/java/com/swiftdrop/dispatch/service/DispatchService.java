package com.swiftdrop.dispatch.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.swiftdrop.common.events.EventEnvelope;
import com.swiftdrop.common.events.EventTopology;
import com.swiftdrop.common.events.ParcelAssigned;
import com.swiftdrop.common.outbox.OutboxEvent;
import com.swiftdrop.common.outbox.OutboxEventRepository;
import com.swiftdrop.common.security.CurrentUser;
import com.swiftdrop.dispatch.api.DispatchDtos;
import com.swiftdrop.dispatch.domain.Assignment;
import com.swiftdrop.dispatch.domain.Courier;
import com.swiftdrop.dispatch.domain.ParcelQueueEntry;
import com.swiftdrop.dispatch.repository.AssignmentRepository;
import com.swiftdrop.dispatch.repository.CourierRepository;
import com.swiftdrop.dispatch.repository.ParcelQueueRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

@Service
public class DispatchService {
    private static final List<String> TERMINAL_STATUSES = List.of("DELIVERED", "FAILED");

    private final ParcelQueueRepository parcelQueueRepository;
    private final CourierRepository courierRepository;
    private final AssignmentRepository assignmentRepository;
    private final OutboxEventRepository outboxEventRepository;
    private final ObjectMapper objectMapper;

    public DispatchService(ParcelQueueRepository parcelQueueRepository, CourierRepository courierRepository,
                           AssignmentRepository assignmentRepository, OutboxEventRepository outboxEventRepository,
                           ObjectMapper objectMapper) {
        this.parcelQueueRepository = parcelQueueRepository;
        this.courierRepository = courierRepository;
        this.assignmentRepository = assignmentRepository;
        this.outboxEventRepository = outboxEventRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public Page<DispatchDtos.ParcelResponse> parcels(String status, UUID courierId, Pageable pageable) {
        if (status == null && courierId == null) return parcelQueueRepository.findAll(pageable).map(this::parcelResponse);
        if (status == null) return parcelQueueRepository.findByCourierId(courierId, pageable).map(this::parcelResponse);
        if (courierId == null) return parcelQueueRepository.findByStatus(status, pageable).map(this::parcelResponse);
        return parcelQueueRepository.findByStatusAndCourierId(status, courierId, pageable).map(this::parcelResponse);
    }

    @Transactional(readOnly = true)
    public Page<DispatchDtos.ParcelResponse> unassigned(Pageable pageable) {
        return parcelQueueRepository.findByStatusAndCourierIdIsNull("CREATED", pageable).map(this::parcelResponse);
    }

    @Transactional(readOnly = true)
    public List<DispatchDtos.CourierResponse> workloads() {
        return courierRepository.findByActiveTrueOrderByFullNameAsc().stream()
                .map(courier -> courierResponse(courier, activeLoad(courier.getId()))).toList();
    }

    @Transactional(readOnly = true)
    public DispatchDtos.CourierResponse suggestedCourier(UUID parcelId) {
        ParcelQueueEntry parcel = findParcel(parcelId);
        List<Courier> active = courierRepository.findByActiveTrueOrderByFullNameAsc();
        List<Courier> sameZone = active.stream()
                .filter(courier -> parcel.getPickupZoneId() != null
                        && parcel.getPickupZoneId().equals(courier.getHomeZoneId())).toList();
        List<Courier> candidates = sameZone.isEmpty() ? active : sameZone;
        return candidates.stream().min(Comparator.comparingLong(c -> activeLoad(c.getId())))
                .map(courier -> courierResponse(courier, activeLoad(courier.getId())))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "No active courier is available"));
    }

    @Transactional
    public DispatchDtos.AssignmentResponse assign(UUID parcelId, DispatchDtos.AssignRequest request) {
        ParcelQueueEntry parcel = findParcel(parcelId);
        if (parcel.getCourierId() != null
                || assignmentRepository.findByParcelIdAndUnassignedAtIsNull(parcelId).isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Parcel is already assigned");
        }
        Courier courier = courierRepository.findById(request.courierId()).filter(Courier::isActive)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Active courier not found"));
        Instant assignedAt = Instant.now();
        Assignment assignment = assignmentRepository.save(new Assignment(
                parcelId, courier.getId(), CurrentUser.id(), assignedAt));
        parcel.assignCourier(courier.getId(), assignedAt);
        ParcelAssigned payload = new ParcelAssigned(parcelId, parcel.getTrackingNo(), courier.getId());
        outboxEventRepository.save(new OutboxEvent(parcelId, EventTopology.PARCEL_ASSIGNED,
                serialize(EventEnvelope.of(EventTopology.PARCEL_ASSIGNED, "dispatch-service", null, payload))));
        return new DispatchDtos.AssignmentResponse(assignment.getParcelId(), assignment.getCourierId(), assignedAt);
    }

    @Transactional
    public DispatchDtos.CourierResponse createCourier(DispatchDtos.CourierRequest request) {
        if (courierRepository.existsById(request.userId())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Courier already exists");
        }
        Courier courier = courierRepository.save(new Courier(request.userId(), request.fullName(),
                request.homeZoneId(), request.vehicleType()));
        return courierResponse(courier, 0);
    }

    @Transactional
    public DispatchDtos.CourierResponse updateCourier(UUID courierId, DispatchDtos.CourierPatch request) {
        Courier courier = courierRepository.findById(courierId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Courier not found"));
        if (request.homeZoneId() != null) courier.setHomeZoneId(request.homeZoneId());
        if (request.vehicleType() != null && !request.vehicleType().isBlank()) courier.setVehicleType(request.vehicleType());
        if (request.active() != null) courier.setActive(request.active());
        return courierResponse(courier, activeLoad(courierId));
    }

    private long activeLoad(UUID courierId) {
        return assignmentRepository.countActiveByCourierId(courierId, TERMINAL_STATUSES);
    }

    private ParcelQueueEntry findParcel(UUID parcelId) {
        return parcelQueueRepository.findById(parcelId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Parcel not found"));
    }

    private DispatchDtos.ParcelResponse parcelResponse(ParcelQueueEntry parcel) {
        return new DispatchDtos.ParcelResponse(parcel.getParcelId(), parcel.getTrackingNo(),
                parcel.getPickupCity(), parcel.getDropoffCity(), parcel.getPickupZoneId(),
                parcel.getWeightKg(), parcel.getStatus(), parcel.getCourierId(),
                parcel.getCreatedAt(), parcel.getUpdatedAt());
    }

    private DispatchDtos.CourierResponse courierResponse(Courier courier, long activeCount) {
        return new DispatchDtos.CourierResponse(courier.getId(), courier.getFullName(),
                courier.getHomeZoneId(), courier.getVehicleType(), courier.isActive(), activeCount);
    }

    private String serialize(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Could not serialize parcel assignment event", ex);
        }
    }
}
