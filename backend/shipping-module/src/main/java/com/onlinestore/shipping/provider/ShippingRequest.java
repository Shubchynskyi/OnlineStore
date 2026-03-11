package com.onlinestore.shipping.provider;

public record ShippingRequest(
    Long orderId,
    String destinationCountry,
    String destinationCity,
    String destinationPostalCode
) {
}
