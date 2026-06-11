/**
 * 全局告警弹窗（外壳层，独立于具体页面）。
 *
 * 通过 WebSocket 接收服务端推送的 Critical / High 告警，在右上角以通知队列形式弹出。
 * - 连接建立时服务端下发当前未处理的高危告警做初始同步；之后实时推送新增告警。
 * - 用户关闭（或点击查看详情）后写入 localStorage，刷新或重连都不再重复弹出同一条。
 * - 只针对 Critical / High，且不干预任何页面内部功能。
 */
(function (window) {
    var STORAGE_KEY = "dismissedAlertIds";
    var MAX_DISMISSED = 1000;
    var MAX_VISIBLE = 5;
    var RECONNECT_BASE = 3000;
    var RECONNECT_MAX = 30000;

    var container = null;
    var socket = null;
    var reconnectDelay = RECONNECT_BASE;
    var reconnectTimer = null;
    var shownIds = {};      // 当前在屏 / 待显示，避免重复
    var pending = [];       // 超过 MAX_VISIBLE 的排队告警
    var visibleCount = 0;

    function getDismissed() {
        try {
            var raw = localStorage.getItem(STORAGE_KEY);
            return raw ? JSON.parse(raw) : [];
        } catch (e) {
            return [];
        }
    }

    function isDismissed(id) {
        return getDismissed().indexOf(Number(id)) > -1;
    }

    function markDismissed(id) {
        var list = getDismissed();
        if (list.indexOf(Number(id)) === -1) {
            list.push(Number(id));
            if (list.length > MAX_DISMISSED) {
                list = list.slice(list.length - MAX_DISMISSED);
            }
            try {
                localStorage.setItem(STORAGE_KEY, JSON.stringify(list));
            } catch (e) { /* ignore quota */ }
        }
    }

    function ensureContainer() {
        if (container) {
            return container;
        }
        container = document.createElement("div");
        container.className = "alert-toast-stack";
        document.body.appendChild(container);
        return container;
    }

    function escapeHtml(value) {
        if (value == null) {
            return "";
        }
        return String(value)
            .replace(/&/g, "&amp;").replace(/</g, "&lt;").replace(/>/g, "&gt;")
            .replace(/"/g, "&quot;").replace(/'/g, "&#39;");
    }

    function levelClass(level) {
        return (level || "").toLowerCase() === "critical" ? "toast-critical" : "toast-high";
    }

    function enqueue(alert) {
        if (!alert || alert.id == null) {
            return;
        }
        var lv = (alert.level || "").toLowerCase();
        if (lv !== "critical" && lv !== "high") {
            return;
        }
        if (shownIds[alert.id] || isDismissed(alert.id)) {
            return;
        }
        shownIds[alert.id] = true;
        if (visibleCount >= MAX_VISIBLE) {
            pending.push(alert);
            return;
        }
        renderCard(alert);
    }

    function renderCard(alert) {
        visibleCount++;
        var card = document.createElement("div");
        card.className = "alert-toast " + levelClass(alert.level);

        var time = alert.eventTime && window.AppUtils ? AppUtils.formatDateTime(alert.eventTime) : (alert.eventTime || "");

        card.innerHTML =
            '<div class="alert-toast-head">'
            + '<span class="alert-toast-level">' + escapeHtml(alert.level) + "</span>"
            + '<span class="alert-toast-host">' + escapeHtml(alert.hostname || ("主机#" + (alert.hostId == null ? "-" : alert.hostId))) + "</span>"
            + '<span class="alert-toast-name" title="' + escapeHtml(alert.alertName) + '">' + escapeHtml(alert.alertName) + "</span>"
            + '<button type="button" class="alert-toast-x" aria-label="关闭">&times;</button>'
            + "</div>"
            + '<div class="alert-toast-body">' + escapeHtml(alert.description || "（无描述）") + "</div>"
            + (time ? '<div class="alert-toast-time">' + escapeHtml(time) + "</div>" : "")
            + '<div class="alert-toast-actions">'
            + '<button type="button" class="alert-toast-btn primary" data-act="detail">查看详情</button>'
            + '<button type="button" class="alert-toast-btn" data-act="close">关闭</button>'
            + "</div>";

        function dismiss() {
            markDismissed(alert.id);
            removeCard(card);
        }

        card.querySelector(".alert-toast-x").addEventListener("click", dismiss);
        card.querySelector('[data-act="close"]').addEventListener("click", dismiss);
        card.querySelector('[data-act="detail"]').addEventListener("click", function () {
            markDismissed(alert.id);
            if (window.AppShell && typeof window.AppShell.navigateToAlert === "function") {
                window.AppShell.navigateToAlert(alert.id);
            }
            removeCard(card);
        });

        ensureContainer().appendChild(card);
        // 入场动画
        requestAnimationFrame(function () { card.classList.add("is-show"); });
    }

    function removeCard(card) {
        if (card.dataset.removing) {
            return;
        }
        card.dataset.removing = "1";
        card.classList.remove("is-show");
        setTimeout(function () {
            if (card.parentNode) {
                card.parentNode.removeChild(card);
            }
            visibleCount = Math.max(0, visibleCount - 1);
            if (pending.length > 0 && visibleCount < MAX_VISIBLE) {
                renderCard(pending.shift());
            }
        }, 220);
    }

    function handleMessage(event) {
        var msg;
        try {
            msg = JSON.parse(event.data);
        } catch (e) {
            return;
        }
        if (!msg || !msg.type) {
            return;
        }
        if (msg.type === "init" && Array.isArray(msg.data)) {
            // 初始同步：按时间正序展示，旧的在上、新的在下
            msg.data.slice().reverse().forEach(enqueue);
        } else if (msg.type === "alert" && msg.data) {
            enqueue(msg.data);
        }
    }

    function buildWsUrl() {
        var token = window.AppAuth ? AppAuth.getToken() : null;
        if (!token) {
            return null;
        }
        var scheme = window.location.protocol === "https:" ? "wss" : "ws";
        return scheme + "://" + window.location.host + "/ws/alerts?token=" + encodeURIComponent(token);
    }

    function connect() {
        var url = buildWsUrl();
        if (!url) {
            return;
        }
        try {
            socket = new WebSocket(url);
        } catch (e) {
            scheduleReconnect();
            return;
        }
        socket.onopen = function () {
            reconnectDelay = RECONNECT_BASE;
        };
        socket.onmessage = handleMessage;
        socket.onclose = function () {
            socket = null;
            scheduleReconnect();
        };
        socket.onerror = function () {
            if (socket) {
                try { socket.close(); } catch (e) { /* ignore */ }
            }
        };
    }

    function scheduleReconnect() {
        if (reconnectTimer) {
            return;
        }
        reconnectTimer = setTimeout(function () {
            reconnectTimer = null;
            if (window.AppAuth && AppAuth.isLoggedIn()) {
                connect();
            }
        }, reconnectDelay);
        reconnectDelay = Math.min(reconnectDelay * 2, RECONNECT_MAX);
    }

    function start() {
        if (!window.AppAuth || !AppAuth.isLoggedIn()) {
            return;
        }
        ensureContainer();
        connect();
    }

    window.AppAlertNotifier = {start: start};

    if (document.readyState === "loading") {
        document.addEventListener("DOMContentLoaded", start);
    } else {
        start();
    }
})(window);
