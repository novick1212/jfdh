async function api(url, options = {}) {
    const response = await fetch(url, {
        credentials: "include",
        headers: {
            "Content-Type": "application/json",
            ...(options.headers || {})
        },
        ...options
    });

    const payload = await response.json().catch(() => ({ success: false, message: "响应解析失败" }));
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
