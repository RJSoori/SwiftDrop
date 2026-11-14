package com.swiftdrop.reporting.domain;

/** The daily_stats column an event adds one to. */
public enum StatsCounter {
    CREATED("created_count"),
    DELIVERED("delivered_count"),
    FAILED("failed_count");

    private final String column;

    StatsCounter(String column) {
        this.column = column;
    }

    /** A fixed column name, never user input, so it is safe to put in SQL. */
    public String column() {
        return column;
    }
}
