package com.swiftdrop.auth.api.dto;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class ValidationRulesTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    private Set<String> invalidFields(String password, String phone) {
        return validator.validate(new SignupRequest("nimal@example.com", password, "Nimal Perera", phone)).stream()
                .map(ConstraintViolation::getPropertyPath)
                .map(Object::toString)
                .collect(java.util.stream.Collectors.toSet());
    }

    @ParameterizedTest
    @ValueSource(strings = {"password1", "A1b2c3d4", "correct horse battery 9"})
    void acceptsPasswordsWithLetterAndDigit(String password) {
        assertThat(invalidFields(password, "0771234567")).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"short1", "allletters", "12345678", ""})
    void rejectsWeakPasswords(String password) {
        assertThat(invalidFields(password, "0771234567")).contains("password");
    }

    @Test
    void rejectsPasswordsLongerThanBcryptLimit() {
        assertThat(invalidFields("a1".repeat(37), "0771234567")).contains("password");
    }

    @ParameterizedTest
    @ValueSource(strings = {"0771234567", "+94771234567", "0112345678", "+94112345678"})
    void acceptsSriLankanPhones(String phone) {
        assertThat(invalidFields("password1", phone)).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"771234567", "+9477123456", "077123456789", "0071234567", "+14155552671", "07712345ab"})
    void rejectsOtherPhones(String phone) {
        assertThat(invalidFields("password1", phone)).contains("phone");
    }

    @Test
    void normalisesPhonesToPlus94() {
        assertThat(ValidationRules.normalizePhone(" 0771234567 ")).isEqualTo("+94771234567");
        assertThat(ValidationRules.normalizePhone("+94771234567")).isEqualTo("+94771234567");
    }
}
