package com.swiftdrop.auth.service;

import com.swiftdrop.auth.config.AuthProperties;
import com.swiftdrop.auth.domain.RefreshToken;
import com.swiftdrop.auth.domain.Role;
import com.swiftdrop.auth.domain.User;
import com.swiftdrop.auth.domain.UserStatus;
import com.swiftdrop.auth.repository.RefreshTokenRepository;
import com.swiftdrop.auth.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RefreshTokenServiceTest {

    private final RefreshTokenRepository tokens = mock(RefreshTokenRepository.class);
    private final UserRepository users = mock(UserRepository.class);
    private final AuthProperties props = new AuthProperties("iss", Duration.ofMinutes(15), Duration.ofDays(7),
            false, "http://localhost:3000", null, null, null);
    private final RefreshTokenService service = new RefreshTokenService(tokens, users, props);

    private User user;

    @BeforeEach
    void setUp() {
        user = new User("a@example.com", "hash", "A", null, Role.CUSTOMER);
        ReflectionTestUtils.setField(user, "id", UUID.randomUUID());
        when(users.findById(user.getId())).thenReturn(Optional.of(user));
        // save() assigns an id like JPA would
        when(tokens.save(any(RefreshToken.class))).thenAnswer(inv -> {
            RefreshToken t = inv.getArgument(0);
            ReflectionTestUtils.setField(t, "id", UUID.randomUUID());
            return t;
        });
    }

    private RefreshToken stored(String raw, Instant expiresAt) {
        RefreshToken token = new RefreshToken(user.getId(), RefreshTokenService.hash(raw), UUID.randomUUID(), expiresAt);
        ReflectionTestUtils.setField(token, "id", UUID.randomUUID());
        when(tokens.findByTokenHash(RefreshTokenService.hash(raw))).thenReturn(Optional.of(token));
        return token;
    }

    @Test
    void issueStoresOnlyTheHash() {
        UUID family = UUID.randomUUID();
        String raw = service.issue(user, family);

        ArgumentCaptor<RefreshToken> saved = ArgumentCaptor.forClass(RefreshToken.class);
        verify(tokens).save(saved.capture());
        assertThat(saved.getValue().getTokenHash()).isEqualTo(RefreshTokenService.hash(raw)).isNotEqualTo(raw);
        assertThat(saved.getValue().getFamilyId()).isEqualTo(family);
        assertThat(saved.getValue().getExpiresAt()).isAfter(Instant.now().plus(Duration.ofDays(6)));
    }

    @Test
    void rotateReplacesTokenWithinSameFamily() {
        RefreshToken current = stored("old", Instant.now().plusSeconds(60));

        RefreshTokenService.Rotation rotation = service.rotate("old");

        assertThat(rotation.user()).isSameAs(user);
        assertThat(rotation.newToken()).isNotBlank().isNotEqualTo("old");
        assertThat(current.getRevokedAt()).isNotNull();
        assertThat(current.getReplacedBy()).isNotNull();
        ArgumentCaptor<RefreshToken> saved = ArgumentCaptor.forClass(RefreshToken.class);
        verify(tokens).save(saved.capture());
        assertThat(saved.getValue().getFamilyId()).isEqualTo(current.getFamilyId());
        verify(tokens, never()).revokeFamily(any(), any());
    }

    @Test
    void reusingARotatedTokenRevokesTheWholeFamily() {
        RefreshToken current = stored("old", Instant.now().plusSeconds(60));
        current.markReplaced(UUID.randomUUID(), Instant.now());

        assertThatThrownBy(() -> service.rotate("old"))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(e -> ((ResponseStatusException) e).getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);
        verify(tokens).revokeFamily(eq(current.getFamilyId()), any());
    }

    @Test
    void rejectsExpiredUnknownAndDisabledUserTokens() {
        stored("expired", Instant.now().minusSeconds(1));
        assertThatThrownBy(() -> service.rotate("expired")).isInstanceOf(ResponseStatusException.class);

        when(tokens.findByTokenHash(anyString())).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.rotate("unknown")).isInstanceOf(ResponseStatusException.class);

        stored("valid", Instant.now().plusSeconds(60));
        user.setStatus(UserStatus.DISABLED);
        assertThatThrownBy(() -> service.rotate("valid")).isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void revokeRevokesFamily() {
        RefreshToken current = stored("t", Instant.now().plusSeconds(60));
        service.revoke("t");
        verify(tokens).revokeFamily(eq(current.getFamilyId()), any());
    }
}
