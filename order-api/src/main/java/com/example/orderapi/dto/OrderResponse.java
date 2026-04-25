package com.example.orderapi.dto;

import com.example.orderapi.domain.Customer;
import com.example.orderapi.domain.Order;
import com.example.orderapi.domain.OrderStatus;

import java.math.BigDecimal;
import java.util.Locale;

public class OrderResponse {
    private Long id;
    private Long customerId;
    private String productName;
    private Integer quantity;
    private BigDecimal totalAmount;
    private String status;
    private String shippingType;
    private Integer estimatedDeliveryDays;

    public static OrderResponse from(Order order) {
        OrderResponse response = new OrderResponse();
        response.setId(order.getId());
        Customer customer = order.getCustomer();
        response.setCustomerId(customer != null ? customer.getId() : null);
        response.setProductName(order.getProductName());
        response.setQuantity(order.getQuantity());
        response.setTotalAmount(order.getTotalAmount());
        OrderStatus orderStatus = order.getStatus();
        response.setStatus(orderStatus != null ? orderStatus.name() : null);
        response.setShippingType(order.getShippingType());
        response.setEstimatedDeliveryDays(order.getEstimatedDeliveryDays());
        return response;
    }

    public String getStatusLabel() {
        if (status == null || status.trim().isEmpty()) {
            return "Unknown";
        }
        return switch (status.trim().toUpperCase(Locale.ROOT)) {
            case "PENDING" -> "Pending";
            case "CONFIRMED" -> "Confirmed";
            case "SHIPPED" -> "Shipped";
            case "DELIVERED" -> "Delivered";
            case "CANCELLED" -> "Cancelled";
            default -> "Unknown";
        };
    }

    public Long getId() {
        return id;
    }

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

    public String getStatus() {
        return status;
    }

    public String getShippingType() {
        return shippingType;
    }

    public Integer getEstimatedDeliveryDays() {
        return estimatedDeliveryDays;
    }

    public void setId(Long id) {
        this.id = id;
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

    public void setStatus(String status) {
        this.status = status;
    }

    public void setShippingType(String shippingType) {
        this.shippingType = shippingType;
    }

    public void setEstimatedDeliveryDays(Integer estimatedDeliveryDays) {
        this.estimatedDeliveryDays = estimatedDeliveryDays;
    }
}
