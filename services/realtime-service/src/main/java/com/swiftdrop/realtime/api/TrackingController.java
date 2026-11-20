package com.swiftdrop.realtime.api;

import com.swiftdrop.realtime.service.TrackingService;
import com.swiftdrop.realtime.service.TrackingView;
import com.swiftdrop.realtime.service.TrackingSseRegistry;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
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

    /**
     * "no-transform" stops proxies from compressing the stream. The web app's Next.js proxy gzips responses for
     * browsers, which buffers server-sent events, so without it the tracking page never received live updates.
     * X-Accel-Buffering does the same for nginx (the Kubernetes ingress).
     */
    @GetMapping(path = "/{trackingNo}/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public ResponseEntity<SseEmitter> streamTracking(@PathVariable String trackingNo) {
        TrackingView initialView = trackingService.getTracking(trackingNo);
        return ResponseEntity.ok()
                .header(HttpHeaders.CACHE_CONTROL, "no-cache, no-transform")
                .header("X-Accel-Buffering", "no")
                .body(sseRegistry.subscribe(trackingNo, initialView));
    }
}
