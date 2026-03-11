package com.onlinestore.users.api;

import com.onlinestore.users.dto.AddressDTO;
import com.onlinestore.users.dto.CreateAddressRequest;
import com.onlinestore.users.dto.UpdateProfileRequest;
import com.onlinestore.users.dto.UserDTO;
import com.onlinestore.users.service.UserService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @GetMapping("/me")
    public UserDTO getCurrentUser(@AuthenticationPrincipal Jwt jwt) {
        return userService.syncFromKeycloak(jwt);
    }

    @PutMapping("/me")
    public UserDTO updateProfile(
        @AuthenticationPrincipal Jwt jwt,
        @Valid @RequestBody UpdateProfileRequest request
    ) {
        return userService.updateProfile(jwt.getSubject(), request);
    }

    @GetMapping("/me/addresses")
    public List<AddressDTO> getAddresses(@AuthenticationPrincipal Jwt jwt) {
        return userService.getAddresses(jwt.getSubject());
    }

    @PostMapping("/me/addresses")
    @ResponseStatus(HttpStatus.CREATED)
    public AddressDTO addAddress(
        @AuthenticationPrincipal Jwt jwt,
        @Valid @RequestBody CreateAddressRequest request
    ) {
        return userService.addAddress(jwt.getSubject(), request);
    }

    @DeleteMapping("/me/addresses/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removeAddress(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id) {
        userService.removeAddress(jwt.getSubject(), id);
    }
}
