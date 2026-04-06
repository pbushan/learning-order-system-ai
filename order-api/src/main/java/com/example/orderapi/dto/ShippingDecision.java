package com.example.orderapi.dto;

public class ShippingDecision {
    private String shippingType;
    private Integer estimatedDeliveryDays;

    public String getShippingType() {
        return shippingType;
    }

    public Integer getEstimatedDeliveryDays() {
        return estimatedDeliveryDays;
    }

    public void setShippingType(String shippingType) {
        this.shippingType = shippingType;
    }

    public void setEstimatedDeliveryDays(Integer estimatedDeliveryDays) {
        this.estimatedDeliveryDays = estimatedDeliveryDays;
    }
}
