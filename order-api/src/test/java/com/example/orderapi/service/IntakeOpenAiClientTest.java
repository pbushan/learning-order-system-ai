package com.example.orderapi.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class IntakeOpenAiClientTest {

    private IntakeOpenAiClient client;
    private int maxChars;

    @BeforeEach
    void setUp() {
        client = new IntakeOpenAiClient(new ObjectMapper(), "test-key", "gpt-4.1-mini");
        maxChars = IntakeOpenAiClient.MAX_CONTENT_CHARS;
    }

    @Test
    void truncateContent_keepsTextWhenExactlyAtLimit() {
        String input = "a".repeat(maxChars);

        String result = client.truncateContent(input);

        assertEquals(input, result);
    }

    @Test
    void truncateContent_prefersBoundaryWhenPastLimitAndBoundaryExists() {
        String prefix = "a".repeat(maxChars - 10);
        String input = prefix + ". next sentence with detail";

        String result = client.truncateContent(input);

        assertTrue(result.length() <= maxChars);
        assertTrue(result.length() < maxChars);
    }

    @Test
    void truncateContent_fallsBackToHardLimitWhenNoBoundaryInWindow() {
        String input = "x".repeat(maxChars + 50);

        String result = client.truncateContent(input);

        assertEquals(maxChars, result.length());
    }

    @Test
    void truncateContent_handlesStructuredTailWithoutThrowing() {
        String jsonTail = " {\"type\":\"bug\",\"title\":\"checkout timeout\",\"priority\":\"high\"}";
        String input = "context ".repeat(300) + jsonTail;

        String result = client.truncateContent(input);

        assertNotNull(result);
        assertFalse(result.isBlank());
        assertTrue(result.length() <= maxChars);
    }

    @Test
    void truncateContent_doesNotSplitSurrogatePairs() {
        String emoji = "\uD83D\uDE80";
        String input = "a".repeat(maxChars - 1) + emoji + " trailing";

        String result = client.truncateContent(input);

        assertTrue(result.length() <= maxChars);
        assertFalse(result.endsWith("\uD83D"));
    }

    @Test
    void truncateContent_maxPlusOneRemainsWithinCap() {
        String input = "a".repeat(maxChars) + "b";

        String result = client.truncateContent(input);

        assertEquals(maxChars, result.length());
    }

    @Test
    void truncateContent_usesHardCapWhenOnlyEarlyBoundaryExists() {
        String input = "a".repeat(maxChars - 60) + " " + "b".repeat(120);

        String result = client.truncateContent(input);

        assertEquals(maxChars, result.length());
    }
}
