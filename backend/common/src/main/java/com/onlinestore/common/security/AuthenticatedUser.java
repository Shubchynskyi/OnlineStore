package com.onlinestore.common.security;

import com.onlinestore.common.exception.BusinessException;

public record AuthenticatedUser(Long userId, String keycloakId) {

    public Long requiredUserId() {
        if (userId == null || userId <= 0) {
            throw new BusinessException("INVALID_TOKEN", "Internal user id could not be resolved");
        }
        return userId;
    }
}
