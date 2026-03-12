package com.onlinestore.common.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.onlinestore.common.exception.BusinessException;
import com.onlinestore.common.port.identity.UserIdentityProvisioningPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.oauth2.jwt.Jwt;

@ExtendWith(MockitoExtension.class)
class AuthenticatedUserResolverTest {

    @Mock
    private UserIdentityProvisioningPort userIdentityProvisioningPort;

    private AuthenticatedUserResolver authenticatedUserResolver;

    @BeforeEach
    void setUp() {
        authenticatedUserResolver = new AuthenticatedUserResolver(userIdentityProvisioningPort);
    }

    @Test
    void resolveShouldAcceptMatchingUserIdClaim() {
        var jwt = jwtBuilder()
            .claim("user_id", "42")
            .build();
        when(userIdentityProvisioningPort.findByKeycloakId("keycloak-user"))
            .thenReturn(java.util.Optional.of(new AuthenticatedUser(42L, "keycloak-user")));

        var authenticatedUser = authenticatedUserResolver.resolve(jwt);

        assertEquals(42L, authenticatedUser.requiredUserId());
        assertEquals("keycloak-user", authenticatedUser.keycloakId());
        verify(userIdentityProvisioningPort).findByKeycloakId("keycloak-user");
    }

    @Test
    void resolveShouldProvisionUserWhenUserIdClaimIsMissing() {
        var jwt = jwtBuilder().build();
        var provisionedUser = new AuthenticatedUser(64L, "keycloak-user");
        when(userIdentityProvisioningPort.resolveOrProvision(any(ExternalUserIdentity.class))).thenReturn(provisionedUser);

        var authenticatedUser = authenticatedUserResolver.resolve(jwt);

        assertEquals(64L, authenticatedUser.requiredUserId());
        ArgumentCaptor<ExternalUserIdentity> identityCaptor = ArgumentCaptor.forClass(ExternalUserIdentity.class);
        verify(userIdentityProvisioningPort).resolveOrProvision(identityCaptor.capture());
        assertEquals(
            new ExternalUserIdentity("keycloak-user", "user@example.com", "+4912345", "Jane", "Doe"),
            identityCaptor.getValue()
        );
    }

    @Test
    void resolveShouldRejectMismatchedUserIdClaim() {
        var jwt = jwtBuilder()
            .claim("user_id", "42")
            .build();
        when(userIdentityProvisioningPort.findByKeycloakId("keycloak-user"))
            .thenReturn(java.util.Optional.of(new AuthenticatedUser(64L, "keycloak-user")));

        var exception = assertThrows(BusinessException.class, () -> authenticatedUserResolver.resolve(jwt));

        assertEquals("INVALID_TOKEN", exception.getErrorCode());
        assertEquals("user_id claim does not match subject mapping", exception.getMessage());
    }

    @Test
    void resolveShouldRejectUserIdClaimWithoutExistingSubjectMapping() {
        var jwt = jwtBuilder()
            .claim("user_id", "42")
            .build();
        when(userIdentityProvisioningPort.findByKeycloakId("keycloak-user"))
            .thenReturn(java.util.Optional.empty());

        var exception = assertThrows(BusinessException.class, () -> authenticatedUserResolver.resolve(jwt));

        assertEquals("INVALID_TOKEN", exception.getErrorCode());
        assertEquals("user_id claim does not match subject mapping", exception.getMessage());
    }

    @Test
    void resolveShouldRejectInvalidUserIdClaimFormat() {
        var jwt = jwtBuilder()
            .claim("user_id", "abc")
            .build();

        var exception = assertThrows(BusinessException.class, () -> authenticatedUserResolver.resolve(jwt));

        assertEquals("INVALID_TOKEN", exception.getErrorCode());
        assertEquals("user_id claim has invalid format", exception.getMessage());
    }

    private Jwt.Builder jwtBuilder() {
        return Jwt.withTokenValue("token")
            .header("alg", "none")
            .claim("sub", "keycloak-user")
            .claim("email", "user@example.com")
            .claim("phone_number", "+4912345")
            .claim("given_name", "Jane")
            .claim("family_name", "Doe");
    }
}
