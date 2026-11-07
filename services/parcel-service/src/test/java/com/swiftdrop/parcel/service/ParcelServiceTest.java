package com.swiftdrop.parcel.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.swiftdrop.parcel.domain.*;
import com.swiftdrop.parcel.domain.pricing.ExpressStrategy;
import com.swiftdrop.parcel.domain.pricing.FlatRateStrategy;
import com.swiftdrop.parcel.domain.pricing.PricingStrategy;
import com.swiftdrop.parcel.domain.pricing.WeightTieredStrategy;
import com.swiftdrop.parcel.domain.state.ParcelStateMachine;
import com.swiftdrop.parcel.dto.CreateParcelRequest;
import com.swiftdrop.parcel.dto.ParcelResponse;
import com.swiftdrop.parcel.dto.QuoteRequest;
import com.swiftdrop.parcel.dto.QuoteResponse;
import com.swiftdrop.parcel.dto.UpdateStatusRequest;
import com.swiftdrop.parcel.repository.OutboxEventRepository;
import com.swiftdrop.parcel.repository.ParcelRepository;
import com.swiftdrop.parcel.repository.ParcelStatusHistoryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

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

    private ParcelStateMachine stateMachine;
    private ParcelService parcelService;

    @BeforeEach
    void setUp() {
        stateMachine = new ParcelStateMachine();
        Map<String, PricingStrategy> strategies = Map.of(
                "weightTieredStrategy", new WeightTieredStrategy(),
                "flatRateStrategy", new FlatRateStrategy(),
                "expressStrategy", new ExpressStrategy()
        );
        parcelService = new ParcelService(
                parcelRepository,
                historyRepository,
                outboxEventRepository,
                stateMachine,
                strategies,
                new ObjectMapper()
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
    @DisplayName("createParcel generates tracking number and records outbox event")
    void createParcel_success() {
        UUID callerId = UUID.randomUUID();
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

        when(parcelRepository.existsByTrackingNo(any())).thenReturn(false);
        when(parcelRepository.save(any(Parcel.class))).thenAnswer(inv -> {
            Parcel p = inv.getArgument(0);
            return p;
        });

        ParcelResponse response = parcelService.createParcel(request, callerId);

        assertThat(response).isNotNull();
        assertThat(response.trackingNo()).startsWith("SD-");
        assertThat(response.currentStatus()).isEqualTo(ParcelStatus.CREATED);
        assertThat(response.pickupAddress()).isEqualTo("123 Galle Rd, Colombo 03");

        verify(parcelRepository).save(any(Parcel.class));
        verify(historyRepository).save(any(ParcelStatusHistory.class));

        ArgumentCaptor<OutboxEvent> outboxCaptor = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(outboxEventRepository).save(outboxCaptor.capture());
        assertThat(outboxCaptor.getValue().getEventType()).isEqualTo("parcel.created");
    }

    @Test
    @DisplayName("updateStatus accepts valid transition from CREATED to PICKED_UP")
    void updateStatus_validTransition() {
        UUID parcelId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();

        Parcel existingParcel = new Parcel(
                "SD-20261108-TEST01", UUID.randomUUID(), new BigDecimal("1.5"),
                "SMALL", new BigDecimal("430.00"), "Colombo", "Kandy"
        );
        existingParcel.setCurrentStatus(ParcelStatus.CREATED);

        when(parcelRepository.findById(parcelId)).thenReturn(Optional.of(existingParcel));
        when(parcelRepository.save(any(Parcel.class))).thenAnswer(inv -> inv.getArgument(0));

        UpdateStatusRequest req = new UpdateStatusRequest(ParcelStatus.PICKED_UP, "Package scanned at merchant", null, null);
        ParcelResponse updated = parcelService.updateStatus(parcelId, req, actorId);

        assertThat(updated.currentStatus()).isEqualTo(ParcelStatus.PICKED_UP);
        verify(historyRepository).save(any(ParcelStatusHistory.class));

        ArgumentCaptor<OutboxEvent> outboxCaptor = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(outboxEventRepository).save(outboxCaptor.capture());
        assertThat(outboxCaptor.getValue().getEventType()).isEqualTo("parcel.status.changed");
    }

    @Test
    @DisplayName("updateStatus rejects invalid transition and throws IllegalStateException")
    void updateStatus_invalidTransition_throwsException() {
        UUID parcelId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();

        Parcel existingParcel = new Parcel(
                "SD-20261108-TEST02", UUID.randomUUID(), new BigDecimal("1.5"),
                "SMALL", new BigDecimal("430.00"), "Colombo", "Kandy"
        );
        existingParcel.setCurrentStatus(ParcelStatus.DELIVERED); // Terminal state

        when(parcelRepository.findById(parcelId)).thenReturn(Optional.of(existingParcel));

        UpdateStatusRequest req = new UpdateStatusRequest(ParcelStatus.IN_TRANSIT, "Illegal status attempt", null, null);

        assertThatThrownBy(() -> parcelService.updateStatus(parcelId, req, actorId))
                .isInstanceOf(IllegalStateException.class);

        verify(parcelRepository, never()).save(any(Parcel.class));
        verify(historyRepository, never()).save(any(ParcelStatusHistory.class));
        verify(outboxEventRepository, never()).save(any(OutboxEvent.class));
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
