package com.swiftdrop.realtime.config;

import com.swiftdrop.realtime.service.TrackingUpdateBroadcaster;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;

@Configuration
public class TrackingRedisConfiguration {

    public static final String TRACKING_UPDATES_CHANNEL = "realtime:tracking-updates";

    @Bean
    ChannelTopic trackingUpdatesTopic() {
        return new ChannelTopic(TRACKING_UPDATES_CHANNEL);
    }

    @Bean
    RedisMessageListenerContainer trackingUpdateListenerContainer(
            RedisConnectionFactory connectionFactory, TrackingUpdateBroadcaster broadcaster,
            ChannelTopic trackingUpdatesTopic) {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);
        container.addMessageListener(broadcaster, trackingUpdatesTopic);
        return container;
    }
}
