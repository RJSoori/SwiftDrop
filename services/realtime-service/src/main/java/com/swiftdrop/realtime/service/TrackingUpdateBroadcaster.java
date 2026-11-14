package com.swiftdrop.realtime.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.swiftdrop.realtime.repository.TrackingViewRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

@Component
public class TrackingUpdateBroadcaster implements MessageListener {

    private static final Logger log = LoggerFactory.getLogger(TrackingUpdateBroadcaster.class);
    private final TrackingViewRepository trackingViewRepository;
    private final TrackingService trackingService;
    private final TrackingSseRegistry sseRegistry;
    private final SimpMessagingTemplate messagingTemplate;
    private final ObjectMapper objectMapper;

    public TrackingUpdateBroadcaster(TrackingViewRepository trackingViewRepository, TrackingService trackingService,
                                     TrackingSseRegistry sseRegistry, SimpMessagingTemplate messagingTemplate,
                                     ObjectMapper objectMapper) {
        this.trackingViewRepository = trackingViewRepository;
        this.trackingService = trackingService;
        this.sseRegistry = sseRegistry;
        this.messagingTemplate = messagingTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    public void onMessage(Message message, byte[] pattern) {
        TrackingUpdateNotice notice;
        try {
            notice = objectMapper.readValue(new String(message.getBody(), StandardCharsets.UTF_8), TrackingUpdateNotice.class);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("Tracking update notice is not valid JSON", exception);
        }
        String trackingNo = notice.trackingNo();
        log.debug("Received Redis tracking update notice for {}", trackingNo);
        var record = trackingViewRepository.findByTrackingNo(trackingNo)
                .orElseThrow(() -> new IllegalStateException("Tracking update references an unknown tracking number"));
        TrackingView view = trackingService.getTracking(trackingNo);
        sseRegistry.publish(trackingNo, view);
        String payload = serialize(view);
        messagingTemplate.convertAndSend("/topic/parcels/" + record.parcelId(), payload);
        messagingTemplate.convertAndSend("/topic/dispatch", payload);
        if (notice.courierId() != null) {
            messagingTemplate.convertAndSend("/topic/courier/" + notice.courierId(), payload);
        }
    }

    private String serialize(TrackingView view) {
        try {
            return objectMapper.writeValueAsString(view);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Could not serialize tracking update", exception);
        }
    }
}
