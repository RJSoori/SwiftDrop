package com.swiftdrop.realtime.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.swiftdrop.realtime.config.TrackingRedisConfiguration;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class TrackingUpdatePublisher {

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    public TrackingUpdatePublisher(StringRedisTemplate redisTemplate, ObjectMapper objectMapper) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    public void publish(String trackingNo, UUID courierId) {
        try {
            String notice = objectMapper.writeValueAsString(new TrackingUpdateNotice(trackingNo, courierId));
            redisTemplate.convertAndSend(TrackingRedisConfiguration.TRACKING_UPDATES_CHANNEL, notice);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Could not serialize tracking update notice", exception);
        }
    }
}
