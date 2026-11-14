package com.swiftdrop.realtime.api;

import com.swiftdrop.realtime.service.TrackingService;
import com.swiftdrop.realtime.service.TrackingView;
import com.swiftdrop.realtime.service.TrackingSseRegistry;
import org.springframework.http.MediaType;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/tracking")
public class TrackingController {

    private final TrackingService trackingService;
    private final TrackingSseRegistry sseRegistry;

    public TrackingController(TrackingService trackingService, TrackingSseRegistry sseRegistry) {
        this.trackingService = trackingService;
        this.sseRegistry = sseRegistry;
    }

    @GetMapping("/{trackingNo}")
    public TrackingView getTracking(@PathVariable String trackingNo) {
        return trackingService.getTracking(trackingNo);
    }

    @GetMapping(path = "/{trackingNo}/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamTracking(@PathVariable String trackingNo) {
        TrackingView initialView = trackingService.getTracking(trackingNo);
        return sseRegistry.subscribe(trackingNo, initialView);
    }
}
