package com.swiftdrop.parcel.domain.state;

import com.swiftdrop.parcel.domain.ParcelStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ParcelStateMachineTest {

    private final ParcelStateMachine stateMachine = new ParcelStateMachine();

    @Test
    @DisplayName("Valid transition path from CREATED to DELIVERED")
    void testHappyPathTransitions() {
        assertTrue(stateMachine.canTransition(ParcelStatus.CREATED, ParcelStatus.PICKED_UP));
        assertTrue(stateMachine.canTransition(ParcelStatus.PICKED_UP, ParcelStatus.IN_TRANSIT));
        assertTrue(stateMachine.canTransition(ParcelStatus.IN_TRANSIT, ParcelStatus.OUT_FOR_DELIVERY));
        assertTrue(stateMachine.canTransition(ParcelStatus.OUT_FOR_DELIVERY, ParcelStatus.DELIVERED));
    }

    @Test
    @DisplayName("Invalid transition should throw IllegalStateException")
    void testDisallowedTransitions() {
        assertFalse(stateMachine.canTransition(ParcelStatus.CREATED, ParcelStatus.DELIVERED));
        assertThrows(IllegalStateException.class, () ->
            stateMachine.transition(ParcelStatus.CREATED, ParcelStatus.DELIVERED)
        );
    }

    @Test
    @DisplayName("Failed parcel can be re-dispatched to OUT_FOR_DELIVERY")
    void testFailedReAttemptLoop() {
        assertTrue(stateMachine.canTransition(ParcelStatus.OUT_FOR_DELIVERY, ParcelStatus.FAILED));
        assertTrue(stateMachine.canTransition(ParcelStatus.FAILED, ParcelStatus.OUT_FOR_DELIVERY));
    }

    @Test
    @DisplayName("Terminal state DELIVERED allows no further transitions")
    void testDeliveredTerminalState() {
        assertFalse(stateMachine.canTransition(ParcelStatus.DELIVERED, ParcelStatus.OUT_FOR_DELIVERY));
        assertFalse(stateMachine.canTransition(ParcelStatus.DELIVERED, ParcelStatus.CANCELLED));
    }
}
