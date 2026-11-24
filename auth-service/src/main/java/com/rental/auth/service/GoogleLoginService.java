package com.rental.auth.service;

import com.rental.auth.dto.response.LoginResponse;
import com.rental.auth.entity.AppUser;
import com.rental.auth.enums.Role;
import com.rental.auth.enums.UserStatus;
import com.rental.auth.exception.ApiException;
import com.rental.auth.repository.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.UUID;

@Service
public class GoogleLoginService {
    private final GoogleIdTokenVerifier google;
    private final UserRepository users;
    private final PasswordEncoder passwords;
    private final AuthService auth;
    private final SecureRandom random = new SecureRandom();

    public GoogleLoginService(GoogleIdTokenVerifier google, UserRepository users,
                              PasswordEncoder passwords, AuthService auth) {
        this.google = google;
        this.users = users;
        this.passwords = passwords;
        this.auth = auth;
    }

    @Transactional
    public LoginResponse login(String idToken) {
        GoogleIdTokenVerifier.GoogleIdentity identity = google.verify(idToken);
        AppUser user = users.findByGoogleSub(identity.subject())
                .orElseGet(() -> registerGoogleUser(identity));
        if (user.getStatus() != UserStatus.ACTIVE) {
            throw new ApiException(HttpStatus.FORBIDDEN, "ACCOUNT_INACTIVE", "Account is inactive");
        }
        return auth.issueToken(user);
    }

    private AppUser registerGoogleUser(GoogleIdTokenVerifier.GoogleIdentity identity) {
        // Never auto-link an existing OWNER/STAFF/TENANT/CUSTOMER account by email.
        if (users.existsByEmailIgnoreCase(identity.email())) {
            throw new ApiException(HttpStatus.CONFLICT, "ACCOUNT_LINK_REQUIRED",
                    "This email already has an account. Sign in with password and link Google first.");
        }
        byte[] passwordBytes = new byte[32];
        random.nextBytes(passwordBytes);
        // A random, unguessable password hash satisfies the existing NOT NULL column.
        String unusablePassword = Base64.getUrlEncoder().withoutPadding().encodeToString(passwordBytes);
        String username = "google_" + UUID.randomUUID().toString().replace("-", "");
        AppUser user = new AppUser(username, passwords.encode(unusablePassword),
                identity.fullName(), identity.email(), null, Role.CUSTOMER);
        user.setGoogleSub(identity.subject());
        // Unique constraints on email and google_sub protect against duplicate records.
        return users.saveAndFlush(user);
    }

    @Transactional
    public void link(UUID currentUserId, String idToken) {
        GoogleIdTokenVerifier.GoogleIdentity identity = google.verify(idToken);
        AppUser current = users.findById(currentUserId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "Account not found"));
        if (current.getStatus() != UserStatus.ACTIVE) {
            throw new ApiException(HttpStatus.FORBIDDEN, "ACCOUNT_INACTIVE", "Account is inactive");
        }
        if (current.getEmail() == null || !current.getEmail().equalsIgnoreCase(identity.email())) {
            throw new ApiException(HttpStatus.CONFLICT, "GOOGLE_EMAIL_MISMATCH",
                    "Google email must match your existing account email");
        }
        if (current.getGoogleSub() != null && !current.getGoogleSub().equals(identity.subject())) {
            throw new ApiException(HttpStatus.CONFLICT, "GOOGLE_ALREADY_LINKED",
                    "Your account is already linked to another Google account");
        }
        AppUser other = users.findByGoogleSub(identity.subject()).orElse(null);
        if (other != null && !other.getId().equals(current.getId())) {
            throw new ApiException(HttpStatus.CONFLICT, "GOOGLE_ALREADY_LINKED",
                    "This Google account is linked to another user");
        }
        current.setGoogleSub(identity.subject());
        users.saveAndFlush(current);
    }
}
