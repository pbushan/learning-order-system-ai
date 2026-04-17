const state = {
    customers: [],
    orders: [],
    products: [],
    intakeMessages: [],
    intakeLoading: false,
    lastIntakeSentAt: 0,
    intakeResult: null,
    intakeTraceId: "",
    decompositionLoading: false,
    githubIssueCreationError: "",
    githubIssueCreationResult: null,
    decisionTrace: {
        traceId: "",
        events: [],
        loading: false,
        error: "",
        mode: "customer"
    },
    decompositionElementsWarningShown: false
};
const MAX_INTAKE_MESSAGES = 30;
const INTAKE_REQUEST_TIMEOUT_MS = 15000;
const INTAKE_ORCHESTRATION_TIMEOUT_MS = 45000;
const MIN_INTAKE_SEND_INTERVAL_MS = 800;

const customerForm = document.getElementById("customer-form");
const orderForm = document.getElementById("order-form");
const productForm = document.getElementById("product-form");
const customerTableBody = document.getElementById("customer-table-body");
const orderTableBody = document.getElementById("order-table-body");
const productTableBody = document.getElementById("product-table-body");
const customerSelect = document.getElementById("order-customer-id");
const orderProductSelect = document.getElementById("order-product-id");
const activityStack = document.getElementById("activity-stack");
const metricGrid = document.getElementById("metric-grid");
const banner = document.getElementById("feedback-banner");
const refreshButton = document.getElementById("refresh-button");
const apiStatusDot = document.getElementById("api-status-dot");
const apiStatusText = document.getElementById("api-status-text");
const intakeChatForm = document.getElementById("intake-chat-form");
const intakeChatHistory = document.getElementById("intake-chat-history");
const intakeChatInput = document.getElementById("intake-chat-input");
const intakeChatSend = document.getElementById("intake-chat-send");
const intakeChatLoading = document.getElementById("intake-chat-loading");
const intakeWorkflowStatus = document.getElementById("intake-workflow-status");
const intakeDecomposeLoading = document.getElementById("intake-decompose-loading");
const intakeDecomposition = document.getElementById("intake-decomposition");
const intakeDecompositionList = document.getElementById("intake-decomposition-list");
const decisionTraceSection = document.getElementById("decision-trace-section");
const decisionTraceMeta = document.getElementById("decision-trace-meta");
const decisionTraceList = document.getElementById("decision-trace-list");
const decisionTraceModeButtons = Array.from(document.querySelectorAll("[data-trace-mode]"));
let tabButtons = [];
let tabPanels = [];

document.addEventListener("DOMContentLoaded", () => {
    customerForm.addEventListener("submit", handleCustomerSubmit);
    orderForm.addEventListener("submit", handleOrderSubmit);
    productForm.addEventListener("submit", handleProductSubmit);
    document.getElementById("customer-reset").addEventListener("click", resetCustomerForm);
    document.getElementById("order-reset").addEventListener("click", resetOrderForm);
    document.getElementById("product-reset").addEventListener("click", resetProductForm);
    refreshButton.addEventListener("click", () => loadDashboard(true));
    customerTableBody.addEventListener("click", handleCustomerTableClick);
    orderTableBody.addEventListener("click", handleOrderTableClick);
    productTableBody.addEventListener("click", handleProductTableClick);
    if (intakeChatForm) {
        intakeChatForm.addEventListener("submit", handleIntakeChatSubmit);
    }
    if (intakeChatInput) {
        intakeChatInput.addEventListener("keydown", handleIntakeInputKeydown);
    }
    setIntakeChatLoading(false);
    setDecompositionLoading(false);
    renderIntakeChatHistory();
    renderDecompositionUI();
    renderDecisionTrace();
    initializeTabs();
    initializeDecisionTraceMode();

    loadDashboard();
    window.setInterval(() => loadDashboard(false, true), 15000);
});

function initializeTabs() {
    const discoveredButtons = Array.from(document.querySelectorAll("[data-tab-target]"));
    const discoveredPanels = Array.from(document.querySelectorAll("[data-tab-panel]"));
    const panelsById = new Map(discoveredPanels.map((panel) => [panel.id, panel]));

    tabButtons = discoveredButtons.filter((button) => {
        const panel = panelsById.get(button.getAttribute("aria-controls"));
        return panel?.dataset.tabPanel === button.dataset.tabTarget;
    });
    tabPanels = discoveredPanels.filter((panel) => (
        tabButtons.some((button) => button.dataset.tabTarget === panel.dataset.tabPanel)
    ));

    if (!tabButtons.length || !tabPanels.length) {
        discoveredPanels.forEach((panel) => {
            panel.hidden = false;
        });
        console.warn("Workspace tabs are disabled because tab buttons and panels did not match.");
        return;
    }

    tabButtons.forEach((button) => {
        const panel = panelsById.get(button.getAttribute("aria-controls"));
        panel?.setAttribute("aria-labelledby", button.id);
        button.addEventListener("click", () => activateTab(button.dataset.tabTarget));
        button.addEventListener("keydown", handleTabKeydown);
    });
    window.addEventListener("hashchange", () => activateTab(getTabFromHash(), false));

    activateTab(getTabFromHash(), true);
}

function getTabFromHash() {
    const requestedTab = window.location.hash.replace("#", "");
    return isAvailableTab(requestedTab)
        ? requestedTab
        : "customers";
}

function activateTab(tabName, updateHash = true) {
    if (!tabButtons.length || !tabPanels.length) {
        return;
    }

    const nextTabName = isAvailableTab(tabName) ? tabName : "customers";

    tabButtons.forEach((button) => {
        const isActive = button.dataset.tabTarget === nextTabName;
        button.classList.toggle("active", isActive);
        button.setAttribute("aria-selected", String(isActive));
        button.tabIndex = isActive ? 0 : -1;
    });

    tabPanels.forEach((panel) => {
        panel.hidden = panel.dataset.tabPanel !== nextTabName;
    });

    if (updateHash && window.location.hash !== `#${nextTabName}`) {
        window.history.replaceState(null, "", `#${nextTabName}`);
    }
}

function isAvailableTab(tabName) {
    return tabButtons.length > 0
        && tabPanels.length > 0
        && tabButtons.some((button) => button.dataset.tabTarget === tabName)
        && tabPanels.some((panel) => panel.dataset.tabPanel === tabName);
}

function handleTabKeydown(event) {
    if (["Enter", " ", "Spacebar"].includes(event.key)) {
        event.preventDefault();
        activateTab(event.currentTarget.dataset.tabTarget);
        return;
    }

    if (!["ArrowLeft", "ArrowRight", "Home", "End"].includes(event.key)) {
        return;
    }

    event.preventDefault();
    const currentIndex = tabButtons.findIndex((button) => button === event.currentTarget);
    if (currentIndex < 0) {
        return;
    }

    const lastIndex = tabButtons.length - 1;
    let nextIndex = currentIndex;

    if (event.key === "ArrowLeft") {
        nextIndex = currentIndex === 0 ? lastIndex : currentIndex - 1;
    } else if (event.key === "ArrowRight") {
        nextIndex = currentIndex === lastIndex ? 0 : currentIndex + 1;
    } else if (event.key === "Home") {
        nextIndex = 0;
    } else if (event.key === "End") {
        nextIndex = lastIndex;
    }

    const nextButton = tabButtons[nextIndex];
    if (!nextButton) {
        return;
    }

    activateTab(nextButton.dataset.tabTarget);
    nextButton.focus();
}

async function apiRequest(path, options = {}) {
    const response = await fetch(path, {
        headers: {
            "Content-Type": "application/json",
            ...(options.headers || {})
        },
        ...options
    });

    if (response.status === 204) {
        return null;
    }

    const text = await response.text();
    const rawText = typeof text === "string" ? text.trim() : "";
    let data = null;
    if (text) {
        try {
            data = JSON.parse(text);
        } catch (error) {
            data = null;
        }
    }

    if (!response.ok) {
        const headerMessage = response.headers.get("X-Error-Message");
        const message = data?.error || headerMessage || rawText || `Request failed with status ${response.status}`;
        throw new Error(message);
    }

    return data;
}

async function loadDashboard(showSuccessMessage = false, silent = false) {
    try {
        const [customers, orders, products] = await Promise.all([
            apiRequest("/api/customers"),
            apiRequest("/api/orders"),
            apiRequest("/api/products")
        ]);

        state.customers = customers;
        state.orders = orders;
        state.products = products;

        updateConnectionStatus(true);
        renderCustomers();
        renderOrders();
        renderProducts();
        renderCustomerOptions();
        renderProductOptions();
        renderMetrics();
        renderActivity();

        if (showSuccessMessage) {
            showBanner("Dashboard refreshed.", "success");
        } else if (silent) {
            hideBanner();
        }
    } catch (error) {
        updateConnectionStatus(false, error.message);
        if (!silent) {
            showBanner(error.message, "error");
        }
    }
}

async function handleCustomerSubmit(event) {
    event.preventDefault();

    const id = document.getElementById("customer-id").value;
    const line2 = document.getElementById("customer-address-line2").value.trim();
    const payload = {
        name: {
            firstName: document.getElementById("customer-first-name").value.trim(),
            lastName: document.getElementById("customer-last-name").value.trim()
        },
        email: document.getElementById("customer-email").value.trim(),
        phone: document.getElementById("customer-phone").value.trim(),
        addresses: [
            {
                type: document.getElementById("customer-address-type").value,
                line1: document.getElementById("customer-address-line1").value.trim(),
                line2: line2 || null,
                city: document.getElementById("customer-city").value.trim(),
                state: document.getElementById("customer-state").value.trim(),
                postalCode: document.getElementById("customer-postal-code").value.trim(),
                country: document.getElementById("customer-country").value.trim().toUpperCase(),
                isDefault: true
            }
        ]
    };

    try {
        await apiRequest(id ? `/api/customers/${id}` : "/api/customers", {
            method: id ? "PUT" : "POST",
            body: JSON.stringify(payload)
        });

        showBanner(id ? "Customer updated." : "Customer created.", "success");
        resetCustomerForm();
        await loadDashboard();
    } catch (error) {
        showBanner(error.message, "error");
    }
}

async function handleOrderSubmit(event) {
    event.preventDefault();

    const id = document.getElementById("order-id").value;
    const payload = {
        customerId: Number(document.getElementById("order-customer-id").value),
        productId: Number(document.getElementById("order-product-id").value),
        quantity: Number(document.getElementById("order-quantity").value),
        totalAmount: Number(document.getElementById("order-total-amount").value).toFixed(2)
    };

    try {
        await apiRequest(id ? `/api/orders/${id}` : "/api/orders", {
            method: id ? "PUT" : "POST",
            body: JSON.stringify(payload)
        });

        showBanner(id ? "Order updated." : "Order created.", "success");
        resetOrderForm();
        await loadDashboard();
    } catch (error) {
        showBanner(error.message, "error");
    }
}

async function handleIntakeChatSubmit(event) {
    event.preventDefault();
    if (!intakeChatInput || !intakeChatSend || !intakeChatHistory) {
        showBanner("Intake chat is unavailable right now.", "error");
        return;
    }
    if (state.intakeLoading) {
        return;
    }
    if (Date.now() - state.lastIntakeSentAt < MIN_INTAKE_SEND_INTERVAL_MS) {
        showBanner("Please wait a moment before sending another intake message.", "error");
        return;
    }

    const content = intakeChatInput.value.trim();
    if (!content) {
        return;
    }

    state.intakeResult = null;
    state.intakeTraceId = "";
    state.githubIssueCreationError = "";
    state.githubIssueCreationResult = null;
    clearDecisionTrace();
    state.intakeMessages.push({ role: "user", content });
    trimIntakeMessages();
    intakeChatInput.value = "";
    renderIntakeChatHistory();
    renderDecompositionUI();
    setIntakeChatLoading(true);
    state.lastIntakeSentAt = Date.now();

    try {
        const response = await apiRequestWithTimeout("/api/intake/chat", {
            method: "POST",
            body: JSON.stringify({
                traceId: state.intakeTraceId || "",
                messages: state.intakeMessages.map((message) => ({
                    role: message.role,
                    content: message.content
                }))
            })
        }, INTAKE_REQUEST_TIMEOUT_MS);
        if (!response || typeof response.reply !== "string" || !response.reply.trim()) {
            throw new Error("Invalid intake response");
        }
        const reply = response.reply.trim();
        state.intakeMessages.push({ role: "assistant", content: reply });
        updateIntakeResult(response);
        await loadDecisionTrace();
        if (hasCompleteIntakeResult(state.intakeResult)) {
            await runAutomatedIntakeFlow(state.intakeResult);
        } else if (response?.intakeComplete === true) {
            state.githubIssueCreationResult = {
                requestId: typeof response?.requestId === "string" ? response.requestId.trim() : "",
                traceId: typeof response?.traceId === "string" ? response.traceId.trim() : state.intakeTraceId,
                issuesCreated: false,
                issues: [],
                note: "Intake completed without an actionable bug/feature request. No GitHub issues were created."
            };
            state.githubIssueCreationError = "";
        }
        trimIntakeMessages();
    } catch (error) {
        const fallbackMessage = resolveIntakeFallbackMessage(error);
        console.error("Intake chat request failed", error);
        showBanner(fallbackMessage, "error");
        state.intakeMessages.push({
            role: "assistant",
            content: fallbackMessage
        });
        state.intakeResult = null;
        trimIntakeMessages();
    } finally {
        setIntakeChatLoading(false);
        renderIntakeChatHistory();
        renderDecompositionUI();
    }
}

async function apiRequestWithTimeout(path, options, timeoutMs) {
    const controller = new AbortController();
    const timeoutId = window.setTimeout(() => controller.abort(), timeoutMs);
    try {
        return await apiRequest(path, {
            ...options,
            signal: controller.signal
        });
    } catch (error) {
        if (error?.name === "AbortError") {
            throw new Error("Intake request timed out");
        }
        throw error;
    } finally {
        window.clearTimeout(timeoutId);
    }
}

function resolveIntakeFallbackMessage(error) {
    if (error?.message === "Invalid intake response") {
        return "Intake service returned an unexpected response. Please try again.";
    }
    if (error?.message === "Intake request timed out") {
        return "Intake service is taking too long to respond. Please try again.";
    }
    return "I could not reach intake service right now. Please try again shortly.";
}

function updateIntakeResult(response) {
    const requestId = typeof response?.requestId === "string" ? response.requestId.trim() : "";
    const structuredData = response?.structuredData && typeof response.structuredData === "object"
        ? response.structuredData
        : null;
    const intakeComplete = response?.intakeComplete === true;
    const traceId = typeof response?.traceId === "string" ? response.traceId.trim() : "";
    state.intakeTraceId = traceId || state.intakeTraceId;
    if (requestId && intakeComplete && structuredData) {
        state.intakeResult = { requestId, traceId: state.intakeTraceId, structuredData, intakeComplete: true };
        return;
    }
    state.intakeResult = null;
}

function hasCompleteIntakeResult(result) {
    return result?.intakeComplete
        && typeof result.requestId === "string"
        && result.requestId.length > 0
        && isActionableStructuredData(result.structuredData);
}

function isActionableStructuredData(structuredData) {
    if (!structuredData || typeof structuredData !== "object") {
        return false;
    }
    const type = typeof structuredData.type === "string" ? structuredData.type.trim().toLowerCase() : "";
    const title = typeof structuredData.title === "string" ? structuredData.title.trim() : "";
    const description = typeof structuredData.description === "string" ? structuredData.description.trim() : "";
    return (type === "bug" || type === "feature") && title.length > 0 && description.length > 0;
}

async function runAutomatedIntakeFlow(intakeResult) {
    setDecompositionLoading(true);
    state.githubIssueCreationError = "";
    state.githubIssueCreationResult = null;
    renderDecompositionUI();
    try {
        const response = await apiRequestWithTimeout("/api/intake/complete-to-github", {
            method: "POST",
            body: JSON.stringify({
                requestId: intakeResult.requestId,
                traceId: intakeResult.traceId || state.intakeTraceId || "",
                structuredData: intakeResult.structuredData
            })
        }, INTAKE_ORCHESTRATION_TIMEOUT_MS);
        if (!response || !Array.isArray(response.issues)) {
            throw new Error("Invalid GitHub issue creation response");
        }
        if (response.issuesCreated !== true || response.issues.length === 0) {
            throw new Error("GitHub issue creation did not complete successfully.");
        }
        state.githubIssueCreationResult = {
            requestId: response.requestId || intakeResult.requestId,
            traceId: response.traceId || intakeResult.traceId || state.intakeTraceId || "",
            issuesCreated: true,
            issues: response.issues
        };
    } catch (error) {
        console.error("Automated intake flow failed", error);
        const message = resolveGithubIssueCreationFallbackMessage(error);
        state.githubIssueCreationError = message;
        state.githubIssueCreationResult = null;
    } finally {
        setDecompositionLoading(false);
        await loadDecisionTrace();
        renderDecompositionUI();
    }
}

function resolveGithubIssueCreationFallbackMessage(error) {
    if (error?.message === "Invalid GitHub issue creation response") {
        return "GitHub issue creation returned an unexpected response. Please try again.";
    }
    if (error?.message === "GitHub issue creation did not complete successfully.") {
        return error.message;
    }
    if (typeof error?.message === "string" && (
        error.message.includes("structuredData.title is required")
        || error.message.includes("structuredData.description is required")
        || error.message.includes("structuredData.type is required")
    )) {
        return "Intake completed without actionable bug/feature details, so no GitHub issues were created.";
    }
    if (error?.message === "Intake request timed out") {
        return "GitHub issue creation timed out. Please try again.";
    }
    if (typeof error?.message === "string" && error.message.trim()) {
        return error.message.trim();
    }
    return "I could not create GitHub issues right now. Please try again shortly.";
}

function renderIntakeChatHistory() {
    if (!intakeChatHistory) {
        return;
    }

    if (!state.intakeMessages.length) {
        replaceElementChildren(intakeChatHistory, [createIntakeMessageElement("assistant", "Hi, tell me about your bug or feature request.")]);
        return;
    }

    const messageNodes = state.intakeMessages.map((message) => (
        createIntakeMessageElement(message.role, message.content)
    ));
    replaceElementChildren(intakeChatHistory, messageNodes);
    intakeChatHistory.scrollTop = intakeChatHistory.scrollHeight;
}

function createIntakeMessageElement(role, content) {
    const article = document.createElement("article");
    article.className = `intake-chat-message ${role === "user" ? "user" : "assistant"}`;
    const text = document.createElement("p");
    text.textContent = content;
    article.appendChild(text);
    return article;
}

function replaceElementChildren(element, nodes) {
    while (element.firstChild) {
        element.removeChild(element.firstChild);
    }
    nodes.forEach((node) => {
        element.appendChild(node);
    });
}

function setIntakeChatLoading(isLoading) {
    state.intakeLoading = isLoading;
    if (intakeChatInput) {
        intakeChatInput.disabled = isLoading;
    }
    if (intakeChatSend) {
        intakeChatSend.disabled = isLoading;
    }
    if (intakeChatLoading) {
        intakeChatLoading.classList.toggle("hidden", !isLoading);
    }
}

function setDecompositionLoading(isLoading) {
    state.decompositionLoading = isLoading;
    if (intakeDecomposeLoading) {
        intakeDecomposeLoading.classList.toggle("hidden", !isLoading);
    }
    if (intakeWorkflowStatus) {
        intakeWorkflowStatus.classList.toggle("hidden", !isLoading);
    }
}

function renderDecompositionUI() {
    if ((!intakeDecomposition || !intakeDecompositionList) && !state.decompositionElementsWarningShown) {
        console.warn("Intake result UI elements are missing. Check intake decomposition element IDs.");
        state.decompositionElementsWarningShown = true;
    }
    if (intakeDecomposition) {
        intakeDecomposition.classList.toggle(
            "hidden",
            !state.decompositionLoading && !state.githubIssueCreationResult && !state.githubIssueCreationError
        );
    }
    if (!intakeDecompositionList) {
        return;
    }

    while (intakeDecompositionList.firstChild) {
        intakeDecompositionList.removeChild(intakeDecompositionList.firstChild);
    }

    if (state.decompositionLoading) {
        const processing = document.createElement("p");
        processing.className = "intake-decomposition-empty";
        processing.textContent = "Intake completed. Creating GitHub issue(s) from decomposed stories...";
        intakeDecompositionList.appendChild(processing);
        return;
    }

    if (!state.githubIssueCreationResult) {
        if (state.githubIssueCreationError) {
            const failure = document.createElement("p");
            failure.className = "intake-decomposition-empty";
            failure.textContent = `GitHub issue creation failed: ${state.githubIssueCreationError}`;
            replaceElementChildren(intakeDecompositionList, [failure]);
        }
        return;
    }
    if (!Array.isArray(state.githubIssueCreationResult.issues) || !state.githubIssueCreationResult.issues.length) {
        const empty = document.createElement("p");
        empty.className = "intake-decomposition-empty";
        empty.textContent = "Intake completed, but no GitHub issues were created.";
        intakeDecompositionList.appendChild(empty);
    }
    intakeDecompositionList.appendChild(createGithubIssueResultsElement(state.githubIssueCreationResult));
}

function createGithubIssueResultsElement(result) {
    const wrapper = document.createElement("section");
    wrapper.className = "intake-github-results";

    const heading = document.createElement("h4");
    heading.textContent = "Created GitHub Issues";
    wrapper.appendChild(heading);

    const issues = Array.isArray(result?.issues) ? result.issues : [];
    if (!issues.length) {
        const empty = document.createElement("p");
        empty.className = "intake-decomposition-empty";
        empty.textContent = (typeof result?.note === "string" && result.note.trim())
            ? result.note.trim()
            : "No GitHub issues created yet.";
        wrapper.appendChild(empty);
    } else {
        issues.forEach((issue) => {
            const item = document.createElement("article");
            item.className = "intake-github-issue";

            const title = document.createElement("p");
            title.className = "intake-story-meta";
            title.textContent = issue?.title || "Untitled issue";
            item.appendChild(title);

            const number = document.createElement("p");
            number.textContent = `Issue #${issue?.issueNumber ?? "n/a"}`;
            item.appendChild(number);

            const link = document.createElement("a");
            link.href = issue?.issueUrl || "#";
            link.target = "_blank";
            link.rel = "noopener noreferrer";
            link.textContent = issue?.issueUrl || "n/a";
            item.appendChild(link);

            const labels = Array.isArray(issue?.labels) ? issue.labels.filter((entry) => typeof entry === "string" && entry.trim()) : [];
            const labelsLine = document.createElement("p");
            labelsLine.textContent = `Labels: ${labels.length ? labels.join(", ") : "n/a"}`;
            item.appendChild(labelsLine);

            wrapper.appendChild(item);
        });
    }

    const note = document.createElement("p");
    note.className = "intake-github-note";
    note.textContent = "Note: These issues require explicit human approval before future automated development.";
    wrapper.appendChild(note);

    return wrapper;
}

function initializeDecisionTraceMode() {
    if (!decisionTraceModeButtons.length) {
        return;
    }
    decisionTraceModeButtons.forEach((button) => {
        button.addEventListener("click", () => {
            const nextMode = button.dataset.traceMode === "engineer" ? "engineer" : "customer";
            state.decisionTrace.mode = nextMode;
            renderDecisionTrace();
        });
    });
}

function clearDecisionTrace() {
    state.decisionTrace.traceId = "";
    state.decisionTrace.events = [];
    state.decisionTrace.error = "";
    state.decisionTrace.loading = false;
}

async function loadDecisionTrace() {
    const traceId = state.githubIssueCreationResult?.traceId || state.intakeResult?.traceId || state.intakeTraceId;
    if (!traceId) {
        renderDecisionTrace();
        return;
    }
    state.decisionTrace.traceId = traceId;
    state.decisionTrace.loading = true;
    state.decisionTrace.error = "";
    renderDecisionTrace();
    try {
        const response = await apiRequest(`/api/intake/trace/${encodeURIComponent(traceId)}`);
        const normalized = window.DecisionTraceUi?.normalizeTraceResponse
            ? window.DecisionTraceUi.normalizeTraceResponse(response || {})
            : { traceId, events: Array.isArray(response?.events) ? response.events : [] };
        state.decisionTrace.traceId = normalized.traceId || traceId;
        state.decisionTrace.events = Array.isArray(normalized.events) ? normalized.events : [];
        state.decisionTrace.error = "";
    } catch (error) {
        state.decisionTrace.error = "Decision trace is unavailable right now.";
    } finally {
        state.decisionTrace.loading = false;
        renderDecisionTrace();
    }
}

function renderDecisionTrace() {
    if (!decisionTraceSection || !decisionTraceList) {
        return;
    }
    const hasTraceContext = state.decisionTrace.loading
        || !!state.decisionTrace.traceId
        || (Array.isArray(state.decisionTrace.events) && state.decisionTrace.events.length > 0)
        || !!state.decisionTrace.error;
    decisionTraceSection.classList.toggle("hidden", !hasTraceContext);
    decisionTraceModeButtons.forEach((button) => {
        const isActive = button.dataset.traceMode === state.decisionTrace.mode;
        button.classList.toggle("active", isActive);
    });

    if (decisionTraceMeta) {
        const showMeta = state.decisionTrace.mode === "engineer" && !!state.decisionTrace.traceId;
        decisionTraceMeta.classList.toggle("hidden", !showMeta);
        decisionTraceMeta.textContent = showMeta ? `Trace ID: ${state.decisionTrace.traceId}` : "";
    }

    const nodes = [];
    if (state.decisionTrace.loading) {
        const loading = document.createElement("p");
        loading.className = "intake-decomposition-empty";
        loading.textContent = "Loading decision trace...";
        nodes.push(loading);
        replaceElementChildren(decisionTraceList, nodes);
        return;
    }
    if (state.decisionTrace.error) {
        const error = document.createElement("p");
        error.className = "intake-decomposition-empty";
        error.textContent = state.decisionTrace.error;
        nodes.push(error);
        replaceElementChildren(decisionTraceList, nodes);
        return;
    }

    const traceEvents = Array.isArray(state.decisionTrace.events) ? state.decisionTrace.events : [];
    const timeline = state.decisionTrace.mode === "engineer"
        ? (window.DecisionTraceUi?.buildEngineerTimeline ? window.DecisionTraceUi.buildEngineerTimeline(traceEvents) : [])
        : (window.DecisionTraceUi?.buildCustomerTimeline ? window.DecisionTraceUi.buildCustomerTimeline(traceEvents) : []);
    if (!timeline.length) {
        const empty = document.createElement("p");
        empty.className = "intake-decomposition-empty";
        empty.textContent = "No decision trace events are available yet.";
        nodes.push(empty);
        replaceElementChildren(decisionTraceList, nodes);
        return;
    }

    timeline.forEach((item) => {
        nodes.push(createDecisionTraceItem(item));
    });
    replaceElementChildren(decisionTraceList, nodes);
}

function createDecisionTraceItem(item) {
    const article = document.createElement("article");
    article.className = "decision-trace-item";

    const header = document.createElement("div");
    header.className = "decision-trace-item-header";

    const step = document.createElement("p");
    step.className = "decision-trace-step";
    step.textContent = item.stepTitle || "Trace event";
    header.appendChild(step);

    const status = document.createElement("span");
    status.className = `decision-trace-status ${resolveTraceStatusClass(item.status)}`;
    status.textContent = item.status || "recorded";
    header.appendChild(status);

    article.appendChild(header);

    const summary = document.createElement("p");
    summary.className = "decision-trace-summary";
    summary.textContent = item.summary || "";
    article.appendChild(summary);

    if (item.timestamp) {
        const ts = document.createElement("p");
        ts.className = "decision-trace-time";
        ts.textContent = window.DecisionTraceUi?.formatTimestamp
            ? window.DecisionTraceUi.formatTimestamp(item.timestamp)
            : item.timestamp;
        article.appendChild(ts);
    }

    const details = document.createElement("details");
    details.className = "decision-trace-details";
    const detailsSummary = document.createElement("summary");
    detailsSummary.textContent = state.decisionTrace.mode === "engineer" ? "Expand details" : "View context";
    details.appendChild(detailsSummary);

    const pre = document.createElement("pre");
    pre.textContent = JSON.stringify(item.details || {}, null, 2);
    details.appendChild(pre);

    const links = Array.isArray(item?.details?.issueLinks) ? item.details.issueLinks : [];
    const fallbackLinks = Array.isArray(state.githubIssueCreationResult?.issues)
        ? state.githubIssueCreationResult.issues.map((issue) => issue?.issueUrl).filter((url) => typeof url === "string" && url.trim())
        : [];
    const mergedLinks = [...new Set([...links, ...fallbackLinks])];
    if (mergedLinks.length) {
        const linksLabel = document.createElement("p");
        linksLabel.className = "decision-trace-links-label";
        linksLabel.textContent = "Issue links:";
        details.appendChild(linksLabel);
        const linksList = document.createElement("ul");
        linksList.className = "decision-trace-links";
        mergedLinks.forEach((url) => {
            const li = document.createElement("li");
            const anchor = document.createElement("a");
            anchor.href = url;
            anchor.target = "_blank";
            anchor.rel = "noopener noreferrer";
            anchor.textContent = url;
            li.appendChild(anchor);
            linksList.appendChild(li);
        });
        details.appendChild(linksList);
    }

    article.appendChild(details);
    return article;
}

function resolveTraceStatusClass(status) {
    const normalized = typeof status === "string" ? status.trim().toLowerCase() : "";
    if (normalized === "completed" || normalized === "accepted" || normalized === "approved" || normalized === "recorded") {
        return "is-success";
    }
    if (normalized === "failed" || normalized === "rejected") {
        return "is-failure";
    }
    return "is-pending";
}

function trimIntakeMessages() {
    if (!Array.isArray(state.intakeMessages) || state.intakeMessages.length <= MAX_INTAKE_MESSAGES) {
        return;
    }
    state.intakeMessages = state.intakeMessages.slice(-MAX_INTAKE_MESSAGES);
}

function handleIntakeInputKeydown(event) {
    if (event.key !== "Enter" || event.shiftKey) {
        return;
    }
    event.preventDefault();
    if (!intakeChatForm || state.intakeLoading) {
        return;
    }
    intakeChatForm.requestSubmit();
}

async function handleCustomerTableClick(event) {
    const button = event.target.closest("button[data-action]");
    if (!button) {
        return;
    }

    const customerId = Number(button.dataset.id);
    const customer = state.customers.find((entry) => entry.id === customerId);

    if (!customer) {
        return;
    }

    if (button.dataset.action === "edit") {
        document.getElementById("customer-id").value = customer.id;
        const primaryAddress = getDefaultAddress(customer);
        document.getElementById("customer-first-name").value = customer.name?.firstName || "";
        document.getElementById("customer-last-name").value = customer.name?.lastName || "";
        document.getElementById("customer-email").value = customer.email;
        document.getElementById("customer-phone").value = customer.phone || "";
        document.getElementById("customer-address-type").value = primaryAddress?.type || "SHIPPING";
        document.getElementById("customer-address-line1").value = primaryAddress?.line1 || "";
        document.getElementById("customer-address-line2").value = primaryAddress?.line2 || "";
        document.getElementById("customer-city").value = primaryAddress?.city || "";
        document.getElementById("customer-state").value = primaryAddress?.state || "";
        document.getElementById("customer-postal-code").value = primaryAddress?.postalCode || "";
        document.getElementById("customer-country").value = primaryAddress?.country || "";
        document.getElementById("customer-submit").textContent = "Update customer";
        customerForm.scrollIntoView({ behavior: "smooth", block: "start" });
        return;
    }

    if (!window.confirm(`Delete customer "${getCustomerDisplayName(customer)}"?`)) {
        return;
    }

    try {
        await apiRequest(`/api/customers/${customer.id}`, { method: "DELETE" });
        showBanner("Customer deleted.", "success");
        resetCustomerForm();
        await loadDashboard();
    } catch (error) {
        showBanner(error.message, "error");
    }
}

async function handleOrderTableClick(event) {
    const button = event.target.closest("button[data-action]");
    if (!button) {
        return;
    }

    const orderId = Number(button.dataset.id);
    const order = state.orders.find((entry) => entry.id === orderId);

    if (!order) {
        return;
    }

    if (button.dataset.action === "edit") {
        document.getElementById("order-id").value = order.id;
        document.getElementById("order-customer-id").value = order.customerId;
        document.getElementById("order-product-id").value = order.productId;
        document.getElementById("order-quantity").value = order.quantity;
        document.getElementById("order-total-amount").value = order.totalAmount;
        document.getElementById("order-submit").textContent = "Update order";
        orderForm.scrollIntoView({ behavior: "smooth", block: "start" });
        return;
    }

    if (button.dataset.action === "submit") {
        try {
            await apiRequest(`/api/orders/${order.id}/submit`, { method: "POST" });
            showBanner(`Order ${order.id} submitted to Lambda and RabbitMQ.`, "success");
            await loadDashboard();
        } catch (error) {
            showBanner(error.message, "error");
        }
        return;
    }

    if (!window.confirm(`Delete order #${order.id}?`)) {
        return;
    }

    try {
        await apiRequest(`/api/orders/${order.id}`, { method: "DELETE" });
        showBanner("Order deleted.", "success");
        resetOrderForm();
        await loadDashboard();
    } catch (error) {
        showBanner(error.message, "error");
    }
}

function renderCustomers() {
    if (!state.customers.length) {
        customerTableBody.innerHTML = `<tr><td colspan="5" class="empty-state">No customers yet. Create one to get started.</td></tr>`;
        return;
    }

    customerTableBody.innerHTML = state.customers.map((customer) => `
        <tr>
            <td>${customer.id}</td>
            <td>${escapeHtml(getCustomerDisplayName(customer))}</td>
            <td>${escapeHtml(customer.email)}</td>
            <td>${escapeHtml(customer.phone || "N/A")}</td>
            <td>
                <div class="actions-row">
                    <button class="inline-action" data-action="edit" data-id="${customer.id}" type="button">Edit</button>
                    <button class="inline-action danger" data-action="delete" data-id="${customer.id}" type="button">Delete</button>
                </div>
            </td>
        </tr>
    `).join("");
}

function renderOrders() {
    if (!state.orders.length) {
        orderTableBody.innerHTML = `<tr><td colspan="6" class="empty-state">No orders yet. Draft one once a customer exists.</td></tr>`;
        return;
    }

    orderTableBody.innerHTML = state.orders.map((order) => {
        const customer = state.customers.find((entry) => entry.id === order.customerId);
        const customerName = customer ? getCustomerDisplayName(customer) : `Customer ${order.customerId}`;
        const shipping = order.shippingType
            ? `${escapeHtml(order.shippingType)} · ${order.estimatedDeliveryDays} day${order.estimatedDeliveryDays === 1 ? "" : "s"}`
            : "Pending submit";
        const skuLabel = order.productSku ? ` · SKU ${escapeHtml(order.productSku)}` : "";

        return `
            <tr>
                <td>#${order.id}</td>
                <td>${escapeHtml(customerName)}</td>
                <td>
                    <strong>${escapeHtml(order.productName)}</strong><br>
                    <span class="hero-text">$${Number(order.totalAmount).toFixed(2)}${skuLabel} · Qty ${order.quantity}</span>
                </td>
                <td>
                    <span class="status-pill ${order.status === "SUBMITTED" ? "submitted" : ""}">
                        ${escapeHtml(order.status)}
                    </span>
                </td>
                <td>${shipping}</td>
                <td>
                    <div class="actions-row">
                        <button class="inline-action" data-action="edit" data-id="${order.id}" type="button">Edit</button>
                        <button class="inline-action accent" data-action="submit" data-id="${order.id}" type="button" ${order.status === "SUBMITTED" ? "disabled" : ""}>Submit</button>
                        <button class="inline-action danger" data-action="delete" data-id="${order.id}" type="button">Delete</button>
                    </div>
                </td>
            </tr>
        `;
    }).join("");
}

function renderProducts() {
    if (!productTableBody) {
        return;
    }

    if (!state.products.length) {
        productTableBody.innerHTML = `<tr><td colspan="7" class="empty-state">No products yet. Create one to get started.</td></tr>`;
        return;
    }

    productTableBody.innerHTML = state.products.map((product) => {
        const tags = product.tags?.length ? escapeHtml(product.tags.join(", ")) : "N/A";
        const statusLabel = product.status?.active ? "Active" : "Inactive";
        const shippingLabels = [
            product.shipping?.fragile ? "Fragile" : "Sturdy",
            product.shipping?.hazmat ? "Hazmat" : "Non-hazmat",
            product.shipping?.requiresCooling ? "Cooling" : "Ambient",
            `Max stackable ${product.shipping?.maxStackable ?? "—"}`
        ].join(" · ");

        return `
            <tr>
                <td>${escapeHtml(product.sku)}</td>
                <td>
                    <strong>${escapeHtml(product.name)}</strong><br>
                    <span class="hero-text">${escapeHtml(product.description)}</span>
                </td>
                <td>${escapeHtml(product.category)}</td>
                <td>${statusLabel}</td>
                <td>${shippingLabels}</td>
                <td>${tags}</td>
                <td>
                    <div class="actions-row">
                        <button class="inline-action" data-action="edit" data-id="${product.id}" type="button">Edit</button>
                        <button class="inline-action danger" data-action="delete" data-id="${product.id}" type="button">Delete</button>
                    </div>
                </td>
            </tr>
        `;
    }).join("");
}

async function handleProductSubmit(event) {
    event.preventDefault();

    const id = document.getElementById("product-id").value;
    const tags = document.getElementById("product-tags").value
        .split(",")
        .map((tag) => tag.trim())
        .filter(Boolean);

    const payload = {
        sku: document.getElementById("product-sku").value.trim(),
        name: document.getElementById("product-name").value.trim(),
        description: document.getElementById("product-description").value.trim(),
        category: document.getElementById("product-category").value.trim(),
        price: {
            amount: Number(document.getElementById("product-price-amount").value),
            currency: document.getElementById("product-price-currency").value.trim().toUpperCase()
        },
        physical: {
            weight: {
                value: Number(document.getElementById("product-weight-value").value),
                unit: document.getElementById("product-weight-unit").value.trim()
            },
            dimensions: {
                length: Number(document.getElementById("product-dimensions-length").value),
                width: Number(document.getElementById("product-dimensions-width").value),
                height: Number(document.getElementById("product-dimensions-height").value),
                unit: document.getElementById("product-dimensions-unit").value.trim()
            }
        },
        shipping: {
            fragile: document.getElementById("product-shipping-fragile").value === "true",
            hazmat: document.getElementById("product-shipping-hazmat").value === "true",
            requiresCooling: document.getElementById("product-shipping-requires-cooling").value === "true",
            maxStackable: Number(document.getElementById("product-shipping-max-stackable").value)
        },
        status: {
            active: document.getElementById("product-status-active").value === "true",
            shippable: document.getElementById("product-status-shippable").value === "true"
        },
        tags
    };

    try {
        await apiRequest(id ? `/api/products/${id}` : "/api/products", {
            method: id ? "PUT" : "POST",
            body: JSON.stringify(payload)
        });

        showBanner(id ? "Product updated." : "Product created.", "success");
        resetProductForm();
        await loadDashboard();
    } catch (error) {
        showBanner(error.message, "error");
    }
}

function resetProductForm() {
    productForm.reset();
    document.getElementById("product-id").value = "";
    document.getElementById("product-submit").textContent = "Save product";
}

function populateProductForm(product) {
    document.getElementById("product-id").value = product.id;
    document.getElementById("product-sku").value = product.sku || "";
    document.getElementById("product-name").value = product.name || "";
    document.getElementById("product-description").value = product.description || "";
    document.getElementById("product-category").value = product.category || "";
    document.getElementById("product-price-amount").value = product.price?.amount ?? "";
    document.getElementById("product-price-currency").value = product.price?.currency || "";
    document.getElementById("product-weight-value").value = product.physical?.weight?.value ?? "";
    document.getElementById("product-weight-unit").value = product.physical?.weight?.unit || "";
    document.getElementById("product-dimensions-length").value = product.physical?.dimensions?.length ?? "";
    document.getElementById("product-dimensions-width").value = product.physical?.dimensions?.width ?? "";
    document.getElementById("product-dimensions-height").value = product.physical?.dimensions?.height ?? "";
    document.getElementById("product-dimensions-unit").value = product.physical?.dimensions?.unit || "";
    document.getElementById("product-shipping-fragile").value = String(product.shipping?.fragile ?? "");
    document.getElementById("product-shipping-hazmat").value = String(product.shipping?.hazmat ?? "");
    document.getElementById("product-shipping-requires-cooling").value = String(product.shipping?.requiresCooling ?? "");
    document.getElementById("product-shipping-max-stackable").value = product.shipping?.maxStackable ?? "";
    document.getElementById("product-status-active").value = String(product.status?.active ?? "");
    document.getElementById("product-status-shippable").value = String(product.status?.shippable ?? "");
    document.getElementById("product-tags").value = (product.tags || []).join(", ");
    document.getElementById("product-submit").textContent = "Update product";
    productForm.scrollIntoView({ behavior: "smooth", block: "start" });
}

async function handleProductTableClick(event) {
    const button = event.target.closest("button[data-action]");
    if (!button) {
        return;
    }

    const productId = Number(button.dataset.id);
    const product = state.products.find((entry) => entry.id === productId);

    if (!product) {
        return;
    }

    if (button.dataset.action === "edit") {
        populateProductForm(product);
        return;
    }

    if (!window.confirm(`Delete product "${product.name}"?`)) {
        return;
    }

    try {
        await apiRequest(`/api/products/${product.id}`, { method: "DELETE" });
        showBanner("Product deleted.", "success");
        resetProductForm();
        await loadDashboard();
    } catch (error) {
        showBanner(error.message, "error");
    }
}

function renderCustomerOptions() {
    const currentValue = document.getElementById("order-customer-id").value;
    const options = state.customers.map((customer) => `
        <option value="${customer.id}">${escapeHtml(getCustomerDisplayName(customer))} (${escapeHtml(customer.email)})</option>
    `).join("");

    customerSelect.innerHTML = `<option value="">Select a customer</option>${options}`;
    customerSelect.value = currentValue;
}

function renderProductOptions() {
    if (!orderProductSelect) {
        return;
    }

    const currentValue = orderProductSelect.value;
    const options = state.products.map((product) => `
        <option value="${product.id}">${escapeHtml(product.name)} (${escapeHtml(product.sku)})</option>
    `).join("");

    orderProductSelect.innerHTML = `<option value="">Select a product</option>${options}`;
    orderProductSelect.value = currentValue;
}

function renderMetrics() {
    const submittedOrders = state.orders.filter((order) => order.status === "SUBMITTED");
    const draftOrders = state.orders.filter((order) => order.status === "DRAFT");
    const totals = [
        { label: "Customers", value: state.customers.length },
        { label: "Products", value: state.products.length },
        { label: "Orders", value: state.orders.length },
        { label: "Submitted", value: submittedOrders.length },
        { label: "Drafts", value: draftOrders.length }
    ];

    metricGrid.innerHTML = totals.map((entry) => `
        <article class="metric-card">
            <span class="metric-label">${entry.label}</span>
            <strong class="metric-value">${entry.value}</strong>
        </article>
    `).join("");
}

function renderActivity() {
    const submittedOrders = state.orders
        .filter((order) => order.status === "SUBMITTED")
        .sort((left, right) => right.id - left.id)
        .slice(0, 6);

    if (!submittedOrders.length) {
        activityStack.innerHTML = `
            <article class="activity-card">
                <p class="activity-title">Awaiting order data</p>
                <p class="activity-text">Submitted orders will show shipping decisions here once the API returns them.</p>
            </article>
        `;
        return;
    }

    activityStack.innerHTML = submittedOrders.map((order) => {
        const customer = state.customers.find((entry) => entry.id === order.customerId);
        return `
            <article class="activity-card">
                <p class="activity-title">Order #${order.id}</p>
                <p class="activity-text">
                    ${escapeHtml(order.productName)} for ${escapeHtml(customer ? getCustomerDisplayName(customer) : `Customer ${order.customerId}`)}<br>
                    Shipping: ${escapeHtml(order.shippingType || "Pending")}<br>
                    ETA: ${order.estimatedDeliveryDays ?? "Pending"} day${order.estimatedDeliveryDays === 1 ? "" : "s"}
                </p>
            </article>
        `;
    }).join("");
}

function updateConnectionStatus(isOnline, message) {
    apiStatusDot.classList.remove("online", "offline");
    apiStatusDot.classList.add(isOnline ? "online" : "offline");
    apiStatusText.textContent = isOnline
        ? "API reachable at /api through the UI container"
        : `API unavailable: ${message}`;
}

function resetCustomerForm() {
    customerForm.reset();
    document.getElementById("customer-id").value = "";
    document.getElementById("customer-submit").textContent = "Save customer";
}

function resetOrderForm() {
    orderForm.reset();
    document.getElementById("order-id").value = "";
    document.getElementById("order-submit").textContent = "Save order";
    orderProductSelect.value = "";
}

function showBanner(message, type) {
    banner.textContent = message;
    banner.className = `banner ${type}`;
}

function hideBanner() {
    banner.className = "banner hidden";
    banner.textContent = "";
}

function escapeHtml(value) {
    return String(value)
        .replaceAll("&", "&amp;")
        .replaceAll("<", "&lt;")
        .replaceAll(">", "&gt;")
        .replaceAll('"', "&quot;")
        .replaceAll("'", "&#39;");
}

function getCustomerDisplayName(customer) {
    if (!customer) {
        return "Unknown customer";
    }

    const firstName = customer.name?.firstName?.trim() || "";
    const lastName = customer.name?.lastName?.trim() || "";
    const fullName = `${firstName} ${lastName}`.trim();

    if (fullName) {
        return fullName;
    }

    return customer.name || "Unnamed customer";
}

function getDefaultAddress(customer) {
    if (!customer?.addresses?.length) {
        return null;
    }
    return customer.addresses.find((address) => address.isDefault) || customer.addresses[0];
}
