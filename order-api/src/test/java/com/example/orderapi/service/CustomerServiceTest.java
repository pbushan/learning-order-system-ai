package com.example.orderapi.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CustomerServiceTest {

    @Test
    void buildDisplayNameReturnsFullNameWhenBothPresent() {
        assertEquals("Jane Doe", CustomerService.buildDisplayName("Jane", "Doe"));
    }

    @Test
    void buildDisplayNameReturnsSingleNameWhenOnlyOnePresent() {
        assertEquals("Jane", CustomerService.buildDisplayName("Jane", "   "));
        assertEquals("Doe", CustomerService.buildDisplayName(null, "Doe"));
    }

    @Test
    void buildDisplayNameReturnsFallbackWhenBothMissingOrBlank() {
        assertEquals("Unknown customer", CustomerService.buildDisplayName(null, null));
        assertEquals("Unknown customer", CustomerService.buildDisplayName("   ", ""));
    }
}
