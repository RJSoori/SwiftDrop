package com.swiftdrop.dispatch.courier;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.unit.DataSize;

import java.time.Duration;

@ConfigurationProperties(prefix = "swiftdrop.courier")
public record CourierProperties(Duration otpTtl, int otpMaxAttempts, DataSize proofMaxFileSize, String proofBucket) {
}
