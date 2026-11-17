package com.civictrack.security;

import com.civictrack.repository.UserRepository;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;
import java.util.List;

@Component
@RequiredArgsConstructor
public class JwtAuthFilter extends OncePerRequestFilter {
    private final JwtService jwt;
    private final UserRepository users;

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
            throws ServletException, IOException {
        String header = req.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            try {
                Claims claims = jwt.parse(header.substring(7));
                if (!jwt.isRevoked(claims.getId())) {
                    users.findByEmail(claims.getSubject())
                            // account must still be active and the role in the token must still be the user's real role
                            .filter(u -> u.isActive() && u.getRole().name().equals(claims.get("role", String.class)))
                            .ifPresent(u -> {
                                var auth = new UsernamePasswordAuthenticationToken(u, null,
                                        List.of(new SimpleGrantedAuthority("ROLE_" + u.getRole().name())));
                                SecurityContextHolder.getContext().setAuthentication(auth);
                            });
                }
            } catch (JwtException | IllegalArgumentException ignored) {
                // invalid token -> request stays unauthenticated
            }
        }
        chain.doFilter(req, res);
    }
}
