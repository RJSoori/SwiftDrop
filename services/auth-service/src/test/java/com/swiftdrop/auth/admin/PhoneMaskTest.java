package com.swiftdrop.auth.admin;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

class PhoneMaskTest {

    @ParameterizedTest
    @CsvSource({
            "+94771234567, +9477*****67",
            "+94112345678, +9411*****78",
            "1234567, *******",
            "12, **"})
    void masksAllButCountryCodePrefixAndLastTwoDigits(String phone, String masked) {
        assertThat(PhoneMask.mask(phone)).isEqualTo(masked);
    }

    @Test
    void nullOrBlankGivesNull() {
        assertThat(PhoneMask.mask(null)).isNull();
        assertThat(PhoneMask.mask(" ")).isNull();
    }
}
