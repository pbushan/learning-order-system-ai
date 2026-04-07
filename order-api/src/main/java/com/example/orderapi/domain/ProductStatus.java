package com.example.orderapi.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

@Embeddable
public class ProductStatus {

    @Column(nullable = false)
    private Boolean active;

    @Column(nullable = false)
    private Boolean shippable;

    public Boolean getActive() {
        return active;
    }

    public Boolean getShippable() {
        return shippable;
    }

    public void setActive(Boolean active) {
        this.active = active;
    }

    public void setShippable(Boolean shippable) {
        this.shippable = shippable;
    }
}
