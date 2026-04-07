package com.example.orderapi.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

import java.math.BigDecimal;

@Embeddable
public class ProductWeight {

    @Column(nullable = false, precision = 10, scale = 4)
    private BigDecimal value;

    @Column(name = "weight_unit", nullable = false, length = 16)
    private String unit;

    public BigDecimal getValue() {
        return value;
    }

    public String getUnit() {
        return unit;
    }

    public void setValue(BigDecimal value) {
        this.value = value;
    }

    public void setUnit(String unit) {
        this.unit = unit;
    }
}
