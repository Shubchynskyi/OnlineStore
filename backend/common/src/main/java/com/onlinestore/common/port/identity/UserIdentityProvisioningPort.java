package com.onlinestore.common.port.identity;

import com.onlinestore.common.security.AuthenticatedUser;
import com.onlinestore.common.security.ExternalUserIdentity;
import java.util.Optional;

public interface UserIdentityProvisioningPort {

    Optional<AuthenticatedUser> findByKeycloakId(String keycloakId);

    AuthenticatedUser resolveOrProvision(ExternalUserIdentity externalUserIdentity);
}
