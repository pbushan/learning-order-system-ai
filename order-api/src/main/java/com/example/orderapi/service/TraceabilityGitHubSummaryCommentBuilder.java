package com.example.orderapi.service;

import com.example.orderapi.dto.DecisionTraceEventResponse;
import com.example.orderapi.dto.DecisionTraceResponse;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.List;

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

    public String buildDecisionTraceSummary(DecisionTraceResponse response) {
        if (response == null) {
            return "trace-unavailable";
        }

        String summary = trimSummary(response.getSummary());
        if (StringUtils.hasText(summary)) {
            return summary;
        }

        String traceId = StringUtils.hasText(response.getTraceId()) ? response.getTraceId().trim() : "trace-unavailable";
        int eventCount = safeEventCount(response.getEvents());
        StringBuilder builder = new StringBuilder(32);
        builder.append("Trace ").append(traceId);
        if (eventCount > 0) {
            builder.append(" (").append(eventCount).append(eventCount == 1 ? " event)" : " events)");
        }
        return builder.toString();
    }

    public String buildDecisionTraceSummary(String traceId, List<DecisionTraceEventResponse> events) {
        DecisionTraceResponse response = new DecisionTraceResponse();
        response.setTraceId(traceId);
        response.setEvents(events);
        return buildDecisionTraceSummary(response);
    }

    public String buildDecisionTraceSummary(String traceId, String summary, List<DecisionTraceEventResponse> events) {
        DecisionTraceResponse response = new DecisionTraceResponse();
        response.setTraceId(traceId);
        response.setSummary(summary);
        response.setEvents(events);
        return buildDecisionTraceSummary(response);
    }

    private int safeEventCount(List<DecisionTraceEventResponse> events) {
        return events == null ? 0 : events.size();
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
        String cleaned = rationaleSummary.trim().replaceAll("\\s+", " ").replaceAll("[\\r\\n]+", " ");
        int maxLength = 180;
        if (cleaned.length() <= maxLength) {
            return cleaned;
        }
        return cleaned.substring(0, maxLength - 3).trim() + "...";
    }

    private String trimSummary(String summary) {
        if (!StringUtils.hasText(summary)) {
            return "";
        }
        return summary.trim().replaceAll("\\s+", " ");
    }
}
