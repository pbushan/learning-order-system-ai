package com.example.orderapi.domain;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class OrderStatusTest {

    @Test
    void displayLabelReturnsHumanReadableTextForKnownStatuses() {
        assertEquals("Draft", OrderStatus.DRAFT.displayLabel());
        assertEquals("Submitted", OrderStatus.SUBMITTED.displayLabel());
    }

    @Test
    void displayLabelOfReturnsSafeFallbackForNullInput() {
        assertEquals("Unknown", OrderStatus.displayLabelOf(null));
    }
}
