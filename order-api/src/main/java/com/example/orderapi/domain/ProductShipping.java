package com.example.orderapi.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

@Embeddable
public class ProductShipping {

    @Column(nullable = false)
    private Boolean fragile;

    @Column(nullable = false)
    private Boolean hazmat;

    @Column(nullable = false)
    private Boolean requiresCooling;

    @Column(nullable = false)
    private Integer maxStackable;

    public Boolean getFragile() {
        return fragile;
    }

    public Boolean getHazmat() {
        return hazmat;
    }

    public Boolean getRequiresCooling() {
        return requiresCooling;
    }

    public Integer getMaxStackable() {
        return maxStackable;
    }

    public void setFragile(Boolean fragile) {
        this.fragile = fragile;
    }

    public void setHazmat(Boolean hazmat) {
        this.hazmat = hazmat;
    }

    public void setRequiresCooling(Boolean requiresCooling) {
        this.requiresCooling = requiresCooling;
    }

    public void setMaxStackable(Integer maxStackable) {
        this.maxStackable = maxStackable;
    }
}
