package com.example.orderapi.service;

import com.example.orderapi.dto.DecisionTraceEventResponse;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
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
    void buildsDecisionTraceSummaryAndFallsBackForMissingFields() {
        DecisionTraceEventResponse event = new DecisionTraceEventResponse();
        event.setEventType("decision");
        event.setStatus("approved");
        event.setActor("  policy-engine  ");
        event.setSummary("  Approved after automated checks passed.  ");

        String summary = builder.buildDecisionTraceSummary(event);

        assertTrue(summary.contains("decision"));
        assertTrue(summary.contains("status=approved"));
        assertTrue(summary.contains("actor=policy-engine"));
        assertTrue(summary.contains("summary=Approved after automated checks passed."));

        DecisionTraceEventResponse emptyEvent = new DecisionTraceEventResponse();
        assertTrue(builder.buildDecisionTraceSummary(emptyEvent).contains("decision-event"));
        assertTrue(builder.buildDecisionTraceSummary(null).contains("unavailable"));
    }
}
