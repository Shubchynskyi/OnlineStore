package com.onlinestore.shipping.entity;

import com.onlinestore.common.entity.BaseEntity;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "shipments")
@Getter
@Setter
public class Shipment extends BaseEntity {

    @Column(name = "order_id", nullable = false)
    private Long orderId;

    @Column(name = "provider_code", nullable = false)
    private String providerCode;

    @Column(name = "tracking_number")
    private String trackingNumber;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ShipmentStatus status = ShipmentStatus.PENDING;

    @Column(name = "estimated_delivery")
    private LocalDate estimatedDelivery;

    @Column(name = "shipping_cost_amount", precision = 12, scale = 2)
    private BigDecimal shippingCostAmount;

    @Column(name = "shipping_cost_currency", length = 3)
    private String shippingCostCurrency = "EUR";

    @Column(name = "label_url")
    private String labelUrl;

    @OneToMany(mappedBy = "shipment", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<TrackingEvent> trackingEvents = new ArrayList<>();
}
