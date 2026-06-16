layui.use(["layer"], function () {
    var layer = layui.layer;
    var currentLicense = null;
    var plans = [];

    init();

    async function init() {
        try {
            await Promise.all([loadCurrentLicense(), loadPlans()]);
            renderOverview();
            renderPlans();
        } catch (error) {
            layer.msg(error.message || "套餐中心加载失败", {icon: 2});
        }
    }

    async function loadCurrentLicense() {
        var result = await AppRequest.request("/api/license/current", {method: "GET"}, {showErrorMessage: false});
        currentLicense = result.data || {};
        AppAuth.setLicenseInfo(currentLicense);
    }

    async function loadPlans() {
        var result = await AppRequest.request("/api/license/plans", {method: "GET"}, {showErrorMessage: false});
        plans = Array.isArray(result.data) ? result.data : [];
    }

    function renderOverview() {
        document.getElementById("currentEdition").textContent = formatEdition(currentLicense.edition);
        document.getElementById("licenseMeta").innerHTML = buildMetaHtml();
        renderQuota("host", currentLicense.hostUsed, currentLicense.hostLimit);
        renderQuota("user", currentLicense.userUsed, currentLicense.userLimit);
        renderActions();
    }

    function buildMetaHtml() {
        var statusText = currentLicense.effective === false ? (currentLicense.message || "未授权") : "有效";
        var expireText = currentLicense.expireTime ? AppUtils.formatDateTime(currentLicense.expireTime) : "长期有效";
        var alert = expireAlertHtml(currentLicense.expireTime);
        return "状态：" + escapeHtml(statusText) + "　到期时间：" + escapeHtml(expireText) + alert;
    }

    function renderQuota(type, used, limit) {
        var safeUsed = Number(used || 0);
        var safeLimit = Number(limit || 0);
        var text = safeLimit > 0 ? safeUsed + "/" + safeLimit : safeUsed + "/不限";
        var percent = safeLimit > 0 ? Math.min(100, Math.round(safeUsed * 100 / safeLimit)) : 0;
        document.getElementById(type + "QuotaText").textContent = text;
        document.getElementById(type + "QuotaBar").style.width = percent + "%";
    }

    function renderActions() {
        var container = document.getElementById("licenseActions");
        if (AppAuth.isSuperAdmin() || !AppAuth.isTenantAdmin()) {
            container.innerHTML = "";
            return;
        }
        container.innerHTML = '<button type="button" class="layui-btn" data-action="contact">升级套餐</button>'
            + '<button type="button" class="layui-btn layui-btn-normal" data-action="contact">续费</button>'
            + '<button type="button" class="layui-btn layui-btn-primary" data-action="contact">联系销售</button>';
        container.querySelectorAll('[data-action="contact"]').forEach(function (button) {
            button.addEventListener("click", showContactMessage);
        });
    }

    function renderPlans() {
        var currentCode = String(currentLicense.edition || "").toUpperCase();
        var grid = document.getElementById("planGrid");
        if (!plans.length) {
            grid.innerHTML = '<div class="empty-plan">暂无可用套餐</div>';
            return;
        }
        grid.innerHTML = plans.map(function (plan) {
            var isCurrent = String(plan.code || "").toUpperCase() === currentCode;
            return '<article class="plan-card' + (isCurrent ? " current" : "") + '">'
                + '<div class="plan-card-head">'
                + '<div><h4>' + escapeHtml(plan.name || plan.code || "-") + '</h4>'
                + '<p>' + escapeHtml(plan.code || "-") + '</p></div>'
                + (isCurrent ? '<span class="current-badge">当前套餐</span>' : "")
                + '</div>'
                + '<div class="plan-limits">'
                + '<span>主机 ' + formatLimit(plan.hostLimit) + '</span>'
                + '<span>用户 ' + formatLimit(plan.userLimit) + '</span>'
                + '</div>'
                + '<div class="feature-list">' + featureHtml(plan.featureFlags) + '</div>'
                + planActionHtml(isCurrent)
                + '</article>';
        }).join("");
        grid.querySelectorAll('[data-action="contact"]').forEach(function (button) {
            button.addEventListener("click", showContactMessage);
        });
    }

    function planActionHtml(isCurrent) {
        if (AppAuth.isSuperAdmin() || !AppAuth.isTenantAdmin()) {
            return "";
        }
        return '<button type="button" class="layui-btn layui-btn-fluid ' + (isCurrent ? "layui-btn-primary" : "") + '" data-action="contact">'
            + (isCurrent ? "续费" : "升级套餐") + '</button>';
    }

    function featureHtml(features) {
        var list = Array.isArray(features) ? features : [];
        if (!list.length) {
            return '<span class="feature-pill muted">无功能项</span>';
        }
        return list.map(function (feature) {
            return '<span class="feature-pill">' + escapeHtml(feature) + '</span>';
        }).join("");
    }

    function showContactMessage() {
        layer.msg("请联系销售或管理员开通服务", {icon: 0});
    }

    function formatEdition(edition) {
        var labels = {
            PLATFORM: "平台",
            NONE: "未授权"
        };
        return labels[edition] || edition || "-";
    }

    function formatLimit(limit) {
        return Number(limit || 0) > 0 ? limit : "不限";
    }

    function expireAlertHtml(expireTime) {
        var days = daysUntilExpire(expireTime);
        if (days == null || days > 30) {
            return "";
        }
        var level = days <= 7 ? "danger" : "warn";
        return ' <span class="expire-alert ' + level + '">剩余 ' + days + ' 天</span>';
    }

    function daysUntilExpire(expireTime) {
        if (!expireTime) {
            return null;
        }
        var expire = new Date(expireTime).getTime();
        if (Number.isNaN(expire)) {
            return null;
        }
        return Math.ceil((expire - Date.now()) / 86400000);
    }

    function escapeHtml(text) {
        return String(text == null ? "" : text)
            .replace(/&/g, "&amp;")
            .replace(/</g, "&lt;")
            .replace(/>/g, "&gt;")
            .replace(/"/g, "&quot;")
            .replace(/'/g, "&#39;");
    }
});
