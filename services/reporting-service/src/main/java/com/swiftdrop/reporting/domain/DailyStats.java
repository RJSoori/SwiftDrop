package com.swiftdrop.reporting.domain;

import java.time.LocalDate;

/** Delivery counts for one Sri Lankan calendar day. */
public record DailyStats(LocalDate day, long created, long delivered, long failed) {

    public static DailyStats empty(LocalDate day) {
        return new DailyStats(day, 0, 0, 0);
    }
}
