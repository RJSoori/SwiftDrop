package com.swiftdrop.realtime.service;

import com.swiftdrop.realtime.repository.ParcelAccessClient;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TrackingStompAuthorizationInterceptorTest {

    private final JwtDecoder jwtDecoder = mock(JwtDecoder.class);
    private final JwtAuthenticationConverter converter = mock(JwtAuthenticationConverter.class);
    private final ParcelAccessClient parcelAccessClient = mock(ParcelAccessClient.class);
    private final TrackingStompAuthorizationInterceptor interceptor =
            new TrackingStompAuthorizationInterceptor(jwtDecoder, converter, parcelAccessClient);
    private final MessageChannel channel = mock(MessageChannel.class);

    @Test
    void authenticatedCustomerCanSubscribeOnlyToOwnedParcel() {
        UUID userId = UUID.randomUUID();
        UUID parcelId = UUID.randomUUID();
        when(parcelAccessClient.canAccess(parcelId, userId, "CUSTOMER")).thenReturn(true);

        Message<?> result = send(StompCommand.SUBSCRIBE, "/topic/parcels/" + parcelId, authentication(userId, "CUSTOMER"));

        assertThat(result).isNotNull();
        verify(parcelAccessClient).canAccess(parcelId, userId, "CUSTOMER");
    }

    @Test
    void customerCannotSubscribeToAnotherOwnersParcel() {
        UUID userId = UUID.randomUUID();
        UUID parcelId = UUID.randomUUID();
        when(parcelAccessClient.canAccess(parcelId, userId, "CUSTOMER")).thenReturn(false);

        assertThatThrownBy(() -> send(StompCommand.SUBSCRIBE, "/topic/parcels/" + parcelId,
                authentication(userId, "CUSTOMER"))).isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void onlyDispatcherAndAdminCanSubscribeToDispatchTopic() {
        UUID userId = UUID.randomUUID();

        Message<?> result = send(StompCommand.SUBSCRIBE, "/topic/dispatch", authentication(userId, "DISPATCHER"));

        assertThat(result).isNotNull();
        verify(parcelAccessClient, never()).canAccess(org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void courierCanSubscribeOnlyToTheirOwnTopic() {
        UUID courierId = UUID.randomUUID();

        assertThat(send(StompCommand.SUBSCRIBE, "/topic/courier/" + courierId,
                authentication(courierId, "COURIER"))).isNotNull();
        assertThatThrownBy(() -> send(StompCommand.SUBSCRIBE, "/topic/courier/" + UUID.randomUUID(),
                authentication(courierId, "COURIER"))).isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void connectWithoutValidatedPrincipalOrBearerTokenIsRejected() {
        assertThatThrownBy(() -> send(StompCommand.CONNECT, null, null))
                .isInstanceOf(AccessDeniedException.class);
    }

    private Message<?> send(StompCommand command, String destination, JwtAuthenticationToken authentication) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(command);
        accessor.setDestination(destination);
        accessor.setUser(authentication);
        Message<byte[]> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
        return interceptor.preSend(message, channel);
    }

    private JwtAuthenticationToken authentication(UUID userId, String role) {
        Instant now = Instant.now();
        Jwt jwt = Jwt.withTokenValue("test-token")
                .header("alg", "none")
                .subject(userId.toString())
                .claim("roles", List.of(role))
                .issuedAt(now)
                .expiresAt(now.plusSeconds(60))
                .build();
        return new JwtAuthenticationToken(jwt, List.of(new SimpleGrantedAuthority("ROLE_" + role)));
    }
}
