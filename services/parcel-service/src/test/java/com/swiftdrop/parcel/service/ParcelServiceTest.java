package com.swiftdrop.parcel.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.swiftdrop.parcel.domain.*;
import com.swiftdrop.parcel.domain.pricing.ExpressStrategy;
import com.swiftdrop.parcel.domain.pricing.FlatRateStrategy;
import com.swiftdrop.parcel.domain.pricing.PricingStrategy;
import com.swiftdrop.parcel.domain.pricing.WeightTieredStrategy;
import com.swiftdrop.parcel.domain.state.ParcelStateMachine;
import com.swiftdrop.parcel.dto.*;
import com.swiftdrop.parcel.repository.IdempotencyKeyRepository;
import com.swiftdrop.common.outbox.OutboxEvent;
import com.swiftdrop.common.outbox.OutboxEventRepository;
import com.swiftdrop.parcel.repository.ParcelRepository;
import com.swiftdrop.parcel.repository.ParcelStatusHistoryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ParcelServiceTest {

    @Mock
    private ParcelRepository parcelRepository;

    @Mock
    private ParcelStatusHistoryRepository historyRepository;

    @Mock
    private OutboxEventRepository outboxEventRepository;

    @Mock
    private IdempotencyKeyRepository idempotencyKeyRepository;

    private ParcelStateMachine stateMachine;
    private ParcelService parcelService;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        stateMachine = new ParcelStateMachine();
        objectMapper = new ObjectMapper().findAndRegisterModules();
        Map<String, PricingStrategy> strategies = Map.of(
                "weightTieredStrategy", new WeightTieredStrategy(),
                "flatRateStrategy", new FlatRateStrategy(),
                "expressStrategy", new ExpressStrategy()
        );
        parcelService = new ParcelService(
                parcelRepository,
                historyRepository,
                outboxEventRepository,
                idempotencyKeyRepository,
                stateMachine,
                strategies,
                objectMapper
        );
    }

    @Test
    @DisplayName("calculateQuote returns expected fee with volumetric weight calculation")
    void calculateQuote_success() {
        QuoteRequest request = new QuoteRequest(
                new BigDecimal("2.0"),
                new BigDecimal("50.0"), // 50 * 40 * 30 / 5000 = 12.0 kg volumetric weight
                new BigDecimal("40.0"),
                new BigDecimal("30.0"),
                PricingStrategyType.WEIGHT_TIERED,
                null,
                null
        );

        QuoteResponse response = parcelService.calculateQuote(request);

        assertThat(response).isNotNull();
        assertThat(response.actualWeightKg()).isEqualByComparingTo("2.0");
        assertThat(response.volumetricWeightKg()).isEqualByComparingTo("12.00");
        assertThat(response.chargeableWeightKg()).isEqualByComparingTo("12.00");
        // Fee = 350 + (12.00 * 80) = 350 + 960 = 1310.00
        assertThat(response.feeLkr()).isEqualByComparingTo("1310.00");
    }

    @Test
    @DisplayName("createParcel saves parcel, initial CREATED history, and publishes parcel.created outbox event")
    void createParcel_success() {
        UUID customerId = UUID.randomUUID();
        CreateParcelRequest request = new CreateParcelRequest(
                "123 Galle Rd, Colombo 03",
                "45 Peradeniya Rd, Kandy",
                new BigDecimal("2.5"),
                new BigDecimal("20.0"),
                new BigDecimal("15.0"),
                new BigDecimal("10.0"),
                "MEDIUM",
                PricingStrategyType.WEIGHT_TIERED,
                null,
                null,
                null
        );

        when(parcelRepository.save(any(Parcel.class))).thenAnswer(inv -> {
            Parcel p = inv.getArgument(0);
            p.setId(UUID.randomUUID());
            return p;
        });

        ParcelResponse response = parcelService.createParcel(request, customerId);

        assertThat(response).isNotNull();
        assertThat(response.trackingNo()).startsWith("SD-");
        assertThat(response.currentStatus()).isEqualTo(ParcelStatus.CREATED);

        // Verify history row
        ArgumentCaptor<ParcelStatusHistory> historyCaptor = ArgumentCaptor.forClass(ParcelStatusHistory.class);
        verify(historyRepository).save(historyCaptor.capture());
        assertThat(historyCaptor.getValue().getToStatus()).isEqualTo(ParcelStatus.CREATED);
        assertThat(historyCaptor.getValue().getActorId()).isEqualTo(customerId);

        // Verify outbox event
        ArgumentCaptor<OutboxEvent> outboxCaptor = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(outboxEventRepository).save(outboxCaptor.capture());
        assertThat(outboxCaptor.getValue().getEventType()).isEqualTo("parcel.created");
    }

    @Test
    @DisplayName("createParcel with existing Idempotency-Key returns cached response without duplicate creation")
    void createParcel_idempotencyHit() throws Exception {
        UUID customerId = UUID.randomUUID();
        String idempotencyKey = "key-unique-12345";
        Parcel parcel = new Parcel(
                "SD-20261108-CACHED", customerId, new BigDecimal("2.0"),
                "MEDIUM", new BigDecimal("510.00"), "Colombo", "Kandy"
        );
        parcel.setId(UUID.randomUUID());
        ParcelResponse cached = ParcelResponse.from(parcel);

        when(idempotencyKeyRepository.findById(idempotencyKey))
                .thenReturn(Optional.of(new IdempotencyKey(idempotencyKey, objectMapper.writeValueAsString(cached), 201)));

        CreateParcelRequest request = new CreateParcelRequest(
                "Colombo", "Kandy", new BigDecimal("2.0"),
                null, null, null, "MEDIUM", PricingStrategyType.WEIGHT_TIERED,
                null, null, null
        );

        ParcelResponse result = parcelService.createParcel(request, customerId, idempotencyKey);

        assertThat(result.trackingNo()).isEqualTo("SD-20261108-CACHED");
        verify(parcelRepository, never()).save(any(Parcel.class));
    }

    @Test
    @DisplayName("updateStatusInternal with valid status transition persists status and emits parcel.status.changed")
    void updateStatusInternal_success() {
        UUID parcelId = UUID.randomUUID();
        UUID courierId = UUID.randomUUID();
        Parcel existingParcel = new Parcel(
                "SD-20261108-TEST01", UUID.randomUUID(), new BigDecimal("1.0"),
                "SMALL", new BigDecimal("430.00"), "Colombo", "Kandy"
        );
        existingParcel.setId(parcelId);
        existingParcel.setCurrentStatus(ParcelStatus.CREATED);

        when(parcelRepository.findById(parcelId)).thenReturn(Optional.of(existingParcel));
        when(parcelRepository.saveAndFlush(any(Parcel.class))).thenAnswer(inv -> inv.getArgument(0));

        InternalUpdateStatusRequest req = new InternalUpdateStatusRequest(
                ParcelStatus.PICKED_UP, courierId, "Parcel collected from sender", null, null, null
        );

        ParcelResponse response = parcelService.updateStatusInternal(parcelId, req);

        assertThat(response.currentStatus()).isEqualTo(ParcelStatus.PICKED_UP);
        verify(historyRepository).save(any(ParcelStatusHistory.class));

        ArgumentCaptor<OutboxEvent> outboxCaptor = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(outboxEventRepository).save(outboxCaptor.capture());
        assertThat(outboxCaptor.getValue().getEventType()).isEqualTo("parcel.status.changed");
    }

    @Test
    @DisplayName("updateStatusInternal with illegal status transition throws 409 Conflict")
    void updateStatusInternal_illegalTransitionThrows409() {
        UUID parcelId = UUID.randomUUID();
        UUID courierId = UUID.randomUUID();
        Parcel existingParcel = new Parcel(
                "SD-20261108-TEST02", UUID.randomUUID(), new BigDecimal("1.0"),
                "SMALL", new BigDecimal("430.00"), "Colombo", "Kandy"
        );
        existingParcel.setId(parcelId);
        existingParcel.setCurrentStatus(ParcelStatus.CREATED);

        when(parcelRepository.findById(parcelId)).thenReturn(Optional.of(existingParcel));

        // Attempt CREATED -> DELIVERED (illegal!)
        InternalUpdateStatusRequest req = new InternalUpdateStatusRequest(
                ParcelStatus.DELIVERED, courierId, "Skipped directly to delivered", null, null, null
        );

        assertThatThrownBy(() -> parcelService.updateStatusInternal(parcelId, req))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("409 CONFLICT");

        verify(parcelRepository, never()).save(any(Parcel.class));
        verify(historyRepository, never()).save(any(ParcelStatusHistory.class));
    }

    @Test
    @DisplayName("getRecipientContact returns recipient name and phone")
    void getRecipientContact_success() {
        UUID parcelId = UUID.randomUUID();
        Parcel parcel = new Parcel(
                "SD-20261108-TEST04", UUID.randomUUID(), new BigDecimal("1.0"),
                "SMALL", new BigDecimal("350.00"), "Colombo", "Kandy"
        );
        parcel.setId(parcelId);
        parcel.setRecipientName("Kasun Perera");
        parcel.setRecipientPhone("+94771234567");

        when(parcelRepository.findById(parcelId)).thenReturn(Optional.of(parcel));

        RecipientContactResponse contact = parcelService.getRecipientContact(parcelId);

        assertThat(contact.recipientName()).isEqualTo("Kasun Perera");
        assertThat(contact.recipientPhone()).isEqualTo("+94771234567");
    }

    @Test
    @DisplayName("assignCourier successfully updates courier ID and records assignment event")
    void assignCourier_success() {
        UUID parcelId = UUID.randomUUID();
        UUID courierId = UUID.randomUUID();
        UUID dispatcherId = UUID.randomUUID();

        Parcel parcel = new Parcel(
                "SD-20261108-TEST03", UUID.randomUUID(), new BigDecimal("2.0"),
                "MEDIUM", new BigDecimal("500.00"), "Colombo", "Galle"
        );
        parcel.setId(parcelId);
        when(parcelRepository.findById(parcelId)).thenReturn(Optional.of(parcel));
        when(parcelRepository.save(any(Parcel.class))).thenAnswer(inv -> inv.getArgument(0));

        ParcelResponse response = parcelService.assignCourier(parcelId, courierId, dispatcherId);

        assertThat(response.courierId()).isEqualTo(courierId);
        verify(historyRepository).save(any(ParcelStatusHistory.class));

        ArgumentCaptor<OutboxEvent> outboxCaptor = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(outboxEventRepository).save(outboxCaptor.capture());
        assertThat(outboxCaptor.getValue().getEventType()).isEqualTo("parcel.assigned");
    }
}
