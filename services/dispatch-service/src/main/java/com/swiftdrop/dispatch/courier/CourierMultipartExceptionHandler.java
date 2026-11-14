package com.swiftdrop.dispatch.courier;

import com.swiftdrop.common.web.RequestIdFilter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.util.unit.DataSize;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
public class CourierMultipartExceptionHandler {

    private final DataSize maxFileSize;

    public CourierMultipartExceptionHandler(@Value("${COURIER_PROOF_MAX_FILE_SIZE:5MB}") DataSize maxFileSize) {
        this.maxFileSize = maxFileSize;
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ProblemDetail> handleMaxUploadSizeExceeded(MaxUploadSizeExceededException exception) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST,
                "Proof image must not exceed " + maxFileSize.toMegabytes() + " MB");
        problem.setProperty("traceId", RequestIdFilter.current());
        return ResponseEntity.badRequest().body(problem);
    }
}
