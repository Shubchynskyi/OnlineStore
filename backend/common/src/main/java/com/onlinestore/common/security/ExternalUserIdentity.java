package com.onlinestore.common.security;

import com.onlinestore.common.exception.BusinessException;
import org.springframework.security.oauth2.jwt.Jwt;

public record ExternalUserIdentity(
    String keycloakId,
    String email,
    String phone,
    String firstName,
    String lastName
) {

    public static ExternalUserIdentity fromJwt(Jwt jwt) {
        return new ExternalUserIdentity(
            requireSubject(jwt),
            jwt.getClaimAsString("email"),
            jwt.getClaimAsString("phone_number"),
            jwt.getClaimAsString("given_name"),
            jwt.getClaimAsString("family_name")
        );
    }

    public static String requireSubject(Jwt jwt) {
        String keycloakId = jwt.getSubject();
        if (keycloakId == null || keycloakId.isBlank()) {
            throw new BusinessException("INVALID_TOKEN", "sub claim is missing");
        }
        return keycloakId;
    }

    public String requiredEmail() {
        if (email == null || email.isBlank()) {
            throw new BusinessException("INVALID_TOKEN", "email claim is missing");
        }
        return email;
    }
}
