package com.onlinestore.users.service;

import com.onlinestore.common.exception.ResourceNotFoundException;
import com.onlinestore.common.port.identity.UserIdentityProvisioningPort;
import com.onlinestore.common.security.AuthenticatedUser;
import com.onlinestore.common.security.ExternalUserIdentity;
import com.onlinestore.users.dto.AddressDTO;
import com.onlinestore.users.dto.CreateAddressRequest;
import com.onlinestore.users.dto.UpdateProfileRequest;
import com.onlinestore.users.dto.UserDTO;
import com.onlinestore.users.entity.User;
import com.onlinestore.users.entity.UserProfile;
import com.onlinestore.users.mapper.UserMapper;
import com.onlinestore.users.repository.UserRepository;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UserService implements UserIdentityProvisioningPort {

    private final UserRepository userRepository;
    private final UserMapper userMapper;

    @Transactional(readOnly = true)
    public UserDTO getCurrentUser(Long userId) {
        return userMapper.toDto(findUserEntity(userId));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<AuthenticatedUser> findByKeycloakId(String keycloakId) {
        return userRepository.findByKeycloakId(keycloakId)
            .map(this::toAuthenticatedUser);
    }

    @Override
    @Transactional
    public AuthenticatedUser resolveOrProvision(ExternalUserIdentity externalUserIdentity) {
        return findByKeycloakId(externalUserIdentity.keycloakId())
            .orElseGet(() -> createUser(externalUserIdentity));
    }

    @Transactional
    public UserDTO updateProfile(Long userId, UpdateProfileRequest request) {
        var user = findUserEntity(userId);
        userMapper.updateProfile(user, request);
        return userMapper.toDto(userRepository.save(user));
    }

    @Transactional
    public AddressDTO addAddress(Long userId, CreateAddressRequest request) {
        var user = findUserEntity(userId);
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
    public List<AddressDTO> getAddresses(Long userId) {
        return findUserEntity(userId).getAddresses().stream()
            .map(userMapper::toAddressDto)
            .toList();
    }

    @Transactional
    public void removeAddress(Long userId, Long addressId) {
        var user = findUserEntity(userId);
        user.getAddresses().removeIf(address -> address.getId().equals(addressId));
        userRepository.save(user);
    }

    private User findUserEntity(Long userId) {
        return userRepository.findById(userId)
            .orElseThrow(() -> new ResourceNotFoundException("User", "id", userId));
    }

    private AuthenticatedUser createUser(ExternalUserIdentity externalUserIdentity) {
        var user = new User();
        user.setKeycloakId(externalUserIdentity.keycloakId());
        user.setEmail(externalUserIdentity.requiredEmail());
        user.setPhone(externalUserIdentity.phone());

        var profile = new UserProfile();
        profile.setUser(user);
        profile.setFirstName(externalUserIdentity.firstName());
        profile.setLastName(externalUserIdentity.lastName());
        user.setProfile(profile);

        try {
            return toAuthenticatedUser(userRepository.save(user));
        } catch (DataIntegrityViolationException ex) {
            return userRepository.findByKeycloakId(externalUserIdentity.keycloakId())
                .map(this::toAuthenticatedUser)
                .orElseThrow(() -> ex);
        }
    }

    private AuthenticatedUser toAuthenticatedUser(User user) {
        return new AuthenticatedUser(user.getId(), user.getKeycloakId());
    }
}
