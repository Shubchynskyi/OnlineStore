package com.onlinestore.shipping.entity;

import com.onlinestore.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "shipping_provider_configs")
@Getter
@Setter
public class ShippingProviderConfig extends BaseEntity {

    @Column(name = "provider_code", nullable = false, unique = true)
    private String providerCode;

    @Column(name = "display_name", nullable = false)
    private String displayName;

    @Column(name = "is_enabled", nullable = false)
    private boolean enabled;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "supported_countries", columnDefinition = "jsonb", nullable = false)
    private List<String> supportedCountries = new ArrayList<>();
}
