package com.swiftdrop.parcel.zone;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.ErrorResponseException;

import java.util.List;
import java.util.Map;

/**
 * A zone, pricing rule or quote location the business rules reject. Rendered by the shared exception handler as an
 * RFC 7807 body with the same {@code errors: [{field, message}]} list Bean Validation produces, so the web forms can
 * show the message next to the field.
 */
public class ZoneValidationException extends ErrorResponseException {

    private final String field;

    public ZoneValidationException(HttpStatus status, String field, String message) {
        super(status, problem(status, field, message), null);
        this.field = field;
    }

    public static ZoneValidationException badRequest(String field, String message) {
        return new ZoneValidationException(HttpStatus.BAD_REQUEST, field, message);
    }

    public String getField() {
        return field;
    }

    private static ProblemDetail problem(HttpStatus status, String field, String message) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, message);
        problem.setProperty("errors", List.of(Map.of("field", field, "message", message)));
        return problem;
    }
}
