package com.rental.auth.service;

import com.rental.auth.dto.response.LoginResponse;
import com.rental.auth.entity.AppUser;
import com.rental.auth.enums.Role;
import com.rental.auth.exception.ApiException;
import com.rental.auth.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class GoogleLoginServiceTest {
    private GoogleIdTokenVerifier google;
    private UserRepository users;
    private PasswordEncoder passwords;
    private AuthService auth;
    private GoogleLoginService service;
    private final GoogleIdTokenVerifier.GoogleIdentity identity =
            new GoogleIdTokenVerifier.GoogleIdentity("google-sub-123", "user@gmail.com", "Test User");

    @BeforeEach void setUp() {
        google = mock(GoogleIdTokenVerifier.class);
        users = mock(UserRepository.class);
        passwords = mock(PasswordEncoder.class);
        auth = mock(AuthService.class);
        service = new GoogleLoginService(google, users, passwords, auth);
        when(google.verify("valid-google-token")).thenReturn(identity);
    }

    @Test void firstGoogleLogin_createsCustomerThenIssuesAppJwt() {
        when(users.findByGoogleSub(identity.subject())).thenReturn(Optional.empty());
        when(users.existsByEmailIgnoreCase(identity.email())).thenReturn(false);
        when(passwords.encode(anyString())).thenReturn("bcrypt-hash");
        when(users.saveAndFlush(any(AppUser.class))).thenAnswer(inv -> inv.getArgument(0));
        LoginResponse expected = new LoginResponse("app-jwt", "Bearer", Instant.now(), "CUSTOMER", UUID.randomUUID());
        when(auth.issueToken(any(AppUser.class))).thenReturn(expected);

        assertSame(expected, service.login("valid-google-token"));
        verify(users).saveAndFlush(argThat(u -> u.getRole() == Role.CUSTOMER
                && identity.subject().equals(u.getGoogleSub())
                && identity.email().equals(u.getEmail())
                && u.getPhone() == null));
    }

    @Test void existingGoogleIdentity_reusesUser() {
        AppUser existing = new AppUser("u", "hash", "User", identity.email(), null, Role.STAFF);
        existing.setGoogleSub(identity.subject());
        when(users.findByGoogleSub(identity.subject())).thenReturn(Optional.of(existing));
        LoginResponse expected = new LoginResponse("app-jwt", "Bearer", Instant.now(), "STAFF", UUID.randomUUID());
        when(auth.issueToken(existing)).thenReturn(expected);
        assertSame(expected, service.login("valid-google-token"));
        verify(users, never()).saveAndFlush(any());
    }

    @Test void duplicateEmail_requiresExplicitLink() {
        when(users.findByGoogleSub(identity.subject())).thenReturn(Optional.empty());
        when(users.existsByEmailIgnoreCase(identity.email())).thenReturn(true);
        ApiException ex = assertThrows(ApiException.class,
                () -> service.login("valid-google-token"));
        assertEquals("ACCOUNT_LINK_REQUIRED", ex.getCode());
        verify(auth, never()).issueToken(any());
    }

    @Test void existingPasswordUser_canSecurelyLinkMatchingGoogleAccount() {
        UUID id = UUID.randomUUID();
        AppUser existing = new AppUser("user", "hash", "User", identity.email(), null, Role.CUSTOMER);
        when(users.findById(id)).thenReturn(Optional.of(existing));
        when(users.findByGoogleSub(identity.subject())).thenReturn(Optional.empty());
        service.link(id, "valid-google-token");
        assertEquals(identity.subject(), existing.getGoogleSub());
        verify(users).saveAndFlush(existing);
    }

    @Test void linkRejectsMismatchedEmail() {
        UUID id = UUID.randomUUID();
        AppUser existing = new AppUser("user", "hash", "User", "other@example.com", null, Role.OWNER);
        when(users.findById(id)).thenReturn(Optional.of(existing));
        ApiException ex = assertThrows(ApiException.class,
                () -> service.link(id, "valid-google-token"));
        assertEquals("GOOGLE_EMAIL_MISMATCH", ex.getCode());
    }
}
