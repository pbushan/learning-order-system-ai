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
            summary: toText(event?.summary),
            decisionMetadata: asObject(event?.decisionMetadata),
            inputSummary: asObject(event?.inputSummary),
            artifactSummary: asObject(event?.artifactSummary),
            governanceMetadata: asObject(event?.governanceMetadata)
        };
    }

    function buildTraceSummary(trace) {
        const traceId = typeof trace?.traceId === "string" ? trace.traceId.trim() : "";
        const eventCount = Array.isArray(trace?.events) ? trace.events.filter(Boolean).length : 0;
        return traceId ? `Trace ${traceId} · ${eventCount} events` : "Trace summary unavailable";
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
        return { key: "", title: "Trace event" };
    }

    function resolveStepTitle(stepTitle, event) {
        if (event?.status && String(event.status).toLowerCase() === "failed") {
            if (stepTitle === "GitHub issues created") {
                return "GitHub issue creation failed";
            }
            if (stepTitle === "GitHub summary comment posted") {
                return "GitHub summary comment posting had failures";
            }
            if (stepTitle === "Classified as bug or feature") {
                return "Classification needs clarification";
            }
        }
        return stepTitle;
    }

    function extractIssueLinks(event) {
        const links = event?.artifactSummary?.issueLinks;
        return Array.isArray(links) ? links.filter((link) => typeof link === "string" && link.trim()) : [];
    }

    function compactDetails(details) {
        return Object.fromEntries(Object.entries(details).filter(([, value]) => value !== undefined && value !== null && value !== "" && !(Array.isArray(value) && value.length === 0)));
    }

    function optionalCount(obj, key) {
        const value = obj?.[key];
        if (typeof value === "number" && Number.isFinite(value)) {
            return value;
        }
        if (typeof value === "string" && value.trim() !== "") {
            const parsed = Number(value);
            return Number.isFinite(parsed) ? parsed : undefined;
        }
        return undefined;
    }

    function readableEventType(eventType) {
        return toText(eventType).replaceAll(".", " ").replaceAll("-", " ").trim() || "Trace event";
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
        buildCustomerTimeline,
        buildEngineerTimeline,
        buildTraceItem,
        classifyStep,
        resolveStepTitle,
        extractIssueLinks,
        compactDetails,
        optionalCount,
        readableEventType,
        toText,
        asObject
    };
}));
