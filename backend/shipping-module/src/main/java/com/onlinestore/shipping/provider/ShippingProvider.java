package com.onlinestore.shipping.provider;

import com.onlinestore.shipping.entity.Shipment;
import java.util.List;
import java.util.Set;

public interface ShippingProvider {

    String getProviderCode();

    Set<String> getSupportedCountries();

    List<ShippingRate> calculateRates(ShippingRequest request);

    Shipment createShipment(ShippingRequest request, ShippingRate selectedRate);

    TrackingInfo track(String trackingNumber);

    void cancelShipment(String shipmentId);
}
