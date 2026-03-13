package com.onlinestore.common.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

class CorsConfigTest {

    @Test
    void corsConfigurationSourceShouldUseExplicitAllowLists() {
        var corsConfig = new CorsConfig(
            List.of(" http://localhost:3000 ", "http://127.0.0.1:4200"),
            List.of(" GET ", "POST", "GET"),
            List.of(" Authorization ", "Content-Type", " Authorization ")
        );

        var request = new MockHttpServletRequest("OPTIONS", "/api/v1/orders");
        var configuration = corsConfig.corsConfigurationSource().getCorsConfiguration(request);

        assertEquals(List.of("http://localhost:3000", "http://127.0.0.1:4200"), configuration.getAllowedOrigins());
        assertEquals(List.of("GET", "POST"), configuration.getAllowedMethods());
        assertEquals(List.of("Authorization", "Content-Type"), configuration.getAllowedHeaders());
        assertTrue(Boolean.TRUE.equals(configuration.getAllowCredentials()));
    }

    @Test
    void constructorShouldRejectWildcardHeaders() {
        var exception = assertThrows(IllegalArgumentException.class, () -> new CorsConfig(
            List.of("http://localhost:3000"),
            List.of("GET"),
            List.of("*")
        ));

        assertEquals(
            "CORS allowed headers must be explicit and must not contain wildcard entries",
            exception.getMessage()
        );
    }
}
