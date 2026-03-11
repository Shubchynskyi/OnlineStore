package com.onlinestore.users.gateway;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.onlinestore.users.repository.AddressRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AddressAccessGatewayImplTest {

    @Mock
    private AddressRepository addressRepository;

    private AddressAccessGatewayImpl gateway;

    @BeforeEach
    void setUp() {
        gateway = new AddressAccessGatewayImpl(addressRepository);
    }

    @Test
    void isAddressOwnedByUserShouldDelegateToRepository() {
        when(addressRepository.existsByIdAndUser_Id(10L, 3L)).thenReturn(true);

        var result = gateway.isAddressOwnedByUser(10L, 3L);

        assertTrue(result);
        verify(addressRepository).existsByIdAndUser_Id(10L, 3L);
    }
}
