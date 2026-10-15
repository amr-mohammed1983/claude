package dev.filenetmcp;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

/**
 * HTTP transport only: every request needs HTTP Basic credentials (spring.security.user.*).
 * CSRF is disabled because MCP clients are not browsers; use TLS in front of this endpoint.
 */
@Configuration(proxyBeanMethods = false)
@Profile("http")
class HttpSecurityConfiguration {
    @Bean
    SecurityFilterChain mcpSecurity(HttpSecurity http) throws Exception {
        return http.csrf(csrf -> csrf.disable())
                .authorizeHttpRequests(a -> a.anyRequest().authenticated())
                .httpBasic(Customizer.withDefaults())
                .build();
    }
}
