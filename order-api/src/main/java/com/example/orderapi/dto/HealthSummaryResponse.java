package com.example.orderapi.dto;

import java.time.Instant;

public class HealthSummaryResponse {

    private String serviceName;
    private Instant timestamp;
    private long customerCount;
    private long productCount;
    private long orderCount;

    public HealthSummaryResponse() {
    }

    public HealthSummaryResponse(String serviceName, Instant timestamp, long customerCount, long productCount, long orderCount) {
        this.serviceName = serviceName;
        this.timestamp = timestamp;
        this.customerCount = customerCount;
        this.productCount = productCount;
        this.orderCount = orderCount;
    }

    public String getServiceName() {
        return serviceName;
    }

    public void setServiceName(String serviceName) {
        this.serviceName = serviceName;
    }

    public Instant getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(Instant timestamp) {
        this.timestamp = timestamp;
    }

    public long getCustomerCount() {
        return customerCount;
    }

    public void setCustomerCount(long customerCount) {
        this.customerCount = customerCount;
    }

    public long getProductCount() {
        return productCount;
    }

    public void setProductCount(long productCount) {
        this.productCount = productCount;
    }

    public long getOrderCount() {
        return orderCount;
    }

    public void setOrderCount(long orderCount) {
        this.orderCount = orderCount;
    }
}
