package com.example.orderapi.dto;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DecisionTraceEventResponseTest {

    @Test
    void getDisplayLabelUsesEventTypeWhenPresent() {
        DecisionTraceEventResponse response = new DecisionTraceEventResponse();
        response.setEventType("Decision approved");
        response.setSummary("Ignored summary");

        assertEquals("Decision approved", response.getDisplayLabel());
    }

    @Test
    void getDisplayLabelFallsBackToSummaryWhenEventTypeMissing() {
        DecisionTraceEventResponse response = new DecisionTraceEventResponse();
        response.setSummary("Summary only label");

        assertEquals("Summary only label", response.getDisplayLabel());
    }

    @Test
    void getDisplayLabelFallsBackToUntitledEventWhenEmpty() {
        DecisionTraceEventResponse response = new DecisionTraceEventResponse();
        response.setEventType("   ");
        response.setSummary(null);

        assertEquals("Untitled event", response.getDisplayLabel());
    }
}
