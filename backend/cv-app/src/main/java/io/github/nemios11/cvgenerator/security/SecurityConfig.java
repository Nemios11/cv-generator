package io.github.nemios11.cvgenerator.security;

import static org.springframework.security.config.Customizer.withDefaults;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.Environment;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter.ReferrerPolicy;

/**
 * The API is a stateless OAuth2 resource server: every request under {@code /api}
 * must carry a valid access token issued by the configured identity provider.
 * No sessions and no cookies, hence no CSRF exposure.
 */
@Configuration(proxyBeanMethods = false)
public class SecurityConfig {

    /**
     * Management port (actuator), when it is separate from the application port
     * ({@code management.server.port}, set in deployed environments). That port is
     * reachable only inside the environment's network, never published: the
     * deploy tooling reads health and metrics there without a token. On the
     * application port actuator endpoints other than health stay denied.
     * Without a separate port ({@code local.management.port} unset) this chain
     * matches nothing.
     */
    @Bean
    @Order(0)
    SecurityFilterChain managementPortSecurity(HttpSecurity http, Environment environment) throws Exception {
        RequestMatcher onManagementPort = request -> {
            Integer port = environment.getProperty("local.management.port", Integer.class);
            return port != null && port > 0 && request.getLocalPort() == port;
        };
        http
                .securityMatcher(onManagementPort)
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
                .requestCache(cache -> cache.disable())
                .securityContext(context -> context.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .csrf(csrf -> csrf.disable());
        return http.build();
    }

    /**
     * API documentation (OpenAPI JSON and Swagger UI). Public so that Swagger UI can
     * load; it only describes endpoints, which still require a token to be called.
     * Disable in production with {@code springdoc.api-docs.enabled=false}
     * and {@code springdoc.swagger-ui.enabled=false}.
     */
    @Bean
    @Order(1)
    SecurityFilterChain apiDocsSecurity(HttpSecurity http) throws Exception {
        http
                .securityMatcher("/v3/api-docs", "/v3/api-docs/**", "/swagger-ui.html", "/swagger-ui/**")
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
                .requestCache(cache -> cache.disable())
                .securityContext(context -> context.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .csrf(csrf -> csrf.disable());
        return http.build();
    }

    @Bean
    @Order(2)
    SecurityFilterChain apiSecurity(HttpSecurity http) throws Exception {
        http
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/actuator/health", "/actuator/health/**").permitAll()
                        .requestMatchers("/api/**").authenticated()
                        .anyRequest().denyAll())
                .oauth2ResourceServer(oauth2 -> oauth2.jwt(withDefaults()))
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .csrf(csrf -> csrf.disable())
                .headers(headers -> headers
                        .contentSecurityPolicy(csp -> csp.policyDirectives("default-src 'none'; frame-ancestors 'none'"))
                        .referrerPolicy(referrer -> referrer.policy(ReferrerPolicy.NO_REFERRER)));
        return http.build();
    }
}
