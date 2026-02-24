package com.onlinestore.apigateway.security;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Stream;

import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Mono;

@Component
public class CustomJwtAuthenticationConverter implements Converter<Jwt, Mono<AbstractAuthenticationToken>> {

    private static final String REALM_ACCESS_CLAIM = "realm_access";
    private static final String RESOURCE_ACCESS_CLAIM = "resource_access";
    private static final String ROLES_CLAIM = "roles";
    private static final String ROLE_PREFIX = "ROLE_";

    private final JwtGrantedAuthoritiesConverter defaultAuthoritiesConverter = new JwtGrantedAuthoritiesConverter();

    @Override
    public Mono<AbstractAuthenticationToken> convert(Jwt jwt) {
        Set<GrantedAuthority> authorities = new LinkedHashSet<>();
        authorities.addAll(defaultAuthoritiesConverter.convert(jwt));
        authorities.addAll(extractRealmRoles(jwt));
        authorities.addAll(extractResourceRoles(jwt));

        String principalName = resolvePrincipalName(jwt);
        return Mono.just(new JwtAuthenticationToken(jwt, authorities, principalName));
    }

    private Collection<GrantedAuthority> extractRealmRoles(Jwt jwt) {
        Map<String, Object> realmAccess = jwt.getClaim(REALM_ACCESS_CLAIM);
        return extractRoles(realmAccess);
    }

    private Collection<GrantedAuthority> extractResourceRoles(Jwt jwt) {
        Map<String, Object> resourceAccess = jwt.getClaim(RESOURCE_ACCESS_CLAIM);
        if (resourceAccess == null || resourceAccess.isEmpty()) {
            return Set.of();
        }

        Collection<GrantedAuthority> authorities = new LinkedHashSet<>();
        for (Object clientClaimsObject : resourceAccess.values()) {
            if (clientClaimsObject instanceof Map<?, ?> clientClaims) {
                authorities.addAll(extractRoles(toStringObjectMap(clientClaims)));
            }
        }
        return authorities;
    }

    private Collection<GrantedAuthority> extractRoles(Map<String, Object> claims) {
        if (claims == null || claims.isEmpty()) {
            return Set.of();
        }

        Object rolesObject = claims.get(ROLES_CLAIM);
        if (!(rolesObject instanceof Collection<?> roles)) {
            return Set.of();
        }

        return roles.stream()
            .filter(String.class::isInstance)
            .map(String.class::cast)
            .map(String::trim)
            .filter(StringUtils::hasText)
            .map(this::normalizeRole)
            .map(SimpleGrantedAuthority::new)
            .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
    }

    private String resolvePrincipalName(Jwt jwt) {
        return Stream.of(
                jwt.getClaimAsString("preferred_username"),
                jwt.getClaimAsString("email"),
                jwt.getSubject()
            )
            .filter(Objects::nonNull)
            .map(String::trim)
            .filter(StringUtils::hasText)
            .findFirst()
            .orElse("unknown");
    }

    private String normalizeRole(String role) {
        return role.startsWith(ROLE_PREFIX) ? role : ROLE_PREFIX + role;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> toStringObjectMap(Map<?, ?> source) {
        return (Map<String, Object>) source;
    }
}
