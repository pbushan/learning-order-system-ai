package com.example.orderapi.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

import java.math.BigDecimal;

@Embeddable
public class ProductDimensions {

    @Column(nullable = false, precision = 10, scale = 4)
    private BigDecimal length;

    @Column(nullable = false, precision = 10, scale = 4)
    private BigDecimal width;

    @Column(nullable = false, precision = 10, scale = 4)
    private BigDecimal height;

    @Column(name = "dimensions_unit", nullable = false, length = 16)
    private String unit;

    public BigDecimal getLength() {
        return length;
    }

    public BigDecimal getWidth() {
        return width;
    }

    public BigDecimal getHeight() {
        return height;
    }

    public String getUnit() {
        return unit;
    }

    public void setLength(BigDecimal length) {
        this.length = length;
    }

    public void setWidth(BigDecimal width) {
        this.width = width;
    }

    public void setHeight(BigDecimal height) {
        this.height = height;
    }

    public void setUnit(String unit) {
        this.unit = unit;
    }
}
