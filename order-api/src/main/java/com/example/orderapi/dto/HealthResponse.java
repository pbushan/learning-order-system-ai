package com.example.orderapi.dto;

import java.time.Instant;

public class HealthResponse {

    private String service;
    private String status;
    private String timestamp;

    public HealthResponse() {
    }

    public HealthResponse(String service, String status, String timestamp) {
        this.service = service;
        this.status = status;
        this.timestamp = timestamp;
    }

    public static HealthResponse ok(String service) {
        return new HealthResponse(service, "ok", Instant.now().toString());
    }

    public String getService() {
        return service;
    }

    public void setService(String service) {
        this.service = service;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(String timestamp) {
        this.timestamp = timestamp;
    }
}
