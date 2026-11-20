package com.swiftdrop.realtime.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Component
public class TrackingSseRegistry {

    private static final Logger log = LoggerFactory.getLogger(TrackingSseRegistry.class);
    private final ConcurrentMap<String, Set<SseEmitter>> emittersByTrackingNo = new ConcurrentHashMap<>();

    public SseEmitter subscribe(String trackingNo) {
        SseEmitter emitter = new SseEmitter(0L);
        Set<SseEmitter> emitters = emittersByTrackingNo.computeIfAbsent(trackingNo, ignored -> ConcurrentHashMap.newKeySet());
        emitters.add(emitter);
        log.debug("Tracking stream subscribed for {} ({} active)", trackingNo, emitters.size());
        Runnable remove = () -> remove(trackingNo, emitter);
        emitter.onCompletion(remove);
        emitter.onTimeout(remove);
        emitter.onError(ignored -> remove.run());
        return emitter;
    }

    public SseEmitter subscribe(String trackingNo, TrackingView initialView) {
        SseEmitter emitter = subscribe(trackingNo);
        try {
            emitter.send(SseEmitter.event().name("tracking").data(initialView));
        } catch (IOException exception) {
            remove(trackingNo, emitter);
            emitter.completeWithError(exception);
            throw new IllegalStateException("Could not send initial tracking snapshot", exception);
        }
        return emitter;
    }

    public void publish(String trackingNo, TrackingView trackingView) {
        Set<SseEmitter> emitters = emittersByTrackingNo.get(trackingNo);
        if (emitters == null) {
            log.debug("No active tracking streams for {}", trackingNo);
            return;
        }
        log.debug("Publishing tracking update for {} to {} stream(s)", trackingNo, emitters.size());
        for (SseEmitter emitter : emitters) {
            try {
                emitter.send(SseEmitter.event().name("tracking").data(trackingView));
            } catch (IOException exception) {
                log.debug("Removing disconnected tracking stream for {}", trackingNo);
                remove(trackingNo, emitter);
                emitter.completeWithError(exception);
            }
        }
    }

    private void remove(String trackingNo, SseEmitter emitter) {
        Set<SseEmitter> emitters = emittersByTrackingNo.get(trackingNo);
        if (emitters != null) {
            emitters.remove(emitter);
            if (emitters.isEmpty()) {
                emittersByTrackingNo.remove(trackingNo, emitters);
            }
        }
    }
}
