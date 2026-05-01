package com.example.orderapi.domain;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class OrderStatusTest {

    @Test
    void displayLabelOfReturnsFriendlyLabelForKnownStatus() {
        assertEquals("Draft", OrderStatus.displayLabelOf(OrderStatus.DRAFT));
        assertEquals("Submitted", OrderStatus.displayLabelOf(OrderStatus.SUBMITTED));
    }

    @Test
    void displayLabelOfFallsBackForNullStatus() {
        assertEquals("Unknown", OrderStatus.displayLabelOf(null));
    }
}
