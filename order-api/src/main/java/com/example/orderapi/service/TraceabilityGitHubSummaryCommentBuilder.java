package com.example.orderapi.service;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class TraceabilityGitHubSummaryCommentBuilder {
    // Keep this formatter aligned with traceability/github_summary.py (shared traceability contract).

    public String buildIssueTraceSummary(String traceId,
                                         String classification,
                                         int issueCount,
                                         String rationaleSummary) {
        String normalizedClassification = normalizeClassification(classification);
        String normalizedTraceId = StringUtils.hasText(traceId) ? traceId.trim() : "trace-unavailable";
        String decomposedSet = issueCount > 1 ? "yes (" + issueCount + " issues)" : "no";

        StringBuilder builder = new StringBuilder();
        builder.append("Generated via agent-assisted intake.").append(System.lineSeparator()).append(System.lineSeparator());
        builder.append("- Classification: `").append(normalizedClassification).append("`").append(System.lineSeparator());
        builder.append("- Decomposed multi-issue set: ").append(decomposedSet).append(System.lineSeparator());
        builder.append("- Trace ID: `").append(normalizedTraceId).append("`");

        String rationale = trimRationale(rationaleSummary);
        if (StringUtils.hasText(rationale)) {
            builder.append(System.lineSeparator());
            builder.append("- Rationale summary: ").append(rationale);
        }
        return builder.toString();
    }

    public String buildEmptyTraceSummaryComment() {
        return "Generated via agent-assisted intake. No traceability events were available.";
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
        int maxLength = 180;
        if (cleaned.length() <= maxLength) {
            return cleaned;
        }
        return cleaned.substring(0, maxLength - 3).trim() + "...";
    }
}
