package com.swiftdrop.auth.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.swiftdrop.auth.domain.*;
import com.swiftdrop.auth.repository.MerchantAccountRepository;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.*;

class MerchantKeyServiceTest {
    JdbcTemplate db = mock(JdbcTemplate.class);
    MerchantAccountRepository accounts = mock(MerchantAccountRepository.class);
    TokenService tokens = mock(TokenService.class);
    MerchantKeyService service = new MerchantKeyService(db, accounts, tokens);

    @Test
    void approvedMerchantReceivesRandomKeyButOnlyHashIsStored() {
        UUID userId = UUID.randomUUID();
        var user = new User("shop@test.invalid", "hash", "Shop", null, Role.MERCHANT);
        ReflectionTestUtils.setField(user, "id", userId);
        var account = new MerchantAccount(user, "Shop", "REG-1");
        account.approve(UUID.randomUUID());
        when(accounts.findByUserId(userId)).thenReturn(Optional.of(account));
        var a = service.create(userId);
        var b = service.create(userId);
        assertThat(a.apiKey()).startsWith("sd_live_").isNotEqualTo(b.apiKey());
        verify(db)
                .update(
                        anyString(),
                        eq(a.id()),
                        eq(userId),
                        argThat(
                                (Object hash) ->
                                        hash instanceof String
                                                && ((String) hash).length() == 64
                                                && !hash.equals(a.apiKey())),
                        eq(a.prefix()));
    }

    @Test
    void pendingAndInactiveMerchantsCannotCreateKeys() {
        UUID userId = UUID.randomUUID();
        var user = new User("shop@test.invalid", "hash", "Shop", null, Role.MERCHANT);
        var account = new MerchantAccount(user, "Shop", "REG-1");
        when(accounts.findByUserId(userId)).thenReturn(Optional.of(account));
        assertThatThrownBy(() -> service.create(userId))
                .isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
        account.approve(UUID.randomUUID());
        user.setStatus(UserStatus.DISABLED);
        assertThatThrownBy(() -> service.create(userId))
                .isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
        verifyNoInteractions(db, tokens);
    }
}
