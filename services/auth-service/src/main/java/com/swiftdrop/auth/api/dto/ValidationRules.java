package com.swiftdrop.auth.api.dto;

/** Shared input rules. apps/web/src/lib/schemas.ts mirrors these; keep them in sync. */
public final class ValidationRules {

    private ValidationRules() {
    }

    /** 8-72 chars (BCrypt ignores anything past 72 bytes) with at least one letter and one digit. */
    public static final String PASSWORD = "^(?=.*[A-Za-z])(?=.*\\d).{8,72}$";
    public static final String PASSWORD_MESSAGE = "must be 8-72 characters and include a letter and a number";

    /** Sri Lankan numbers: 0XXXXXXXXX or +94XXXXXXXXX (mobiles are 07X..., landlines 011... etc.). */
    public static final String PHONE = "^(?:0|\\+94)[1-9]\\d{8}$";
    public static final String PHONE_MESSAGE = "must be a Sri Lankan number like 0771234567 or +94771234567";

    /** Stores phones in one canonical form: +94XXXXXXXXX. */
    public static String normalizePhone(String phone) {
        String trimmed = phone.trim();
        return trimmed.startsWith("0") ? "+94" + trimmed.substring(1) : trimmed;
    }
}
