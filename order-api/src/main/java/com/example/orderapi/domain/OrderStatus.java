package com.example.orderapi.domain;

public enum OrderStatus {
    DRAFT,
    SUBMITTED;

    public String getDisplayLabel() {
        return switch (this) {
            case DRAFT -> "Draft";
            case SUBMITTED -> "Submitted";
            default -> "Unknown";
        };
    }

    public static String getDisplayLabel(OrderStatus status) {
        return status != null ? status.getDisplayLabel() : "Unknown";
    }
}
