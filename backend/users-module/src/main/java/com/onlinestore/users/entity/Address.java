package com.onlinestore.users.entity;

import com.onlinestore.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "addresses")
@Getter
@Setter
public class Address extends BaseEntity {

    @ManyToOne
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    private String label;

    @Column(nullable = false, length = 3)
    private String country;

    @Column(nullable = false)
    private String city;

    @Column(nullable = false)
    private String street;

    private String building;

    private String apartment;

    @Column(name = "postal_code", nullable = false)
    private String postalCode;

    @Column(name = "is_default", nullable = false)
    private boolean isDefault;
}
