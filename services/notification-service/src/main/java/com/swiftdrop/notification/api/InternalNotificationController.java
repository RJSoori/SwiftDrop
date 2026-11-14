package com.swiftdrop.notification.api;

import com.swiftdrop.notification.service.Notifier;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/** Internal API (INTERNAL_TOKEN only, never routed by the gateway). dispatch-service sends delivery OTPs here. */
@RestController
@RequestMapping("/internal/v1/notifications")
public class InternalNotificationController {

    public record OtpRequest(@NotNull UUID parcelId, @NotNull @Pattern(regexp = "\\d{4}") String code) {}

    private final Notifier notifier;

    public InternalNotificationController(Notifier notifier) {
        this.notifier = notifier;
    }

    @PostMapping("/otp")
    public ResponseEntity<Void> sendOtp(@Valid @RequestBody OtpRequest request) {
        notifier.sendOtp(request.parcelId(), request.code());
        return ResponseEntity.accepted().build();
    }
}
