package com.example.orderapi.service;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class TraceabilityGitHubSummaryCommentBuilder {
    // Keep this formatter aligned with traceability/github_summary.py (shared traceability contract).
    private static final int MAX_RATIONALE_LENGTH = 180;

    public String buildIssueTraceSummary(String traceId,
                                         String classification,
                                         int issueCount,
                                         String rationaleSummary) {
        String normalizedClassification = normalizeClassification(classification);
        String normalizedTraceId = StringUtils.hasText(traceId) ? traceId.trim() : "trace-unavailable";
        String decomposedSet = issueCount > 1 ? "yes (" + issueCount + " issues)" : "no";

        String rationale = trimRationale(rationaleSummary);
        boolean hasMeaningfulSummary = StringUtils.hasText(rationale);
        boolean hasEvents = issueCount > 0;

        StringBuilder builder = new StringBuilder();
        builder.append("Generated via agent-assisted intake.").append(System.lineSeparator()).append(System.lineSeparator());
        builder.append("- Classification: `").append(normalizedClassification).append("`").append(System.lineSeparator());
        builder.append("- Decomposed multi-issue set: ").append(decomposedSet).append(System.lineSeparator());
        builder.append("- Trace ID: `").append(normalizedTraceId).append("`");

        if (hasMeaningfulSummary) {
            builder.append(System.lineSeparator());
            builder.append("- Rationale summary: ").append(rationale);
        } else if (!hasEvents) {
            builder.append(System.lineSeparator());
            builder.append("- Summary: No decision-trace events were available to summarize.");
        } else {
            builder.append(System.lineSeparator());
            builder.append("- Summary: No meaningful decision-trace summary was available.");
        }
        return builder.toString();
    }

    private String normalizeClassification(String classification) {
        if (!StringUtils.hasText(classification)) {
            return "unknown";
        }
        String value = classification.trim().toLowerCase();
        if ("bug".equals(value) || "feature".equals(value)) {
            return value;
        }
        return "unknown";
    }

    private String trimRationale(String rationaleSummary) {
        if (!StringUtils.hasText(rationaleSummary)) {
            return "";
        }
        String cleaned = rationaleSummary.trim().replaceAll("\\s+", " ");
        if (cleaned.length() <= MAX_RATIONALE_LENGTH) {
            return cleaned;
        }
        return cleaned.substring(0, MAX_RATIONALE_LENGTH - 3).trim() + "...";
    }
}
