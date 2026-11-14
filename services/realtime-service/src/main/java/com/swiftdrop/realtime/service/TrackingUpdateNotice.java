package com.swiftdrop.realtime.service;

import java.util.UUID;

public record TrackingUpdateNotice(String trackingNo, UUID courierId) {
}
