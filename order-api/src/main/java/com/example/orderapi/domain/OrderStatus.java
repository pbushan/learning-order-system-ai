package com.example.orderapi.domain;

import java.util.Locale;

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

    public static String displayLabelOf(String statusName) {
        if (statusName == null || statusName.isBlank()) {
            return "Unknown";
        }
        try {
            return valueOf(statusName.trim().toUpperCase(Locale.ROOT)).getDisplayLabel();
        } catch (IllegalArgumentException ex) {
            return "Unknown";
        }
    }
}
