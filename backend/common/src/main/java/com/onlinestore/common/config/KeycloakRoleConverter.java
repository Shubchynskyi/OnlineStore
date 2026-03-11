package com.onlinestore.common.config;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

public class KeycloakRoleConverter implements Converter<Jwt, Collection<GrantedAuthority>> {

    @Override
    public Collection<GrantedAuthority> convert(Jwt jwt) {
        Map<String, Object> realmAccess = jwt.getClaimAsMap("realm_access");
        if (realmAccess == null || !realmAccess.containsKey("roles")) {
            return List.of();
        }
        Object rawRoles = realmAccess.get("roles");
        if (!(rawRoles instanceof List<?> roles)) {
            return Collections.emptyList();
        }
        return roles.stream()
            .filter(String.class::isInstance)
            .map(String.class::cast)
            .map(role -> role.startsWith("ROLE_") ? role : "ROLE_" + role.toUpperCase(Locale.ROOT))
            .map(SimpleGrantedAuthority::new)
            .collect(Collectors.toList());
    }
}
