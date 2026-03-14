package com.onlinestore.shipping.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.onlinestore.common.security.AuthenticatedUser;
import com.onlinestore.common.security.AuthenticatedUserResolver;
import com.onlinestore.shipping.dto.ShipmentDTO;
import com.onlinestore.shipping.dto.ShippingProviderConfigDTO;
import com.onlinestore.shipping.dto.ShippingRateDTO;
import com.onlinestore.shipping.dto.TrackingEventDTO;
import com.onlinestore.shipping.dto.UpdateShippingProviderConfigRequest;
import com.onlinestore.shipping.provider.ShippingRequest;
import com.onlinestore.shipping.service.ShippingProviderConfigService;
import com.onlinestore.shipping.service.ShippingService;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.oauth2.jwt.Jwt;

@ExtendWith(MockitoExtension.class)
class ShippingApiControllersTest {

    @Mock
    private AuthenticatedUserResolver authenticatedUserResolver;

    @Mock
    private ShippingService shippingService;

    @Mock
    private ShippingProviderConfigService shippingProviderConfigService;

    private Jwt jwt;
    private ShippingController shippingController;
    private AdminShippingController adminShippingController;

    @BeforeEach
    void setUp() {
        jwt = Jwt.withTokenValue("token")
            .header("alg", "none")
            .claim("sub", "kc-1")
            .build();
        shippingController = new ShippingController(authenticatedUserResolver, shippingService);
        adminShippingController = new AdminShippingController(shippingProviderConfigService);
    }

    @Test
    void calculateRatesShouldDelegateToShippingService() {
        stubAuthenticatedUser();
        var request = new ShippingController.QuoteRatesRequest(
            15L,
            "DHL",
            "US",
            "New York",
            "10001"
        );
        var expectedRequest = new ShippingRequest(15L, "US", "New York", "10001");
        var response = List.of(new ShippingRateDTO("EXP", "DHL", "Express", BigDecimal.TEN, "USD", 2));
        when(shippingService.calculateRates(42L, 15L, expectedRequest, "DHL")).thenReturn(response);

        assertThat(shippingController.calculateRates(jwt, request)).isSameAs(response);

        verify(shippingService).calculateRates(42L, 15L, expectedRequest, "DHL");
    }

    @Test
    void createShipmentShouldDelegateToShippingService() {
        stubAuthenticatedUser();
        var request = new ShippingController.CreateShipmentRequest(
            15L,
            "DHL",
            "EXP",
            "US",
            "New York",
            "10001"
        );
        var expectedRequest = new ShippingRequest(15L, "US", "New York", "10001");
        var response = mock(ShipmentDTO.class);
        when(shippingService.createShipment(42L, 15L, expectedRequest, "DHL", "EXP")).thenReturn(response);

        assertThat(shippingController.createShipment(jwt, request)).isSameAs(response);

        verify(shippingService).createShipment(42L, 15L, expectedRequest, "DHL", "EXP");
    }

    @Test
    void getByOrderShouldDelegateToShippingService() {
        stubAuthenticatedUser();
        var response = mock(ShipmentDTO.class);
        when(shippingService.getByOrderId(42L, 15L)).thenReturn(response);

        assertThat(shippingController.getByOrder(jwt, 15L)).isSameAs(response);

        verify(shippingService).getByOrderId(42L, 15L);
    }

    @Test
    void trackShipmentShouldDelegateToShippingService() {
        stubAuthenticatedUser();
        var response = List.of(mock(TrackingEventDTO.class));
        when(shippingService.trackShipment(42L, 9L)).thenReturn(response);

        assertThat(shippingController.trackShipment(jwt, 9L)).isSameAs(response);

        verify(shippingService).trackShipment(42L, 9L);
    }

    @Test
    void cancelShipmentShouldDelegateToShippingService() {
        stubAuthenticatedUser();
        var response = mock(ShipmentDTO.class);
        when(shippingService.cancelShipment(42L, 9L)).thenReturn(response);

        assertThat(shippingController.cancelShipment(jwt, 9L)).isSameAs(response);

        verify(shippingService).cancelShipment(42L, 9L);
    }

    @Test
    void listProviderConfigsShouldDelegateToConfigService() {
        var response = List.of(mock(ShippingProviderConfigDTO.class));
        when(shippingProviderConfigService.listConfigs()).thenReturn(response);

        assertThat(adminShippingController.listProviderConfigs()).isSameAs(response);

        verify(shippingProviderConfigService).listConfigs();
    }

    @Test
    void updateProviderConfigShouldDelegateToConfigService() {
        var request = mock(UpdateShippingProviderConfigRequest.class);
        var response = mock(ShippingProviderConfigDTO.class);
        when(shippingProviderConfigService.updateProviderConfig("DHL", request)).thenReturn(response);

        assertThat(adminShippingController.updateProviderConfig("DHL", request)).isSameAs(response);

        verify(shippingProviderConfigService).updateProviderConfig("DHL", request);
    }

    private void stubAuthenticatedUser() {
        when(authenticatedUserResolver.resolve(jwt)).thenReturn(new AuthenticatedUser(42L, "kc-1"));
    }
}
