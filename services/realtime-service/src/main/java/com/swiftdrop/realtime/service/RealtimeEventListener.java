package com.swiftdrop.realtime.service;

import com.swiftdrop.common.events.EventTopology;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
public class RealtimeEventListener {

    private final TrackingEventProcessor trackingEventProcessor;
    private final TrackingUpdatePublisher trackingUpdatePublisher;

    public RealtimeEventListener(TrackingEventProcessor trackingEventProcessor,
                                 TrackingUpdatePublisher trackingUpdatePublisher) {
        this.trackingEventProcessor = trackingEventProcessor;
        this.trackingUpdatePublisher = trackingUpdatePublisher;
    }

    @RabbitListener(queues = EventTopology.REALTIME_QUEUE)
    public void handle(String payload) {
        TrackingUpdateNotice notice = trackingEventProcessor.process(payload);
        if (notice != null) {
            trackingUpdatePublisher.publish(notice.trackingNo(), notice.courierId());
        }
    }
}
