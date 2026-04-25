package com.example.orderapi.service;

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
    void returnsFallbackMessageWhenAllInputsAreMissingOrEmpty() {
        assertTrue(builder.buildIssueTraceSummary(null, null, 0, null).equals("Traceability summary unavailable."));
        assertTrue(builder.buildIssueTraceSummary("   ", "", 0, "   ").equals("Traceability summary unavailable."));
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
}
