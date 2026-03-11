package com.onlinestore.users.mapper;

import com.onlinestore.users.dto.AddressDTO;
import com.onlinestore.users.dto.CreateAddressRequest;
import com.onlinestore.users.dto.UpdateProfileRequest;
import com.onlinestore.users.dto.UserDTO;
import com.onlinestore.users.entity.Address;
import com.onlinestore.users.entity.User;
import com.onlinestore.users.entity.UserProfile;
import org.springframework.stereotype.Component;

@Component
public class UserMapper {

    public UserDTO toDto(User user) {
        String firstName = user.getProfile() == null ? null : user.getProfile().getFirstName();
        String lastName = user.getProfile() == null ? null : user.getProfile().getLastName();
        String avatarUrl = user.getProfile() == null ? null : user.getProfile().getAvatarUrl();
        return new UserDTO(
            user.getId(),
            user.getEmail(),
            user.getPhone(),
            firstName,
            lastName,
            avatarUrl,
            user.getStatus()
        );
    }

    public void updateProfile(User user, UpdateProfileRequest request) {
        if (user.getProfile() == null) {
            var profile = new UserProfile();
            profile.setUser(user);
            user.setProfile(profile);
        }
        user.getProfile().setFirstName(request.firstName());
        user.getProfile().setLastName(request.lastName());
        user.setPhone(request.phone());
    }

    public Address toAddress(CreateAddressRequest request) {
        var address = new Address();
        address.setLabel(request.label());
        address.setCountry(request.country());
        address.setCity(request.city());
        address.setStreet(request.street());
        address.setBuilding(request.building());
        address.setApartment(request.apartment());
        address.setPostalCode(request.postalCode());
        address.setDefault(request.isDefault());
        return address;
    }

    public AddressDTO toAddressDto(Address address) {
        return new AddressDTO(
            address.getId(),
            address.getLabel(),
            address.getCountry(),
            address.getCity(),
            address.getStreet(),
            address.getBuilding(),
            address.getApartment(),
            address.getPostalCode(),
            address.isDefault()
        );
    }
}
