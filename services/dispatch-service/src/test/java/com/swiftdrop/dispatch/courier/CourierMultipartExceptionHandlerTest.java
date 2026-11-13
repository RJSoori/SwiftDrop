package com.swiftdrop.dispatch.courier;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.util.unit.DataSize;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CourierMultipartExceptionHandlerTest {

    @Test
    void returnsProblemDetailForOversizedUploads() {
        CourierMultipartExceptionHandler handler =
                new CourierMultipartExceptionHandler(DataSize.ofMegabytes(5));

        var response = handler.handleMaxUploadSizeExceeded(
                new MaxUploadSizeExceededException(DataSize.ofMegabytes(5).toBytes()));

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals("Proof image must not exceed 5 MB", response.getBody().getDetail());
    }
}
