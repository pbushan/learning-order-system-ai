package com.example.orderapi.controller;

import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.CrossOrigin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OrderControllerTest {

    @Test
    void controllerAllowsCrossOriginRequests() {
        assertTrue(OrderController.class.isAnnotationPresent(CrossOrigin.class));
        assertEquals("*", OrderController.class.getAnnotation(CrossOrigin.class).origins()[0]);
    }

    @Test
    void formatOrderStatusLabelFormatsNormalStatus() {
        assertEquals("Status: Submitted", OrderController.formatOrderStatusLabel("Submitted"));
    }

    @Test
    void formatOrderStatusLabelHandlesNullOrBlankStatus() {
        assertEquals("Status: Unknown", OrderController.formatOrderStatusLabel(null));
        assertEquals("Status: Unknown", OrderController.formatOrderStatusLabel("   "));
    }
}
