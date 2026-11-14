package com.swiftdrop.realtime.service;

import com.swiftdrop.realtime.repository.ParcelAccessClient;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

import java.security.Principal;
import java.util.List;
import java.util.UUID;

@Component
public class TrackingStompAuthorizationInterceptor implements ChannelInterceptor {

    private final JwtDecoder jwtDecoder;
    private final JwtAuthenticationConverter jwtAuthenticationConverter;
    private final ParcelAccessClient parcelAccessClient;

    public TrackingStompAuthorizationInterceptor(JwtDecoder jwtDecoder,
                                                 JwtAuthenticationConverter jwtAuthenticationConverter,
                                                 ParcelAccessClient parcelAccessClient) {
        this.jwtDecoder = jwtDecoder;
        this.jwtAuthenticationConverter = jwtAuthenticationConverter;
        this.parcelAccessClient = parcelAccessClient;
    }

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(message);
        StompCommand command = accessor.getCommand();
        if (command == StompCommand.CONNECT) {
            accessor.setUser(authenticate(accessor));
            return MessageBuilder.createMessage(message.getPayload(), accessor.getMessageHeaders());
        }
        if (command == StompCommand.SUBSCRIBE) {
            Authentication authentication = requireJwt(accessor.getUser());
            authorizeSubscription(authentication, accessor.getDestination());
            return message;
        }
        if (command == StompCommand.DISCONNECT || command == StompCommand.UNSUBSCRIBE
                || command == StompCommand.ACK || command == StompCommand.NACK) {
            return message;
        }
        throw new AccessDeniedException("Client message is not allowed");
    }

    private Authentication authenticate(StompHeaderAccessor accessor) {
        Principal principal = accessor.getUser();
        if (principal instanceof Authentication authentication && authentication.isAuthenticated()) {
            return requireJwt(authentication);
        }
        List<String> authorizationHeaders = accessor.getNativeHeader("Authorization");
        if (authorizationHeaders == null || authorizationHeaders.isEmpty()
                || !authorizationHeaders.getFirst().startsWith("Bearer ")) {
            throw new AccessDeniedException("A valid access token is required");
        }
        Jwt jwt = jwtDecoder.decode(authorizationHeaders.getFirst().substring("Bearer ".length()));
        Authentication authentication = jwtAuthenticationConverter.convert(jwt);
        return requireJwt(authentication);
    }

    private Authentication requireJwt(Principal principal) {
        if (principal instanceof JwtAuthenticationToken authentication && authentication.isAuthenticated()) {
            return authentication;
        }
        throw new AccessDeniedException("A valid access token is required");
    }

    private void authorizeSubscription(Authentication authentication, String destination) {
        if (destination == null || !(authentication.getPrincipal() instanceof Jwt jwt)) {
            throw new AccessDeniedException("Subscription is not allowed");
        }
        UUID userId;
        try {
            userId = UUID.fromString(jwt.getSubject());
        } catch (IllegalArgumentException exception) {
            throw new AccessDeniedException("Subscription is not allowed");
        }
        List<String> roles = jwt.getClaimAsStringList("roles");

        if ("/topic/dispatch".equals(destination)) {
            requireRole(roles, "DISPATCHER", "ADMIN");
            return;
        }
        if (destination.startsWith("/topic/parcels/")) {
            UUID parcelId = parseDestinationId(destination, "/topic/parcels/");
            boolean owner = roles != null && roles.contains("CUSTOMER")
                    && parcelAccessClient.canAccess(parcelId, userId, "CUSTOMER");
            boolean merchant = roles != null && roles.contains("MERCHANT")
                    && parcelAccessClient.canAccess(parcelId, userId, "MERCHANT");
            if (!owner && !merchant) {
                throw new AccessDeniedException("Subscription is not allowed");
            }
            return;
        }
        if (destination.startsWith("/topic/courier/")) {
            UUID courierId = parseDestinationId(destination, "/topic/courier/");
            if (roles == null || !roles.contains("COURIER") || !courierId.equals(userId)) {
                throw new AccessDeniedException("Subscription is not allowed");
            }
            return;
        }
        throw new AccessDeniedException("Subscription is not allowed");
    }

    private UUID parseDestinationId(String destination, String prefix) {
        String value = destination.substring(prefix.length());
        if (value.isBlank() || value.contains("/")) {
            throw new AccessDeniedException("Subscription is not allowed");
        }
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException exception) {
            throw new AccessDeniedException("Subscription is not allowed");
        }
    }

    private void requireRole(List<String> roles, String first, String second) {
        if (roles == null || (!roles.contains(first) && !roles.contains(second))) {
            throw new AccessDeniedException("Subscription is not allowed");
        }
    }
}
