package com.example.orderapi.dto;

import java.math.BigDecimal;

public class OrderRequest {
    private Long customerId;
    private String productName;
    private Integer quantity;
    private BigDecimal totalAmount;

    public Long getCustomerId() {
        return customerId;
    }

    public String getProductName() {
        return productName;
    }

    public Integer getQuantity() {
        return quantity;
    }

    public BigDecimal getTotalAmount() {
        return totalAmount;
    }

    public void setCustomerId(Long customerId) {
        this.customerId = customerId;
    }

    public void setProductName(String productName) {
        this.productName = productName;
    }

    public void setQuantity(Integer quantity) {
        this.quantity = quantity;
    }

    public void setTotalAmount(BigDecimal totalAmount) {
        this.totalAmount = totalAmount;
    }
}
