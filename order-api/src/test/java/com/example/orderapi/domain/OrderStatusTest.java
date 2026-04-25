package com.example.orderapi.domain;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class OrderStatusTest {

    @Test
    void getDisplayLabelReturnsStableHumanReadableLabels() {
        assertEquals("Draft", OrderStatus.DRAFT.getDisplayLabel());
        assertEquals("Submitted", OrderStatus.SUBMITTED.getDisplayLabel());
    }

    @Test
    void displayLabelOfHandlesNullAndUnknownValues() {
        assertEquals("Unknown", OrderStatus.displayLabelOf((OrderStatus) null));
        assertEquals("Unknown", OrderStatus.displayLabelOf((String) null));
        assertEquals("Unknown", OrderStatus.displayLabelOf(""));
        assertEquals("Unknown", OrderStatus.displayLabelOf("not-a-status"));
    }

    @Test
    void displayLabelOfParsesStatusNamesCaseInsensitively() {
        assertEquals("Draft", OrderStatus.displayLabelOf("draft"));
        assertEquals("Submitted", OrderStatus.displayLabelOf(" SUBMITTED "));
    }
}
