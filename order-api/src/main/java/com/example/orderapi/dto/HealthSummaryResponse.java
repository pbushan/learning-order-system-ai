package com.example.orderapi.dto;

public class HealthSummaryResponse {
    private final long totalOrders;
    private final long totalProducts;
    private final long totalCustomers;

    private HealthSummaryResponse() {
        this(0L, 0L, 0L);
    }

    public HealthSummaryResponse(long totalOrders, long totalProducts, long totalCustomers) {
        this.totalOrders = totalOrders;
        this.totalProducts = totalProducts;
        this.totalCustomers = totalCustomers;
    }

    public long getTotalOrders() {
        return totalOrders;
    }

    public long getTotalProducts() {
        return totalProducts;
    }

    public long getTotalCustomers() {
        return totalCustomers;
    }
}
