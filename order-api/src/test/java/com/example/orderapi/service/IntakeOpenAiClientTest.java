package com.example.orderapi.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.*;

class IntakeOpenAiClientTest {

    private IntakeOpenAiClient client;
    private int maxChars;

    @BeforeEach
    void setUp() {
        client = new IntakeOpenAiClient(new ObjectMapper(), "test-key", "gpt-4.1-mini");
        maxChars = (int) ReflectionTestUtils.getField(client, "MAX_CONTENT_CHARS");
    }

    @Test
    void truncateContent_keepsTextWhenExactlyAtLimit() {
        String input = "a".repeat(maxChars);

        String result = (String) ReflectionTestUtils.invokeMethod(client, "truncateContent", input);

        assertEquals(input, result);
    }

    @Test
    void truncateContent_prefersBoundaryWhenPastLimitAndBoundaryExists() {
        String prefix = "a".repeat(maxChars - 10);
        String input = prefix + ". next sentence with detail";

        String result = (String) ReflectionTestUtils.invokeMethod(client, "truncateContent", input);

        assertTrue(result.length() <= maxChars);
        assertTrue(result.length() < maxChars);
    }

    @Test
    void truncateContent_fallsBackToHardLimitWhenNoBoundaryInWindow() {
        String input = "x".repeat(maxChars + 50);

        String result = (String) ReflectionTestUtils.invokeMethod(client, "truncateContent", input);

        assertEquals(maxChars, result.length());
    }

    @Test
    void truncateContent_handlesStructuredTailWithoutThrowing() {
        String jsonTail = " {\"type\":\"bug\",\"title\":\"checkout timeout\",\"priority\":\"high\"}";
        String input = "context ".repeat(300) + jsonTail;

        String result = (String) ReflectionTestUtils.invokeMethod(client, "truncateContent", input);

        assertNotNull(result);
        assertFalse(result.isBlank());
        assertTrue(result.length() <= maxChars);
    }
}
