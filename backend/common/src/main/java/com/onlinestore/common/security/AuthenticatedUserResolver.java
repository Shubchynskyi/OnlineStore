package com.onlinestore.common.security;

import com.onlinestore.common.exception.BusinessException;
import com.onlinestore.common.port.identity.UserIdentityProvisioningPort;
import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AuthenticatedUserResolver {

    private final UserIdentityProvisioningPort userIdentityProvisioningPort;

    public AuthenticatedUser resolve(Jwt jwt) {
        String keycloakId = ExternalUserIdentity.requireSubject(jwt);
        Long userId = extractUserId(jwt);
        if (userId != null) {
            return resolveExistingUser(keycloakId, userId);
        }
        return userIdentityProvisioningPort.resolveOrProvision(ExternalUserIdentity.fromJwt(jwt));
    }

    private Long extractUserId(Jwt jwt) {
        Object claimValue = jwt.getClaim("user_id");
        if (claimValue == null) {
            return null;
        }
        if (claimValue instanceof Number numberValue) {
            return validateUserId(numberValue.longValue());
        }
        if (claimValue instanceof String stringValue) {
            if (stringValue.isBlank()) {
                throw invalidUserIdClaim();
            }
            try {
                return validateUserId(Long.parseLong(stringValue));
            } catch (NumberFormatException ex) {
                throw invalidUserIdClaim();
            }
        }
        throw invalidUserIdClaim();
    }

    private Long validateUserId(long userId) {
        if (userId <= 0) {
            throw invalidUserIdClaim();
        }
        return userId;
    }

    private void validateUserIdClaim(AuthenticatedUser authenticatedUser, Long claimedUserId) {
        if (!authenticatedUser.requiredUserId().equals(claimedUserId)) {
            throw new BusinessException("INVALID_TOKEN", "user_id claim does not match subject mapping");
        }
    }

    private AuthenticatedUser resolveExistingUser(String keycloakId, Long claimedUserId) {
        var authenticatedUser = userIdentityProvisioningPort.findByKeycloakId(keycloakId)
            .orElseThrow(() -> new BusinessException("INVALID_TOKEN", "user_id claim does not match subject mapping"));
        validateUserIdClaim(authenticatedUser, claimedUserId);
        return authenticatedUser;
    }

    private BusinessException invalidUserIdClaim() {
        return new BusinessException("INVALID_TOKEN", "user_id claim has invalid format");
    }
}
