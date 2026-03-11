package com.onlinestore.common.port.address;

public interface AddressAccessGateway {

    boolean isAddressOwnedByUser(Long addressId, Long userId);
}
