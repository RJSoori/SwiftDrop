package com.swiftdrop.auth.service;

import com.swiftdrop.auth.domain.OAuthAccount;
import com.swiftdrop.auth.domain.Role;
import com.swiftdrop.auth.domain.User;
import com.swiftdrop.auth.domain.UserStatus;
import com.swiftdrop.auth.repository.OAuthAccountRepository;
import com.swiftdrop.auth.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GoogleAccountServiceTest {

    private final OAuthAccountRepository oauthAccounts = mock(OAuthAccountRepository.class);
    private final UserRepository users = mock(UserRepository.class);
    private final GoogleAccountService service = new GoogleAccountService(oauthAccounts, users);

    private static User user(String email, String passwordHash, Role role) {
        User user = new User(email, passwordHash, "Name", null, role);
        ReflectionTestUtils.setField(user, "id", UUID.randomUUID());
        return user;
    }

    @Test
    void returnsAlreadyLinkedUser() {
        User existing = user("kamal@gmail.com", null, Role.CUSTOMER);
        when(oauthAccounts.findByProviderAndProviderSubject("google", "sub-1"))
                .thenReturn(Optional.of(new OAuthAccount(existing.getId(), "google", "sub-1")));
        when(users.findById(existing.getId())).thenReturn(Optional.of(existing));

        assertThat(service.findOrCreate("sub-1", "kamal@gmail.com", true, "Kamal")).isSameAs(existing);
        verify(users, never()).save(any());
        verify(oauthAccounts, never()).save(any());
    }

    @Test
    void linksExistingPasswordAccountByVerifiedEmail() {
        User passwordUser = user("nimal@gmail.com", "bcrypt-hash", Role.MERCHANT);
        when(oauthAccounts.findByProviderAndProviderSubject("google", "sub-2")).thenReturn(Optional.empty());
        when(users.findByEmail("nimal@gmail.com")).thenReturn(Optional.of(passwordUser));

        User result = service.findOrCreate("sub-2", "Nimal@Gmail.com", true, "Nimal");

        assertThat(result).isSameAs(passwordUser);
        assertThat(result.getRoles()).containsExactly(Role.MERCHANT); // linking never changes roles
        ArgumentCaptor<OAuthAccount> link = ArgumentCaptor.forClass(OAuthAccount.class);
        verify(oauthAccounts).save(link.capture());
        assertThat(link.getValue().getUserId()).isEqualTo(passwordUser.getId());
        assertThat(link.getValue().getProviderSubject()).isEqualTo("sub-2");
        verify(users, never()).save(any());
    }

    @Test
    void createsCustomerWithoutPasswordForNewGoogleUser() {
        when(oauthAccounts.findByProviderAndProviderSubject("google", "sub-3")).thenReturn(Optional.empty());
        when(users.findByEmail("new@gmail.com")).thenReturn(Optional.empty());
        when(users.save(any(User.class))).thenAnswer(inv -> {
            User u = inv.getArgument(0);
            ReflectionTestUtils.setField(u, "id", UUID.randomUUID());
            return u;
        });

        User created = service.findOrCreate("sub-3", "new@gmail.com", true, "New Person");

        assertThat(created.getRoles()).containsExactly(Role.CUSTOMER);
        assertThat(created.getPasswordHash()).isNull();
        assertThat(created.getFullName()).isEqualTo("New Person");
        verify(oauthAccounts).save(any(OAuthAccount.class));
    }

    @Test
    void refusesUnverifiedEmail() {
        when(oauthAccounts.findByProviderAndProviderSubject("google", "sub-4")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.findOrCreate("sub-4", "victim@gmail.com", false, "Attacker"))
                .isInstanceOf(ResponseStatusException.class);
        verify(users, never()).findByEmail(any());
        verify(oauthAccounts, never()).save(any());
    }

    @Test
    void refusesDisabledAccounts() {
        User disabled = user("old@gmail.com", null, Role.CUSTOMER);
        disabled.setStatus(UserStatus.DISABLED);
        when(oauthAccounts.findByProviderAndProviderSubject("google", "sub-5"))
                .thenReturn(Optional.of(new OAuthAccount(disabled.getId(), "google", "sub-5")));
        when(users.findById(disabled.getId())).thenReturn(Optional.of(disabled));

        assertThatThrownBy(() -> service.findOrCreate("sub-5", "old@gmail.com", true, "Old"))
                .isInstanceOf(ResponseStatusException.class);
    }
}
