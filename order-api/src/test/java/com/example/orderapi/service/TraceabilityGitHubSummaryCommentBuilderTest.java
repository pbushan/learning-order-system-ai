package com.example.orderapi.service;

import com.example.orderapi.dto.DecisionTraceEventResponse;
import com.example.orderapi.dto.DecisionTraceResponse;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TraceabilityGitHubSummaryCommentBuilderTest {

    private final TraceabilityGitHubSummaryCommentBuilder builder = new TraceabilityGitHubSummaryCommentBuilder();

    @Test
    void buildsConciseCommentWithClassificationTraceAndDecompositionContext() {
        String comment = builder.buildIssueTraceSummary(
                "trace-abc-123",
                "Feature",
                3,
                "Intake classification mapped to a UX enhancement request."
        );

        assertTrue(comment.contains("Generated via agent-assisted intake."));
        assertTrue(comment.contains("- Classification: `feature`"));
        assertTrue(comment.contains("- Decomposed multi-issue set: yes (3 issues)"));
        assertTrue(comment.contains("- Trace ID: `trace-abc-123`"));
        assertTrue(comment.contains("- Rationale summary: Intake classification mapped to a UX enhancement request."));
    }

    @Test
    void fallsBackToUnknownClassificationAndOmitsEmptyRationale() {
        String comment = builder.buildIssueTraceSummary("trace-xyz", "maintenance", 1, "   ");

        assertTrue(comment.contains("- Classification: `unknown`"));
        assertTrue(comment.contains("- Decomposed multi-issue set: no"));
        assertFalse(comment.contains("Rationale summary:"));
    }

    @Test
    void truncatesLongRationaleToKeepCommentShort() {
        String comment = builder.buildIssueTraceSummary(
                "trace-xyz",
                "bug",
                1,
                "This rationale is intentionally long to verify the summary remains concise and suitable for issue comments while still retaining enough context for engineers to understand where the issue came from in the intake flow."
        );

        assertTrue(comment.contains("- Classification: `bug`"));
        assertTrue(comment.contains("- Rationale summary:"));
        assertTrue(comment.endsWith("..."));
    }

    @Test
    void buildsDecisionTraceSummaryForNormalAndMissingFieldEvents() {
        DecisionTraceEventResponse event = new DecisionTraceEventResponse();
        event.setEventType("decision-made");
        event.setStatus("approved");
        event.setActor("policy-engine");
        event.setSummary("Approved after validation checks passed.");

        DecisionTraceResponse response = new DecisionTraceResponse();
        response.setTraceId("trace-123");
        response.setEvents(List.of(event));

        assertEquals(
                "Trace trace-123: decision-made | approved | policy-engine | Approved after validation checks passed.",
                builder.buildDecisionTraceSummary(response)
        );

        DecisionTraceEventResponse sparseEvent = new DecisionTraceEventResponse();
        DecisionTraceResponse sparseResponse = new DecisionTraceResponse();
        sparseResponse.setEvents(List.of(sparseEvent));

        assertEquals("Trace unavailable: trace event details unavailable", builder.buildDecisionTraceSummary(sparseResponse));
        assertEquals("Decision trace unavailable.", builder.buildDecisionTraceSummary(null));
    }
}
