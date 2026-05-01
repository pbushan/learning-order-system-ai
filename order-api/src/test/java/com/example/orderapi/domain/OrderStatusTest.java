package com.example.orderapi.domain;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class OrderStatusTest {

    @Test
    void labelOfReturnsHumanFriendlyLabelsForKnownStatuses() {
        assertEquals("Draft", OrderStatus.labelOf(OrderStatus.DRAFT));
        assertEquals("Submitted", OrderStatus.labelOf(OrderStatus.SUBMITTED));
    }

    @Test
    void labelOfReturnsSafeFallbackForNullInput() {
        assertEquals("Unknown", OrderStatus.labelOf(null));
    }
}
