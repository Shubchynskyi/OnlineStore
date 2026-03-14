package com.onlinestore.common.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;

class ExternalUserIdentityTest {

    @Test
    void fromJwtShouldExtractStandardClaims() {
        var jwt = jwt(Map.of(
            "sub", "kc-1",
            "email", "ada@example.test",
            "phone_number", "+380991112233",
            "given_name", "Ada",
            "family_name", "Lovelace"
        ));

        var identity = ExternalUserIdentity.fromJwt(jwt);

        assertThat(identity.keycloakId()).isEqualTo("kc-1");
        assertThat(identity.email()).isEqualTo("ada@example.test");
        assertThat(identity.phone()).isEqualTo("+380991112233");
        assertThat(identity.firstName()).isEqualTo("Ada");
        assertThat(identity.lastName()).isEqualTo("Lovelace");
        assertThat(identity.requiredEmail()).isEqualTo("ada@example.test");
    }

    @Test
    void requiredEmailShouldRejectBlankClaim() {
        var identity = new ExternalUserIdentity("kc-1", " ", null, null, null);

        assertThatThrownBy(identity::requiredEmail)
            .isInstanceOf(com.onlinestore.common.exception.BusinessException.class)
            .hasMessageContaining("email claim is missing");
    }

    @Test
    void requireSubjectShouldRejectMissingClaim() {
        var jwt = jwt(Map.of("email", "ada@example.test"));

        assertThatThrownBy(() -> ExternalUserIdentity.requireSubject(jwt))
            .isInstanceOf(com.onlinestore.common.exception.BusinessException.class)
            .hasMessageContaining("sub claim is missing");
    }

    private Jwt jwt(Map<String, Object> claims) {
        return new Jwt("token", Instant.now(), Instant.now().plusSeconds(60), Map.of("alg", "none"), claims);
    }
}
