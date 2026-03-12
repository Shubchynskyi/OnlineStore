package com.onlinestore.users.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.onlinestore.common.security.ExternalUserIdentity;
import com.onlinestore.users.entity.User;
import com.onlinestore.users.mapper.UserMapper;
import com.onlinestore.users.repository.UserRepository;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private UserMapper userMapper;

    private UserService userService;

    @BeforeEach
    void setUp() {
        userService = new UserService(userRepository, userMapper);
    }

    @Test
    void resolveOrProvisionShouldReuseExistingUser() {
        var user = new User();
        user.setId(7L);
        user.setKeycloakId("keycloak-user");
        when(userRepository.findByKeycloakId("keycloak-user")).thenReturn(Optional.of(user));

        var authenticatedUser = userService.resolveOrProvision(identity());

        assertEquals(7L, authenticatedUser.requiredUserId());
        assertEquals("keycloak-user", authenticatedUser.keycloakId());
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void resolveOrProvisionShouldCreateMissingUserFromExternalIdentity() {
        when(userRepository.findByKeycloakId("keycloak-user")).thenReturn(Optional.empty());
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
            var user = invocation.getArgument(0, User.class);
            user.setId(11L);
            return user;
        });

        var authenticatedUser = userService.resolveOrProvision(identity());

        assertEquals(11L, authenticatedUser.requiredUserId());
        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCaptor.capture());
        assertEquals("keycloak-user", userCaptor.getValue().getKeycloakId());
        assertEquals("user@example.com", userCaptor.getValue().getEmail());
        assertEquals("+4912345", userCaptor.getValue().getPhone());
        assertEquals("Jane", userCaptor.getValue().getProfile().getFirstName());
        assertEquals("Doe", userCaptor.getValue().getProfile().getLastName());
    }

    private ExternalUserIdentity identity() {
        return new ExternalUserIdentity("keycloak-user", "user@example.com", "+4912345", "Jane", "Doe");
    }
}
