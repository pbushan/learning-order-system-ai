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
        events.sort((left, right) => {
            const leftTime = Date.parse(left.timestamp || "") || 0;
            const rightTime = Date.parse(right.timestamp || "") || 0;
            return leftTime - rightTime;
        });
        return { traceId, events, summary };
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

    function buildTraceabilitySummaryLabel(response) {
        const normalized = normalizeTraceResponse(response);
        const parts = [];
        if (normalized.traceId) parts.push(`Trace ${normalized.traceId}`);
        if (normalized.events.length === 0) {
            parts.push("No events");
        } else {
            parts.push(`${normalized.events.length} event${normalized.events.length === 1 ? "" : "s"}`);
            const firstStatus = normalized.events[0]?.status;
            const lastStatus = normalized.events[normalized.events.length - 1]?.status;
            if (firstStatus) parts.push(`starts ${firstStatus}`);
            if (lastStatus && lastStatus !== firstStatus) parts.push(`ends ${lastStatus}`);
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
                details: {
                    traceId: event.traceId,
                    sessionId: event.sessionId,
                    correlationId: event.correlationId,
                    issueLinks,
                    decisionMetadata: event.decisionMetadata,
                    inputSummary: event.inputSummary,
                    artifactSummary: event.artifactSummary,
                    governanceMetadata: event.governanceMetadata
                }
            };
        }

        return {
            stepTitle: resolvedTitle,
            status: event.status || "recorded",
            summary: event.summary || readableEventType(event.eventType),
            details: {
                ...details,
                issueLinks
            }
        };
    }

    function classifyStep(eventType) {
        const type = toText(eventType).toLowerCase();
        if (type.includes("session.started") || type.includes("intake.captured")) return { key: "intake_captured", title: "Intake captured" };
        if (type.includes("classification")) return { key: "classification", title: "Classification" };
        if (type.includes("decomposition")) return { key: "decomposition", title: "Decomposition" };
        if (type.includes("github.payload")) return { key: "github_payload", title: "GitHub payload" };
        if (type.includes("issue-creation")) return { key: "github_issues", title: "GitHub issues created" };
        if (type.includes("summary-comment")) return { key: "github_comments", title: "GitHub comments" };
        return { key: "", title: readableEventType(eventType) };
    }

    function resolveStepTitle(stepTitle, event) {
        const status = toText(event?.status).toLowerCase();
        if (!status) return stepTitle;
        if (status === "failed") return `${stepTitle} failed`;
        if (status === "pending") return `${stepTitle} pending`;
        return stepTitle;
    }

    function extractIssueLinks(event) {
        const links = event?.artifactSummary?.issueLinks;
        return Array.isArray(links) ? links.filter((link) => typeof link === "string" && link.trim()) : [];
    }

    function readableEventType(eventType) {
        return toText(eventType)
            .replace(/[._-]+/g, " ")
            .replace(/\s+/g, " ")
            .trim();
    }

    function toText(value) {
        return typeof value === "string" ? value.trim() : "";
    }

    function asObject(value) {
        return value && typeof value === "object" && !Array.isArray(value) ? value : {};
    }

    return {
        normalizeTraceResponse,
        normalizeEvent,
        buildTraceSummary,
        buildCompactTraceSummary,
        buildTraceabilitySummaryLabel,
        buildCustomerTimeline,
        buildEngineerTimeline,
        buildTraceItem,
        classifyStep,
        resolveStepTitle,
        extractIssueLinks,
        readableEventType,
        toText,
        asObject
    };
}));
