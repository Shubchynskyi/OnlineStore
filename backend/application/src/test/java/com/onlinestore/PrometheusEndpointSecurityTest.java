package com.onlinestore;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PrometheusEndpointSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void prometheusEndpointShouldRequireAuthentication() throws Exception {
        mockMvc.perform(get("/actuator/prometheus"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void prometheusEndpointShouldBeAvailableToAdminRole() throws Exception {
        mockMvc.perform(get("/actuator/prometheus")
                .header("Authorization", "Bearer admin-token"))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("# HELP")));
    }

    @Test
    void prometheusEndpointShouldBeAvailableToOpsRole() throws Exception {
        mockMvc.perform(get("/actuator/prometheus")
                .header("Authorization", "Bearer ops-token"))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("# HELP")));
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class JwtDecoderTestConfig {

        @Bean
        @Primary
        JwtDecoder jwtDecoder() {
            return token -> switch (token) {
                case "admin-token" -> jwt(token, List.of("ADMIN"));
                case "ops-token" -> jwt(token, List.of("OPS"));
                default -> throw new JwtException("Unsupported test token");
            };
        }

        private Jwt jwt(String token, List<String> roles) {
            return Jwt.withTokenValue(token)
                .header("alg", "none")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(300))
                .claim("sub", "user-1")
                .claim("realm_access", Map.of("roles", roles))
                .build();
        }
    }
}
