package com.example.orderapi.controller;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class OrderControllerTest {

    @Test
    void formatOrderStatusLabelFormatsNormalStatus() {
        assertEquals("Status: Submitted", OrderController.formatOrderStatusLabel("Submitted"));
    }

    @Test
    void formatOrderStatusLabelHandlesNullOrBlankStatus() {
        assertEquals("Status: Unknown", OrderController.formatOrderStatusLabel(null));
        assertEquals("Status: Unknown", OrderController.formatOrderStatusLabel("   "));
    }

    @Test
    void formatOrderStatusLabelPreservesAlreadyFormattedValue() {
        assertEquals("Status: Delivered", OrderController.formatOrderStatusLabel("Status: Delivered"));
        assertEquals("status: Delivered", OrderController.formatOrderStatusLabel("status: Delivered"));
    }
}
