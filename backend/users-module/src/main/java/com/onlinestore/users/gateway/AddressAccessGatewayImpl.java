package com.onlinestore.users.gateway;

import com.onlinestore.common.port.address.AddressAccessGateway;
import com.onlinestore.users.repository.AddressRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AddressAccessGatewayImpl implements AddressAccessGateway {

    private final AddressRepository addressRepository;

    @Override
    public boolean isAddressOwnedByUser(Long addressId, Long userId) {
        return addressRepository.existsByIdAndUser_Id(addressId, userId);
    }
}
