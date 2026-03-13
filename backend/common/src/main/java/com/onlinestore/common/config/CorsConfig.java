package com.onlinestore.common.config;

import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Configuration
public class CorsConfig {

    private static final String DEFAULT_ALLOWED_ORIGINS =
        "http://localhost:3000,http://localhost:4200,http://127.0.0.1:3000,http://127.0.0.1:4200";
    private static final String DEFAULT_ALLOWED_METHODS = "GET,POST,PUT,PATCH,DELETE,OPTIONS";
    private static final String DEFAULT_ALLOWED_HEADERS =
        "Authorization,Content-Type,Accept,Accept-Language,Cache-Control,Pragma,X-Requested-With";

    private final List<String> allowedOrigins;
    private final List<String> allowedMethods;
    private final List<String> allowedHeaders;

    public CorsConfig(
        @Value("${onlinestore.security.cors.allowed-origins:" + DEFAULT_ALLOWED_ORIGINS + "}") List<String> allowedOrigins,
        @Value("${onlinestore.security.cors.allowed-methods:" + DEFAULT_ALLOWED_METHODS + "}") List<String> allowedMethods,
        @Value("${onlinestore.security.cors.allowed-headers:" + DEFAULT_ALLOWED_HEADERS + "}") List<String> allowedHeaders
    ) {
        this.allowedOrigins = normalizeConfiguredValues(allowedOrigins);
        this.allowedMethods = normalizeConfiguredValues(allowedMethods);
        this.allowedHeaders = normalizeConfiguredValues(allowedHeaders);
        validateExplicitAllowList("origins", this.allowedOrigins);
        validateExplicitAllowList("methods", this.allowedMethods);
        validateExplicitAllowList("headers", this.allowedHeaders);
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        var configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(allowedOrigins);
        configuration.setAllowedMethods(allowedMethods);
        configuration.setAllowedHeaders(allowedHeaders);
        configuration.setAllowCredentials(true);

        var source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    private static List<String> normalizeConfiguredValues(List<String> values) {
        return values.stream()
            .map(String::trim)
            .filter(value -> !value.isBlank())
            .distinct()
            .toList();
    }

    private static void validateExplicitAllowList(String settingName, List<String> values) {
        if (values.stream().anyMatch(value -> value.contains("*"))) {
            throw new IllegalArgumentException(
                "CORS allowed " + settingName + " must be explicit and must not contain wildcard entries"
            );
        }
    }
}
