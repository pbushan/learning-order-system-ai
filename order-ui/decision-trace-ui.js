(function (root, factory) {
    if (typeof module !== "undefined" && module.exports) {
        module.exports = factory();
        return;
    }
    root.DecisionTraceUi = factory();
}(typeof self !== "undefined" ? self : this, function () {
    const STEP_ORDER = [
        "intake_captured",
        "classification",
        "decomposition",
        "github_payload",
        "github_issues",
        "github_comments"
    ];

    function normalizeTraceResponse(response) {
        const traceId = typeof response?.traceId === "string" ? response.traceId.trim() : "";
        const events = Array.isArray(response?.events) ? response.events.filter(Boolean).map(normalizeEvent) : [];
        const summary = buildTraceSummary(response);
        const summaryLabel = buildTraceSummaryLabel(response);
        events.sort((left, right) => {
            const leftTime = Date.parse(left.timestamp || "") || 0;
            const rightTime = Date.parse(right.timestamp || "") || 0;
            return leftTime - rightTime;
        });
        return { traceId, events, summary, summaryLabel };
    }

    function normalizeEvent(event) {
        return {
            traceId: toText(event?.traceId),
            sessionId: toText(event?.sessionId),
            correlationId: toText(event?.correlationId),
            eventType: toText(event?.eventType),
            timestamp: toText(event?.timestamp),
            status: toText(event?.status),
            actor: toText(event?.actor),
            summary: toText(event?.summary),
            decisionMetadata: asObject(event?.decisionMetadata),
            inputSummary: asObject(event?.inputSummary),
            artifactSummary: asObject(event?.artifactSummary),
            governanceMetadata: asObject(event?.governanceMetadata)
        };
    }

    function buildTraceSummaryLabel(response) {
        const summary = buildTraceSummary(response);
        if (summary) {
            return `Decision trace summary: ${summary}`;
        }
        return "Decision trace summary unavailable";
    }

    function buildTraceSummary(response) {
        const traceId = typeof response?.traceId === "string" ? response.traceId.trim() : "";
        const events = Array.isArray(response?.events) ? response.events.filter(Boolean) : [];
        const eventCount = events.length;
        const firstEvent = events[0] || {};
        const lastEvent = events[eventCount - 1] || {};
        const parts = [];

        if (traceId) parts.push(`Trace ${traceId}`);
        if (eventCount > 0) parts.push(`${eventCount} event${eventCount === 1 ? "" : "s"}`);
        if (toText(firstEvent.status)) parts.push(`starts ${toText(firstEvent.status)}`);
        if (toText(lastEvent.status) && lastEvent.status !== firstEvent.status) parts.push(`ends ${toText(lastEvent.status)}`);

        return parts.join(" · ");
    }

    function buildCompactTraceSummary(trace) {
        const parts = [];
        const traceId = toText(trace?.traceId);
        const eventType = toText(trace?.eventType);
        const status = toText(trace?.status);
        const actor = toText(trace?.actor);

        if (traceId) parts.push(`Trace ${traceId}`);
        if (eventType) parts.push(eventType);
        if (status) parts.push(status);
        if (actor) parts.push(`by ${actor}`);

        const sourceType = toText(trace?.decisionMetadata?.sourceType);
        const classifiedType = toText(trace?.decisionMetadata?.classifiedType);
        if (sourceType || classifiedType) {
            parts.push([sourceType, classifiedType].filter(Boolean).join(" → "));
        }

        return parts.join(" · ");
    }

    function buildCustomerTimeline(events) {
        const byStep = new Map();
        events.forEach((event) => {
            const stepKey = classifyStep(event.eventType).key;
            if (!stepKey) {
                return;
            }
            byStep.set(stepKey, event);
        });

        return STEP_ORDER
            .map((stepKey) => {
                const event = byStep.get(stepKey);
                if (!event) {
                    return null;
                }
                const step = classifyStep(event.eventType);
                return buildTraceItem(event, step.title, true);
            })
            .filter(Boolean);
    }

    function buildEngineerTimeline(events) {
        return events.map((event) => {
            const step = classifyStep(event.eventType);
            return buildTraceItem(event, step.title, false);
        });
    }

    function buildTraceItem(event, stepTitle, hideDenseDetail) {
        const issueLinks = extractIssueLinks(event);
        const resolvedTitle = resolveStepTitle(stepTitle, event);
        const details = {
            traceId: event.traceId,
            sessionId: event.sessionId,
            correlationId: event.correlationId,
            actor: event.actor,
            decisionMetadata: event.decisionMetadata,
            inputSummary: event.inputSummary,
            artifactSummary: event.artifactSummary,
            governanceMetadata: event.governanceMetadata
        };

        if (hideDenseDetail) {
            return {
                stepTitle: resolvedTitle,
                status: event.status || "recorded",
                summary: event.summary || readableEventType(event.eventType),
                timestamp: event.timestamp,
                details: compactDetails({
                    issueLinks,
                    sourceType: toText(event.decisionMetadata?.sourceType),
                    classifiedType: toText(event.decisionMetadata?.classifiedType),
                    issueCount: optionalCount(event.artifactSummary, "issueCount"),
                    commentedIssueCount: optionalCount(event.artifactSummary, "commentedIssueCount"),
                    failedIssueCount: optionalCount(event.artifactSummary, "failedIssueCount"),
                    unknownFailedIssueCount: optionalCount(event.artifactSummary, "unknownFailedIssueCount")
                })
            };
        }

        return {
            stepTitle: resolvedTitle,
            status: event.status || "recorded",
            summary: event.summary || readableEventType(event.eventType),
            timestamp: event.timestamp,
            details: {
                ...details,
                issueLinks
            }
        };
    }

    function classifyStep(eventType) {
        if (!eventType) {
            return { key: "", title: "Trace event" };
        }
        if (eventType.startsWith("intake.session") || eventType.startsWith("intake.structured-data")) {
            return { key: "intake_captured", title: "Intake captured" };
        }
        if (eventType.startsWith("intake.classification")) {
            return { key: "classification", title: "Classified as bug or feature" };
        }
        if (eventType.startsWith("intake.decomposition")) {
            return { key: "decomposition", title: "Decomposition completed" };
        }
        if (eventType.startsWith("intake.github.payload")) {
            return { key: "github_payload", title: "GitHub payload prepared" };
        }
        if (eventType.startsWith("intake.github.issue-creation")) {
            return { key: "github_issues", title: "GitHub issues created" };
        }
        if (eventType.startsWith("intake.github.summary-comment")) {
            return { key: "github_comments", title: "GitHub summary comment posted" };
        }
        return { key: "", title: readableEventType(eventType) };
    }

    function resolveStepTitle(stepTitle, event) {
        const status = toText(event?.status).toLowerCase();
        if (stepTitle === "Classified as bug or feature" && status === "pending") {
            return "Classification needs clarification";
        }
        if (stepTitle === "GitHub issues created" && status === "failed") {
            return "GitHub issue creation failed";
        }
        if (stepTitle === "GitHub summary comment posted" && status === "failed") {
            return "GitHub summary comment posting had failures";
        }
        return stepTitle;
    }

    function extractIssueLinks(event) {
        const links = event?.artifactSummary?.issueLinks;
        return Array.isArray(links) ? links.filter((link) => typeof link === "string" && link.trim()) : [];
    }

    function compactDetails(details) {
        return Object.entries(details)
            .filter(([, value]) => value !== undefined && value !== null && value !== "" && !(Array.isArray(value) && value.length === 0))
            .map(([key, value]) => `${key}: ${Array.isArray(value) ? value.join(", ") : value}`)
            .join(" · ");
    }

    function optionalCount(object, key) {
        const value = object?.[key];
        if (typeof value === "number" && Number.isFinite(value)) {
            return value;
        }
        if (typeof value === "string" && value.trim() !== "" && !Number.isNaN(Number(value))) {
            return Number(value);
        }
        return null;
    }

    function readableEventType(eventType) {
        return toText(eventType).replace(/\./g, " ");
    }

    function toText(value) {
        return typeof value === "string" ? value.trim() : "";
    }

    function asObject(value) {
        return value && typeof value === "object" && !Array.isArray(value) ? value : {};
    }

    return {
        normalizeTraceResponse,
        buildTraceSummary,
        buildTraceSummaryLabel,
        buildCompactTraceSummary,
        buildCustomerTimeline,
        buildEngineerTimeline
    };
}));
