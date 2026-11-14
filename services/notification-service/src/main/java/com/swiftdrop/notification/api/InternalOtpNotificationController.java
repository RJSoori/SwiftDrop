package com.swiftdrop.notification.api;

import com.swiftdrop.notification.dto.OtpNotificationRequest;
import com.swiftdrop.notification.service.SmsSender;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/v1/notifications")
public class InternalOtpNotificationController {

    private final SmsSender smsSender;

    public InternalOtpNotificationController(SmsSender smsSender) {
        this.smsSender = smsSender;
    }

    @PostMapping("/otp")
    public ResponseEntity<Void> sendOtp(@Valid @RequestBody OtpNotificationRequest request) {
        smsSender.sendOtp(request.parcelId(), request.code());
        return ResponseEntity.accepted().build();
    }
}
