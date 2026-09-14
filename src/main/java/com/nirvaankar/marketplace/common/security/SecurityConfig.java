package com.nirvaankar.marketplace.common.security;

import com.nirvaankar.marketplace.common.config.NirvaankarProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

/**
 * Three panels, one backend, three filter chains.
 * <p>
 * The chains are ordered most-specific-first because Spring evaluates them in
 * order and the first match wins. Admin sits ahead of seller, seller ahead of
 * the public customer chain.
 * <p>
 * Note the coarse role check here is a first gate only. The real authorization
 * is per-permission at the method level, and seller data scoping happens in the
 * service layer where the sellerId becomes a WHERE clause - a controller
 * annotation cannot stop a seller from passing another seller's id.
 */
@Configuration
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private static final String[] PUBLIC_PATHS = {
            "/api/v1/auth/**",
            "/api/v1/config/**",
            "/api/v1/payments/webhooks/**",
            "/webhook",
            "/actuator/health/**",
            "/v3/api-docs/**",
            "/swagger-ui/**",
            "/swagger-ui.html"
    };

    private final JwtAuthenticationFilter jwtAuthenticationFilter;

    @Bean
    @Order(1)
    public SecurityFilterChain adminFilterChain(HttpSecurity http) throws Exception {
        return baseChain(http.securityMatcher("/api/v1/admin/**"))
                .authorizeHttpRequests(auth -> auth
                        .anyRequest().hasAnyRole("admin", "support"))
                .build();
    }

    @Bean
    @Order(2)
    public SecurityFilterChain sellerFilterChain(HttpSecurity http) throws Exception {
        return baseChain(http.securityMatcher("/api/v1/seller/**"))
                .authorizeHttpRequests(auth -> auth
                        .anyRequest().hasAnyRole("seller", "admin"))
                .build();
    }

    @Bean
    @Order(3)
    public SecurityFilterChain customerFilterChain(HttpSecurity http) throws Exception {
        return baseChain(http)
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(PUBLIC_PATHS).permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/catalog/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/shopping-intentions/**").permitAll()
                        .anyRequest().authenticated())
                .build();
    }

    private HttpSecurity baseChain(HttpSecurity http) throws Exception {
        return http
                // No cookies, no server-side session: the API is stateless and
                // is consumed by native apps as much as by the browser.
                .csrf(csrf -> csrf.disable())
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .formLogin(form -> form.disable())
                .httpBasic(basic -> basic.disable())
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);
    }

    @Bean
    public PasswordEncoder passwordEncoder(NirvaankarProperties properties) {
        return new BCryptPasswordEncoder(properties.security().password().bcryptStrength());
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        // Tighten this per environment. Native apps send no Origin at all, so
        // this only affects the React web client.
        configuration.setAllowedOriginPatterns(List.of("http://localhost:*", "https://*.nirvaankar.com"));
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("*"));
        configuration.setExposedHeaders(List.of("X-Trace-Id", "ETag"));
        configuration.setAllowCredentials(true);
        configuration.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
