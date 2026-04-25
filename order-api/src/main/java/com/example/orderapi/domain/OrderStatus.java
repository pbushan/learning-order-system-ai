package com.example.orderapi.domain;

public enum OrderStatus {
    DRAFT,
    SUBMITTED;

    public String displayLabel() {
        return switch (this) {
            case DRAFT -> "Draft";
            case SUBMITTED -> "Submitted";
        };
    }

    public static String displayLabelOf(OrderStatus status) {
        return status != null ? status.displayLabel() : "Unknown";
    }
}
