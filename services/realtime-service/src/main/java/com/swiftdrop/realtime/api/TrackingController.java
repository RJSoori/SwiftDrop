package com.swiftdrop.realtime.api;

import com.swiftdrop.realtime.service.TrackingService;
import com.swiftdrop.realtime.service.TrackingView;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/tracking")
public class TrackingController {

    private final TrackingService trackingService;

    public TrackingController(TrackingService trackingService) {
        this.trackingService = trackingService;
    }

    @GetMapping("/{trackingNo}")
    public TrackingView getTracking(@PathVariable String trackingNo) {
        return trackingService.getTracking(trackingNo);
    }
}
