package com.swiftdrop.auth.admin;

/**
 * Masks a stored phone (+94XXXXXXXXX) for API responses: country code and the first two digits, then the last two,
 * e.g. +94771234567 -> +9477*****67. The full number is never needed to manage an account.
 */
public final class PhoneMask {

    private static final int KEEP_START = 5;
    private static final int KEEP_END = 2;

    private PhoneMask() {
    }

    public static String mask(String phone) {
        if (phone == null || phone.isBlank()) {
            return null;
        }
        if (phone.length() <= KEEP_START + KEEP_END) {
            return "*".repeat(phone.length());
        }
        return phone.substring(0, KEEP_START)
                + "*".repeat(phone.length() - KEEP_START - KEEP_END)
                + phone.substring(phone.length() - KEEP_END);
    }
}
