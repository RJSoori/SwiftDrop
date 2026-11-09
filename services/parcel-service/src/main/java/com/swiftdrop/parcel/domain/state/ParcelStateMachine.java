package com.swiftdrop.parcel.domain.state;

import com.swiftdrop.parcel.domain.ParcelStatus;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.Map;
import java.util.Set;

/**
 * State machine managing parcel lifecycle transitions according to Figure 7.5.
 * Implements the State Pattern to enforce valid state transitions and guards.
 */
@Component
public class ParcelStateMachine {

    private final Map<ParcelStatus, ParcelState> states = new EnumMap<>(ParcelStatus.class);

    public ParcelStateMachine() {
        registerState(ParcelStatus.CREATED, Set.of(ParcelStatus.PICKED_UP, ParcelStatus.CANCELLED));
        registerState(ParcelStatus.PICKED_UP, Set.of(ParcelStatus.IN_TRANSIT, ParcelStatus.FAILED));
        registerState(ParcelStatus.IN_TRANSIT, Set.of(ParcelStatus.OUT_FOR_DELIVERY, ParcelStatus.FAILED));
        registerState(ParcelStatus.OUT_FOR_DELIVERY, Set.of(ParcelStatus.DELIVERED, ParcelStatus.FAILED));
        registerState(ParcelStatus.DELIVERED, Set.of()); // Terminal state
        registerState(ParcelStatus.FAILED, Set.of(ParcelStatus.OUT_FOR_DELIVERY, ParcelStatus.CANCELLED)); // Re-dispatch or return
        registerState(ParcelStatus.CANCELLED, Set.of()); // Terminal state
    }

    private void registerState(ParcelStatus status, Set<ParcelStatus> allowedTransitions) {
        states.put(status, new ParcelState() {
            @Override
            public ParcelStatus getStatus() {
                return status;
            }

            @Override
            public boolean canTransitionTo(ParcelStatus targetStatus) {
                return allowedTransitions.contains(targetStatus);
            }
        });
    }

    /**
     * Retrieves the state handler for a given status.
     */
    public ParcelState getState(ParcelStatus status) {
        ParcelState state = states.get(status);
        if (state == null) {
            throw new IllegalArgumentException("Unknown parcel status: " + status);
        }
        return state;
    }

    /**
     * Validates whether transition from current status to target status is permitted.
     *
     * @param current current parcel status
     * @param target intended next status
     * @return true if valid, false otherwise
     */
    public boolean canTransition(ParcelStatus current, ParcelStatus target) {
        return getState(current).canTransitionTo(target);
    }

    /**
     * Executes transition validation. Throws IllegalStateException if transition is disallowed.
     */
    public void transition(ParcelStatus current, ParcelStatus target) {
        getState(current).validateTransition(target);
    }
}
