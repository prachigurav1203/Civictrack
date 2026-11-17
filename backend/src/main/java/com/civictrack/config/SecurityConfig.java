package com.civictrack.config;

import com.civictrack.security.JwtAuthFilter;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import java.util.Arrays;
import java.util.List;

/**
 * Server-side authorization for every endpoint. Role rules are enforced here AND re-checked in the services
 * (ownership: citizens see only their complaints, officers only complaints assigned to them).
 */
@Configuration
@RequiredArgsConstructor
public class SecurityConfig {
    private final JwtAuthFilter jwtFilter;
    @Value("${app.cors-origins}") private String corsOrigins;

    @Bean
    public PasswordEncoder passwordEncoder() { return new BCryptPasswordEncoder(); }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http.csrf(c -> c.disable())   // stateless JWT in the Authorization header, no cookies -> no CSRF surface
            .cors(c -> c.configurationSource(corsSource()))
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(a -> a
                .requestMatchers("/error", "/uploads/**").permitAll()
                .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/auth/login", "/api/auth/register").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/complaints/track/**").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/departments", "/api/departments/**").permitAll()
                .requestMatchers("/api/departments/**").hasRole("ADMIN")
                .requestMatchers("/api/admin/**", "/api/analytics/**").hasRole("ADMIN")
                // citizen
                .requestMatchers(HttpMethod.POST, "/api/complaints").hasRole("CITIZEN")
                .requestMatchers(HttpMethod.POST, "/api/complaints/*/feedback").hasRole("CITIZEN")
                // admin
                .requestMatchers(HttpMethod.POST, "/api/complaints/*/assign", "/api/complaints/*/review",
                        "/api/complaints/*/reject", "/api/complaints/*/reopen", "/api/complaints/*/priority",
                        "/api/complaints/*/verify").hasRole("ADMIN")
                .requestMatchers(HttpMethod.GET, "/api/complaints/*/duplicates").hasRole("ADMIN")
                // officer
                .requestMatchers(HttpMethod.POST, "/api/complaints/*/accept", "/api/complaints/*/start",
                        "/api/complaints/*/progress", "/api/complaints/*/resolution").hasRole("OFFICER")
                .requestMatchers("/api/**").authenticated()
                // everything else is the bundled React app (static files) - contains no data
                .anyRequest().permitAll())
            .exceptionHandling(e -> e
                .authenticationEntryPoint((req, res, ex) -> json(res, HttpServletResponse.SC_UNAUTHORIZED, "Authentication required"))
                .accessDeniedHandler((req, res, ex) -> json(res, HttpServletResponse.SC_FORBIDDEN, "You do not have permission to perform this action")))
            .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    private static void json(HttpServletResponse res, int status, String message) throws java.io.IOException {
        res.setStatus(status);
        res.setContentType("application/json");
        res.setCharacterEncoding("UTF-8");
        res.getWriter().write("{\"status\":" + status + ",\"message\":\"" + message + "\"}");
    }

    @Bean
    public CorsConfigurationSource corsSource() {
        CorsConfiguration c = new CorsConfiguration();
        c.setAllowedOrigins(Arrays.stream(corsOrigins.split(",")).map(String::trim).toList());
        c.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        c.setAllowedHeaders(List.of("Authorization", "Content-Type", "Accept"));
        c.setExposedHeaders(List.of("Content-Disposition"));
        UrlBasedCorsConfigurationSource src = new UrlBasedCorsConfigurationSource();
        src.registerCorsConfiguration("/**", c);
        return src;
    }
}
