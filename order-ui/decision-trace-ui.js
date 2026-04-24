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

    const EMPTY_EVENT_TEXT = "No event text provided";

    function normalizeTraceResponse(response) {
        const traceId = typeof response?.traceId === "string" ? response.traceId.trim() : "";
        const events = Array.isArray(response?.events) ? response.events.filter(Boolean).map(normalizeEvent) : [];
        events.sort((left, right) => {
            const leftTime = Date.parse(left.timestamp || "") || 0;
            const rightTime = Date.parse(right.timestamp || "") || 0;
            return leftTime - rightTime;
        });
        return { traceId, events };
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
            summary: toEventText(event?.summary),
            decisionMetadata: asObject(event?.decisionMetadata),
            inputSummary: asObject(event?.inputSummary),
            artifactSummary: asObject(event?.artifactSummary),
            governanceMetadata: asObject(event?.governanceMetadata)
        };
    }

    function toEventText(value) {
        const text = toText(value);
        return text || EMPTY_EVENT_TEXT;
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
        if (event?.status && String(event.status).toLowerCase() === "failed") {
            return `${stepTitle} failed`;
        }
        if (event?.status && String(event.status).toLowerCase() === "pending") {
            return `${stepTitle} needs clarification`;
        }
        return stepTitle;
    }

    function readableEventType(eventType) {
        const text = toText(eventType);
        if (!text) {
            return "Trace event";
        }
        return text.replace(/[._-]+/g, " ").replace(/\b\w/g, (match) => match.toUpperCase());
    }

    function compactDetails(details) {
        return Object.fromEntries(Object.entries(details).filter(([, value]) => value !== undefined && value !== null && value !== ""));
    }

    function optionalCount(source, key) {
        const value = source?.[key];
        if (value === undefined || value === null || value === "") {
            return undefined;
        }
        const parsed = Number(value);
        return Number.isFinite(parsed) ? parsed : undefined;
    }

    function extractIssueLinks(event) {
        const links = event?.artifactSummary?.issueLinks;
        return Array.isArray(links) ? links.filter((link) => typeof link === "string" && link.trim()) : [];
    }

    function toText(value) {
        return typeof value === "string" ? value.trim() : "";
    }

    function asObject(value) {
        return value && typeof value === "object" && !Array.isArray(value) ? value : {};
    }

    return {
        normalizeTraceResponse,
        buildCustomerTimeline,
        buildEngineerTimeline,
        classifyStep,
        resolveStepTitle,
        readableEventType,
        compactDetails,
        optionalCount,
        extractIssueLinks,
        normalizeEvent,
        toText,
        toEventText,
        EMPTY_EVENT_TEXT
    };
}));
