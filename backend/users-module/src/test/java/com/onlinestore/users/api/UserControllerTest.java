package com.onlinestore.users.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.onlinestore.common.security.AuthenticatedUser;
import com.onlinestore.common.security.AuthenticatedUserResolver;
import com.onlinestore.users.dto.AddressDTO;
import com.onlinestore.users.dto.CreateAddressRequest;
import com.onlinestore.users.dto.UpdateProfileRequest;
import com.onlinestore.users.dto.UserDTO;
import com.onlinestore.users.service.UserService;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.oauth2.jwt.Jwt;

@ExtendWith(MockitoExtension.class)
class UserControllerTest {

    @Mock
    private AuthenticatedUserResolver authenticatedUserResolver;

    @Mock
    private UserService userService;

    private Jwt jwt;
    private UserController userController;

    @BeforeEach
    void setUp() {
        jwt = Jwt.withTokenValue("token")
            .header("alg", "none")
            .claim("sub", "kc-1")
            .build();
        when(authenticatedUserResolver.resolve(jwt)).thenReturn(new AuthenticatedUser(42L, "kc-1"));
        userController = new UserController(authenticatedUserResolver, userService);
    }

    @Test
    void getCurrentUserShouldDelegateToUserService() {
        var response = mock(UserDTO.class);
        when(userService.getCurrentUser(42L)).thenReturn(response);

        assertThat(userController.getCurrentUser(jwt)).isSameAs(response);

        verify(userService).getCurrentUser(42L);
    }

    @Test
    void updateProfileShouldDelegateToUserService() {
        var request = mock(UpdateProfileRequest.class);
        var response = mock(UserDTO.class);
        when(userService.updateProfile(42L, request)).thenReturn(response);

        assertThat(userController.updateProfile(jwt, request)).isSameAs(response);

        verify(userService).updateProfile(42L, request);
    }

    @Test
    void getAddressesShouldDelegateToUserService() {
        var response = List.of(mock(AddressDTO.class));
        when(userService.getAddresses(42L)).thenReturn(response);

        assertThat(userController.getAddresses(jwt)).isSameAs(response);

        verify(userService).getAddresses(42L);
    }

    @Test
    void addAddressShouldDelegateToUserService() {
        var request = mock(CreateAddressRequest.class);
        var response = mock(AddressDTO.class);
        when(userService.addAddress(42L, request)).thenReturn(response);

        assertThat(userController.addAddress(jwt, request)).isSameAs(response);

        verify(userService).addAddress(42L, request);
    }

    @Test
    void removeAddressShouldDelegateToUserService() {
        userController.removeAddress(jwt, 9L);

        verify(userService).removeAddress(42L, 9L);
    }
}
