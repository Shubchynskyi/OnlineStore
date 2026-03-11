package com.onlinestore.shipping.provider;

import java.util.List;
import java.util.Set;

public interface ShippingProvider {

    String getProviderCode();

    Set<String> getSupportedCountries();

    ShippingRate calculateRate(ShippingRequest request);

    String createShipment(ShippingRequest request);

    List<TrackingInfo> track(String trackingNumber);
}
