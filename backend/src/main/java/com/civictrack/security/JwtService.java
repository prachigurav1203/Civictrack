package com.civictrack.security;

import com.civictrack.model.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import javax.crypto.SecretKey;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Base64;
import java.security.SecureRandom;
import java.util.Date;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Issues and validates JWTs. Each token has a unique id (jti) so it can be revoked on logout.
 * If JWT_SECRET is not configured, a random secret is generated at start-up (tokens then do not survive a restart).
 */
@Slf4j
@Service
public class JwtService {
    private final SecretKey key;
    private final long expirationMs;
    /** jti -> expiry (epoch ms). In-memory: revocations are cleared when the server restarts. */
    private final Map<String, Long> revoked = new ConcurrentHashMap<>();

    public JwtService(@Value("${app.jwt.secret:}") String secret, @Value("${app.jwt.expiration-ms:28800000}") long expirationMs,
                      @Value("${app.jwt.secret-file:data/jwt.secret}") String secretFile) {
        byte[] bytes;
        if (secret == null || secret.getBytes(StandardCharsets.UTF_8).length < 32) {
            bytes = loadOrCreate(Paths.get(secretFile));
        } else {
            bytes = secret.getBytes(StandardCharsets.UTF_8);
        }
        this.key = Keys.hmacShaKeyFor(bytes);
        this.expirationMs = expirationMs;
    }

    /** No JWT_SECRET configured: generate a random secret once and keep it in a local file (never in source code). */
    private static byte[] loadOrCreate(Path file) {
        try {
            if (Files.exists(file)) {
                byte[] existing = Files.readString(file).trim().getBytes(StandardCharsets.UTF_8);
                if (existing.length >= 32) return existing;
            }
            byte[] raw = new byte[48];
            new SecureRandom().nextBytes(raw);
            String encoded = Base64.getEncoder().encodeToString(raw);
            if (file.getParent() != null) Files.createDirectories(file.getParent());
            Files.writeString(file, encoded);
            log.info("Generated a new JWT signing secret at {} (keep this file private).", file.toAbsolutePath());
            return encoded.getBytes(StandardCharsets.UTF_8);
        } catch (IOException e) {
            byte[] raw = new byte[64];
            new SecureRandom().nextBytes(raw);
            log.warn("Could not persist the JWT secret ({}). Using a temporary one; users must log in again after a restart.", e.toString());
            return raw;
        }
    }

    public String generate(User u) {
        long now = System.currentTimeMillis();
        return Jwts.builder().id(UUID.randomUUID().toString()).subject(u.getEmail()).claim("role", u.getRole().name())
                .issuedAt(new Date(now)).expiration(new Date(now + expirationMs)).signWith(key).compact();
    }

    /** Throws JwtException / IllegalArgumentException when the token is invalid, tampered with or expired. */
    public Claims parse(String token) {
        return Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
    }

    public boolean isRevoked(String jti) { return jti != null && revoked.containsKey(jti); }

    public void revoke(Claims claims) {
        long now = System.currentTimeMillis();
        revoked.values().removeIf(exp -> exp < now);
        if (claims.getId() != null) revoked.put(claims.getId(), claims.getExpiration().getTime());
    }
}
