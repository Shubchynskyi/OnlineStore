package com.onlinestore.users.dto;

import com.onlinestore.users.entity.UserStatus;

public record UserDTO(
    Long id,
    String email,
    String phone,
    String firstName,
    String lastName,
    String avatarUrl,
    UserStatus status
) {
}
