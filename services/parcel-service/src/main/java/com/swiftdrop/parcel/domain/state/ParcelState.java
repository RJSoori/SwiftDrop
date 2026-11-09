package com.swiftdrop.parcel.domain.state;

import com.swiftdrop.parcel.domain.ParcelStatus;

/**
 * State interface in the State Pattern for parcel lifecycle transitions.
 */
public interface ParcelState {

    /**
     * Returns the enum status associated with this state.
     */
    ParcelStatus getStatus();

    /**
     * Checks whether transition to the target status is allowed from this state.
     */
    boolean canTransitionTo(ParcelStatus targetStatus);

    /**
     * Validates transition to the target status. Throws IllegalStateException if invalid.
     */
    default void validateTransition(ParcelStatus targetStatus) {
        if (!canTransitionTo(targetStatus)) {
            throw new IllegalStateException("Illegal transition from state " + getStatus() + " to " + targetStatus);
        }
    }
}
