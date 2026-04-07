const state = {
    customers: [],
    orders: []
};

const customerForm = document.getElementById("customer-form");
const orderForm = document.getElementById("order-form");
const customerTableBody = document.getElementById("customer-table-body");
const orderTableBody = document.getElementById("order-table-body");
const customerSelect = document.getElementById("order-customer-id");
const activityStack = document.getElementById("activity-stack");
const metricGrid = document.getElementById("metric-grid");
const banner = document.getElementById("feedback-banner");
const refreshButton = document.getElementById("refresh-button");
const apiStatusDot = document.getElementById("api-status-dot");
const apiStatusText = document.getElementById("api-status-text");
let tabButtons = [];
let tabPanels = [];

document.addEventListener("DOMContentLoaded", () => {
    customerForm.addEventListener("submit", handleCustomerSubmit);
    orderForm.addEventListener("submit", handleOrderSubmit);
    document.getElementById("customer-reset").addEventListener("click", resetCustomerForm);
    document.getElementById("order-reset").addEventListener("click", resetOrderForm);
    refreshButton.addEventListener("click", () => loadDashboard(true));
    customerTableBody.addEventListener("click", handleCustomerTableClick);
    orderTableBody.addEventListener("click", handleOrderTableClick);
    initializeTabs();

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
    const data = text ? JSON.parse(text) : null;

    if (!response.ok) {
        const message = data?.error || `Request failed with status ${response.status}`;
        throw new Error(message);
    }

    return data;
}

async function loadDashboard(showSuccessMessage = false, silent = false) {
    try {
        const [customers, orders] = await Promise.all([
            apiRequest("/api/customers"),
            apiRequest("/api/orders")
        ]);

        state.customers = customers;
        state.orders = orders;

        updateConnectionStatus(true);
        renderCustomers();
        renderOrders();
        renderCustomerOptions();
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
        productName: document.getElementById("order-product-name").value.trim(),
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
        document.getElementById("order-product-name").value = order.productName;
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

        return `
            <tr>
                <td>#${order.id}</td>
                <td>${escapeHtml(customerName)}</td>
                <td>
                    <strong>${escapeHtml(order.productName)}</strong><br>
                    <span class="hero-text">$${Number(order.totalAmount).toFixed(2)} · Qty ${order.quantity}</span>
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

function renderCustomerOptions() {
    const currentValue = document.getElementById("order-customer-id").value;
    const options = state.customers.map((customer) => `
        <option value="${customer.id}">${escapeHtml(getCustomerDisplayName(customer))} (${escapeHtml(customer.email)})</option>
    `).join("");

    customerSelect.innerHTML = `<option value="">Select a customer</option>${options}`;
    customerSelect.value = currentValue;
}

function renderMetrics() {
    const submittedOrders = state.orders.filter((order) => order.status === "SUBMITTED");
    const draftOrders = state.orders.filter((order) => order.status === "DRAFT");
    const totals = [
        { label: "Customers", value: state.customers.length },
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
