package com.example.orderapi.domain;

public enum OrderStatus {
    DRAFT("Draft"),
    SUBMITTED("Submitted");

    private final String displayLabel;

    OrderStatus(String displayLabel) {
        this.displayLabel = displayLabel;
    }

    public String getDisplayLabel() {
        return displayLabel;
    }

    public static String getDisplayLabel(OrderStatus status) {
        return status == null ? "Unknown" : status.getDisplayLabel();
    }
}
