package com.example.orderapi.dto;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class OrderResponseTest {

    @Test
    void knownStatusesMapToExpectedLabels() {
        Map<String, String> expectations = new LinkedHashMap<>();
        expectations.put("PENDING", "Pending");
        expectations.put("CONFIRMED", "Confirmed");
        expectations.put("SHIPPED", "Shipped");
        expectations.put("DELIVERED", "Delivered");
        expectations.put("CANCELLED", "Cancelled");

        expectations.forEach((status, label) -> {
            OrderResponse response = new OrderResponse();
            response.setStatus(status);

            assertEquals(label, response.getStatusLabel());
        });
    }

    @Test
    void nullOrBlankStatusReturnsFallbackLabel() {
        OrderResponse nullStatus = new OrderResponse();
        assertEquals("Unknown", nullStatus.getStatusLabel());

        OrderResponse blankStatus = new OrderResponse();
        blankStatus.setStatus("   ");
        assertEquals("Unknown", blankStatus.getStatusLabel());
    }

    @Test
    void helperDoesNotModifyExistingDtoFields() {
        OrderResponse response = new OrderResponse();
        response.setId(10L);
        response.setCustomerId(20L);
        response.setProductName("Widget");
        response.setQuantity(3);
        response.setTotalAmount(new BigDecimal("19.99"));
        response.setStatus("pending");
        response.setShippingType("EXPRESS");
        response.setEstimatedDeliveryDays(2);

        String label = response.getStatusLabel();

        assertEquals("Pending", label);
        assertEquals(10L, response.getId());
        assertEquals(20L, response.getCustomerId());
        assertEquals("Widget", response.getProductName());
        assertEquals(3, response.getQuantity());
        assertEquals(new BigDecimal("19.99"), response.getTotalAmount());
        assertEquals("pending", response.getStatus());
        assertEquals("EXPRESS", response.getShippingType());
        assertEquals(2, response.getEstimatedDeliveryDays());
    }
}
