package com.onlinestore.common.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;

class KeycloakRoleConverterTest {

    private final KeycloakRoleConverter converter = new KeycloakRoleConverter();

    @Test
    void convertShouldNormalizeRealmRolesToAuthorities() {
        var jwt = jwt(Map.of("realm_access", Map.of("roles", List.of("admin", "ROLE_MANAGER", 123))));

        var authorities = converter.convert(jwt);

        assertThat(authorities)
            .extracting(authority -> authority.getAuthority())
            .containsExactlyInAnyOrder("ROLE_ADMIN", "ROLE_MANAGER");
    }

    @Test
    void convertShouldReturnEmptyAuthoritiesWhenRolesClaimIsMissing() {
        var jwt = jwt(Map.of());

        assertThat(converter.convert(jwt)).isEmpty();
    }

    private Jwt jwt(Map<String, Object> claims) {
        return Jwt.withTokenValue("token")
            .header("alg", "none")
            .issuedAt(Instant.now())
            .expiresAt(Instant.now().plusSeconds(300))
            .claim("sub", "user-1")
            .claims(existing -> existing.putAll(claims))
            .build();
    }
}
