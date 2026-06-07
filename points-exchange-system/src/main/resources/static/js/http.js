let _csrfToken = null;

async function fetchCsrfToken() {
    if (_csrfToken) return _csrfToken;
    const resp = await fetch("/api/auth/csrf-token", { credentials: "include" });
    const data = await resp.json();
    if (data.success && data.data) {
        _csrfToken = data.data.csrfToken;
    }
    return _csrfToken;
}

async function api(url, options = {}) {
    const method = (options.method || "GET").toUpperCase();
    const extraHeaders = {};

    // 对修改型请求附加 CSRF token
    if (method !== "GET" && method !== "HEAD" && method !== "OPTIONS") {
        const token = await fetchCsrfToken();
        if (token) {
            extraHeaders["X-CSRF-Token"] = token;
        }
    }

    const response = await fetch(url, {
        credentials: "include",
        headers: {
            "Content-Type": "application/json",
            ...extraHeaders,
            ...(options.headers || {})
        },
        ...options
    });

    const payload = await response.json().catch(() => ({ success: false, message: "响应解析失败" }));

    // 如果 CSRF token 过期，清除缓存并重试一次
    if (response.status === 403 && payload.message && payload.message.includes("CSRF")) {
        _csrfToken = null;
        const token = await fetchCsrfToken();
        if (token) {
            const retryResponse = await fetch(url, {
                credentials: "include",
                headers: {
                    "Content-Type": "application/json",
                    "X-CSRF-Token": token,
                    ...(options.headers || {})
                },
                ...options
            });
            const retryPayload = await retryResponse.json().catch(() => ({ success: false, message: "响应解析失败" }));
            if (!retryResponse.ok || retryPayload.success === false) {
                throw new Error(retryPayload.message || "请求失败");
            }
            return retryPayload.data;
        }
    }

    if (!response.ok || payload.success === false) {
        throw new Error(payload.message || "请求失败");
    }
    return payload.data;
}

function escapeHtml(value) {
    return String(value ?? "")
        .replaceAll("&", "&amp;")
        .replaceAll("<", "&lt;")
        .replaceAll(">", "&gt;")
        .replaceAll("\"", "&quot;");
}

function getOrderStatusText(status) {
    const statusMap = {
        "PENDING": "待处理",
        "PROCESSING": "处理中",
        "FULFILLED": "已兑换",
        "CANCELLED": "已取消",
        "REFUNDED": "已退款"
    };
    return statusMap[status] || status || "-";
}
