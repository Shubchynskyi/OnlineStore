package com.onlinestore.users.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import com.onlinestore.users.dto.CreateAddressRequest;
import com.onlinestore.users.dto.UpdateProfileRequest;
import com.onlinestore.users.entity.User;
import com.onlinestore.users.entity.UserProfile;
import com.onlinestore.users.entity.UserStatus;
import org.junit.jupiter.api.Test;

class UserMapperTest {

    private final UserMapper userMapper = new UserMapper();

    @Test
    void toDtoShouldExposeProfileFields() {
        var profile = new UserProfile();
        profile.setFirstName("Ada");
        profile.setLastName("Lovelace");
        profile.setAvatarUrl("https://cdn.example.test/avatar.png");

        var user = new User();
        user.setId(7L);
        user.setEmail("ada@example.test");
        user.setPhone("+380991112233");
        user.setStatus(UserStatus.ACTIVE);
        user.setProfile(profile);

        var dto = userMapper.toDto(user);

        assertThat(dto.id()).isEqualTo(7L);
        assertThat(dto.firstName()).isEqualTo("Ada");
        assertThat(dto.lastName()).isEqualTo("Lovelace");
        assertThat(dto.avatarUrl()).isEqualTo("https://cdn.example.test/avatar.png");
        assertThat(dto.status()).isEqualTo(UserStatus.ACTIVE);
    }

    @Test
    void updateProfileShouldCreateProfileWhenMissing() {
        var user = new User();
        var request = new UpdateProfileRequest("Grace", "Hopper", "+380971234567");

        userMapper.updateProfile(user, request);

        assertThat(user.getPhone()).isEqualTo("+380971234567");
        assertThat(user.getProfile()).isNotNull();
        assertThat(user.getProfile().getUser()).isSameAs(user);
        assertThat(user.getProfile().getFirstName()).isEqualTo("Grace");
        assertThat(user.getProfile().getLastName()).isEqualTo("Hopper");
    }

    @Test
    void addressMappingShouldPreserveRequestFields() {
        var request = new CreateAddressRequest(
            "Home",
            "UKR",
            "Kyiv",
            "Khreshchatyk 1",
            "1A",
            "10",
            "01001",
            true
        );

        var address = userMapper.toAddress(request);
        address.setId(15L);
        var dto = userMapper.toAddressDto(address);

        assertThat(dto.id()).isEqualTo(15L);
        assertThat(dto.label()).isEqualTo("Home");
        assertThat(dto.country()).isEqualTo("UKR");
        assertThat(dto.city()).isEqualTo("Kyiv");
        assertThat(dto.street()).isEqualTo("Khreshchatyk 1");
        assertThat(dto.postalCode()).isEqualTo("01001");
        assertThat(dto.isDefault()).isTrue();
    }
}
