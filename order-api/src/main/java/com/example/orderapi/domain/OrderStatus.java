package com.example.orderapi.domain;

public enum OrderStatus {
    DRAFT,
    SUBMITTED;

    public String getDisplayLabel() {
        return switch (this) {
            case DRAFT -> "Draft";
            case SUBMITTED -> "Submitted";
        };
    }

    public static String displayLabelOf(OrderStatus status) {
        return status != null ? status.getDisplayLabel() : "Unknown";
    }
}
