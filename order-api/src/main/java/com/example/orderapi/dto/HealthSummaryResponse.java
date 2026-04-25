package com.example.orderapi.dto;

public class HealthSummaryResponse {
    private long totalOrders;
    private long totalProducts;
    private long totalCustomers;

    public HealthSummaryResponse() {
    }

    public HealthSummaryResponse(long totalOrders, long totalProducts, long totalCustomers) {
        this.totalOrders = totalOrders;
        this.totalProducts = totalProducts;
        this.totalCustomers = totalCustomers;
    }

    public long getTotalOrders() {
        return totalOrders;
    }

    public void setTotalOrders(long totalOrders) {
        this.totalOrders = totalOrders;
    }

    public long getTotalProducts() {
        return totalProducts;
    }

    public void setTotalProducts(long totalProducts) {
        this.totalProducts = totalProducts;
    }

    public long getTotalCustomers() {
        return totalCustomers;
    }

    public void setTotalCustomers(long totalCustomers) {
        this.totalCustomers = totalCustomers;
    }
}
