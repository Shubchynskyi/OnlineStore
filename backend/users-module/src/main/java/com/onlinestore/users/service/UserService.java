package com.onlinestore.users.service;

import com.onlinestore.common.exception.ResourceNotFoundException;
import com.onlinestore.users.dto.AddressDTO;
import com.onlinestore.users.dto.CreateAddressRequest;
import com.onlinestore.users.dto.UpdateProfileRequest;
import com.onlinestore.users.dto.UserDTO;
import com.onlinestore.users.entity.User;
import com.onlinestore.users.entity.UserProfile;
import com.onlinestore.users.mapper.UserMapper;
import com.onlinestore.users.repository.UserRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final UserMapper userMapper;

    @Transactional(readOnly = true)
    public UserDTO findByKeycloakId(String keycloakId) {
        return userRepository.findByKeycloakId(keycloakId)
            .map(userMapper::toDto)
            .orElseThrow(() -> new ResourceNotFoundException("User", "keycloakId", keycloakId));
    }

    @Transactional
    public UserDTO syncFromKeycloak(Jwt jwt) {
        String keycloakId = jwt.getSubject();
        return userRepository.findByKeycloakId(keycloakId)
            .map(userMapper::toDto)
            .orElseGet(() -> createFromJwt(jwt));
    }

    @Transactional
    public UserDTO updateProfile(String keycloakId, UpdateProfileRequest request) {
        var user = findUserEntity(keycloakId);
        userMapper.updateProfile(user, request);
        return userMapper.toDto(userRepository.save(user));
    }

    @Transactional
    public AddressDTO addAddress(String keycloakId, CreateAddressRequest request) {
        var user = findUserEntity(keycloakId);
        if (request.isDefault()) {
            user.getAddresses().forEach(existing -> existing.setDefault(false));
        }
        var address = userMapper.toAddress(request);
        address.setUser(user);
        user.getAddresses().add(address);
        userRepository.save(user);
        return userMapper.toAddressDto(address);
    }

    @Transactional(readOnly = true)
    public List<AddressDTO> getAddresses(String keycloakId) {
        return findUserEntity(keycloakId).getAddresses().stream()
            .map(userMapper::toAddressDto)
            .toList();
    }

    @Transactional
    public void removeAddress(String keycloakId, Long addressId) {
        var user = findUserEntity(keycloakId);
        user.getAddresses().removeIf(address -> address.getId().equals(addressId));
        userRepository.save(user);
    }

    private User findUserEntity(String keycloakId) {
        return userRepository.findByKeycloakId(keycloakId)
            .orElseThrow(() -> new ResourceNotFoundException("User", "keycloakId", keycloakId));
    }

    private UserDTO createFromJwt(Jwt jwt) {
        var user = new User();
        user.setKeycloakId(jwt.getSubject());
        user.setEmail(jwt.getClaimAsString("email"));
        user.setPhone(jwt.getClaimAsString("phone_number"));

        var profile = new UserProfile();
        profile.setUser(user);
        profile.setFirstName(jwt.getClaimAsString("given_name"));
        profile.setLastName(jwt.getClaimAsString("family_name"));
        user.setProfile(profile);

        return userMapper.toDto(userRepository.save(user));
    }
}
