package com.example.orderapi.domain;

public enum OrderStatus {
    DRAFT,
    SUBMITTED;

    public String getLabel() {
        return switch (this) {
            case DRAFT -> "Draft";
            case SUBMITTED -> "Submitted";
        };
    }

    public static String labelOf(OrderStatus status) {
        return status != null ? status.getLabel() : "Unknown";
    }
}
