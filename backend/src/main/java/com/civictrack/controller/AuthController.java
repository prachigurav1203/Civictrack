package com.civictrack.controller;

import com.civictrack.dto.Dtos.*;
import com.civictrack.exception.ApiException;
import com.civictrack.model.*;
import com.civictrack.repository.UserRepository;
import com.civictrack.security.JwtService;
import com.civictrack.security.LoginAttemptService;
import io.jsonwebtoken.JwtException;
import jakarta.annotation.PostConstruct;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {
    private final UserRepository users;
    private final PasswordEncoder encoder;
    private final JwtService jwt;
    private final LoginAttemptService attempts;

    /** Compared against when the email is unknown, so response time does not reveal whether an email exists. */
    private String dummyHash;

    @PostConstruct
    void init() { dummyHash = encoder.encode(UUID.randomUUID().toString()); }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public AuthResponse register(@Valid @RequestBody RegisterRequest r) {
        String email = r.email().trim().toLowerCase();
        if (users.existsByEmail(email)) throw ApiException.conflict("Email is already registered");
        User u = new User();
        u.setName(r.name().trim()); u.setEmail(email);
        u.setPhone(r.phone() == null || r.phone().isBlank() ? null : r.phone().trim());
        u.setPassword(encoder.encode(r.password())); u.setRole(Role.CITIZEN);
        users.save(u);
        return new AuthResponse(jwt.generate(u), UserResponse.from(u));
    }

    @PostMapping("/login")
    public AuthResponse login(@Valid @RequestBody LoginRequest r, HttpServletRequest req) {
        String email = r.email().trim().toLowerCase();
        String ip = req.getRemoteAddr();
        attempts.assertAllowed(email, ip);

        User found = users.findByEmail(email).orElse(null);
        boolean passwordOk = encoder.matches(r.password(), found != null ? found.getPassword() : dummyHash);
        if (found == null || !passwordOk) {
            attempts.failed(email, ip);
            // same message for "unknown email" and "wrong password" (no account enumeration)
            throw ApiException.unauthorized("Invalid email or password");
        }
        if (!found.isActive()) throw ApiException.forbidden("This account has been deactivated. Contact the administrator.");
        attempts.succeeded(email, ip);
        return new AuthResponse(jwt.generate(found), UserResponse.from(found));
    }

    /** Revokes the presented token so it cannot be used again. */
    @PostMapping("/logout")
    public Map<String, String> logout(HttpServletRequest req) {
        String header = req.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            try { jwt.revoke(jwt.parse(header.substring(7))); } catch (JwtException | IllegalArgumentException ignored) { }
        }
        return Map.of("message", "Logged out");
    }

    @GetMapping("/me")
    public UserResponse me(@AuthenticationPrincipal User user) { return UserResponse.from(user); }
}
