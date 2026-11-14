package com.swiftdrop.dispatch.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.swiftdrop.common.outbox.OutboxEventRepository;
import com.swiftdrop.dispatch.api.DispatchDtos;
import com.swiftdrop.dispatch.domain.Courier;
import com.swiftdrop.dispatch.domain.ParcelQueueEntry;
import com.swiftdrop.dispatch.repository.AssignmentRepository;
import com.swiftdrop.dispatch.repository.CourierRepository;
import com.swiftdrop.dispatch.repository.ParcelQueueRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DispatchServiceTest {
    @Mock ParcelQueueRepository parcels;
    @Mock CourierRepository couriers;
    @Mock AssignmentRepository assignments;
    @Mock OutboxEventRepository outbox;

    @Test
    void suggestionChoosesLowestLoadInPickupZone() {
        UUID parcelId = UUID.randomUUID();
        UUID zone = UUID.randomUUID();
        Courier busy = new Courier(UUID.randomUUID(), "Busy", zone, "VAN");
        Courier free = new Courier(UUID.randomUUID(), "Free", zone, "BIKE");
        when(parcels.findById(parcelId)).thenReturn(Optional.of(parcel(parcelId, zone)));
        when(couriers.findByActiveTrueOrderByFullNameAsc()).thenReturn(List.of(busy, free));
        when(assignments.countActiveByCourierId(eq(busy.getId()), anyList())).thenReturn(2L);
        when(assignments.countActiveByCourierId(eq(free.getId()), anyList())).thenReturn(0L);

        DispatchDtos.CourierResponse result = service().suggestedCourier(parcelId);

        assertEquals(free.getId(), result.userId());
        assertEquals(0, result.activeParcelCount());
    }

    @Test
    void assigningAlreadyAssignedParcelReturnsConflict() {
        UUID parcelId = UUID.randomUUID();
        ParcelQueueEntry parcel = parcel(parcelId, UUID.randomUUID());
        parcel.assignCourier(UUID.randomUUID(), Instant.now());
        when(parcels.findById(parcelId)).thenReturn(Optional.of(parcel));

        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> service().assign(parcelId, new DispatchDtos.AssignRequest(UUID.randomUUID())));

        assertEquals(409, error.getStatusCode().value());
        verifyNoInteractions(outbox);
    }

    @Test
    void inactiveCourierIsNotSuggested() {
        UUID parcelId = UUID.randomUUID();
        Courier courier = new Courier(UUID.randomUUID(), "Inactive", UUID.randomUUID(), "VAN");
        courier.setActive(false);
        when(parcels.findById(parcelId)).thenReturn(Optional.of(parcel(parcelId, UUID.randomUUID())));
        when(couriers.findByActiveTrueOrderByFullNameAsc()).thenReturn(List.of());

        assertThrows(ResponseStatusException.class, () -> service().suggestedCourier(parcelId));
    }

    private DispatchService service() {
        return new DispatchService(parcels, couriers, assignments, outbox, new ObjectMapper());
    }

    private ParcelQueueEntry parcel(UUID id, UUID zone) {
        return new ParcelQueueEntry(id, "SD-20261113-000001", "Colombo", "Kandy", zone,
                BigDecimal.valueOf(2), "CREATED", Instant.now());
    }
}
