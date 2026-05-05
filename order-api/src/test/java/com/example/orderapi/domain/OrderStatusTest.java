package com.example.orderapi.domain;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class OrderStatusTest {

    @Test
    void displayLabelMatchesExpectedValue() {
        assertEquals("Draft", OrderStatus.DRAFT.getDisplayLabel());
        assertEquals("Submitted", OrderStatus.SUBMITTED.getDisplayLabel());
    }

    @Test
    void staticDisplayLabelHandlesNullSafely() {
        assertEquals("Unknown", OrderStatus.getDisplayLabel(null));
    }
}
