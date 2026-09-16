layui.use(["layer", "table", "form", "laydate"], function () {
    var layer = layui.layer;
    var table = layui.table;
    var form = layui.form;
    var laydate = layui.laydate;

    var currentLicense = null;
    var plans = [];
    var overview = null;

    init();

    async function init() {
        bindActions();
        try {
            await Promise.all([loadOverview(), loadPlans()]);
            renderAll();
        } catch (error) {
            layer.msg(error.message || "授权中心加载失败", {icon: 2});
        }
    }

    function bindActions() {
        document.getElementById("generateSingleCodeButton").addEventListener("click", function () {
            if (!canGenerateActivationCode()) {
                AppRequest.showMessage(currentLicense.message || "当前租户暂无有效套餐，无法生成激活码", 2, 2200);
                return;
            }
            openActivationDialog(1);
        });
        document.getElementById("generateBatchCodeButton").addEventListener("click", function () {
            if (!canGenerateActivationCode()) {
                AppRequest.showMessage(currentLicense.message || "当前租户暂无有效套餐，无法生成激活码", 2, 2200);
                return;
            }
            openActivationDialog(10);
        });

        form.on("submit(saveActivationCode)", function (data) {
            var formEl = document.querySelector('form[lay-filter="activationCodeForm"]');
            AppUtils.clearFormError(formEl);
            (async function () {
                try {
                    var result = await AppRequest.request("/api/tenant/license-center/activation-codes", {
                        method: "POST",
                        body: {
                            quantity: Number(data.field.quantity || 1),
                            expireTime: data.field.expireTime ? data.field.expireTime.replace(" ", "T") : data.field.expireTime,
                            remark: data.field.remark || ""
                        }
                    }, {
                        successMessage: "激活码已生成",
                        showErrorMessage: false
                    });
                    layer.closeAll("page");
                    await loadOverview();
                    renderAll();
                    showGeneratedCodes(result.data || []);
                } catch (error) {
                    AppUtils.showFormError(formEl, error.message);
                }
            })();
            return false;
        });
    }

    async function loadOverview() {
        var result = await AppRequest.request("/api/tenant/license-center/overview", {method: "GET"}, {showErrorMessage: false});
        overview = result.data || {};
        currentLicense = overview.currentLicense || {};
        AppAuth.setLicenseInfo(currentLicense);
    }

    async function loadPlans() {
        var result = await AppRequest.request("/api/license/plans", {method: "GET"}, {showErrorMessage: false});
        plans = Array.isArray(result.data) ? result.data : [];
    }

    function renderAll() {
        renderOverview();
        renderActivatedHosts();
        renderActivationCodes();
        renderPlans();
    }

    function renderOverview() {
        document.getElementById("currentEdition").textContent = formatEdition(currentLicense.edition);
        document.getElementById("licenseMeta").innerHTML = buildMetaHtml();

        var activated = Number(overview.activatedHostCount || 0);
        var remaining = overview.remainingActivatableCount;
        var hostLimit = Number(currentLicense.hostLimit || 0);
        renderQuota("host", activated, hostLimit);
        renderQuota("user", currentLicense.userUsed, currentLicense.userLimit);

        document.getElementById("hostQuotaExtra").textContent = "已激活 " + activated + " / 剩余可激活 " + formatRemaining(remaining);
        document.getElementById("featureSummary").textContent = "功能范围 " + featureSummary(currentLicense.featureFlags);
        document.getElementById("activatedHostCount").textContent = activated + " 台";

        renderActions();
        renderGenerationButtons();
    }

    function renderActivatedHosts() {
        table.render({
            elem: "#activatedHostTable",
            data: Array.isArray(overview.activatedHosts) ? overview.activatedHosts : [],
            page: false,
            cols: [[
                {field: "hostName", title: "主机名", minWidth: 150, templet: function (d) { return escapeHtml(d.hostName || "-"); }},
                {field: "macAddress", title: "MAC", minWidth: 150, templet: function (d) { return escapeHtml(d.macAddress || "-"); }},
                {field: "machineId", title: "MachineId", minWidth: 260, templet: function (d) { return escapeHtml(d.machineId || "-"); }},
                {field: "machineBoundAt", title: "绑定时间", minWidth: 170, templet: function (d) { return AppUtils.formatDateTime(d.machineBoundAt); }},
                {field: "remark", title: "备注", minWidth: 160, templet: function (d) { return escapeHtml(d.remark || "-"); }}
            ]],
            text: {none: "暂无已激活主机"}
        });
    }

    function renderActivationCodes() {
        table.render({
            elem: "#activationCodeTable",
            data: Array.isArray(overview.activationCodes) ? overview.activationCodes : [],
            page: false,
            cols: [[
                {field: "code", title: "激活码", minWidth: 210, templet: function (d) {
                    return '<span class="activation-code-value">' + escapeHtml(d.code || "-") + "</span>";
                }},
                {field: "status", title: "状态", width: 100, templet: activationStatusTpl},
                {field: "expireTime", title: "到期时间", minWidth: 170, templet: function (d) { return AppUtils.formatDateTime(d.expireTime); }},
                {field: "boundHostName", title: "绑定主机", minWidth: 130, templet: function (d) { return escapeHtml(d.boundHostName || "-"); }},
                {field: "boundMacAddress", title: "绑定 MAC", minWidth: 150, templet: function (d) { return escapeHtml(d.boundMacAddress || "-"); }},
                {field: "usedAt", title: "使用时间", minWidth: 170, templet: function (d) { return AppUtils.formatDateTime(d.usedAt); }},
                {field: "remark", title: "备注", minWidth: 160, templet: function (d) { return escapeHtml(d.remark || "-"); }}
            ]],
            text: {none: "暂无激活码，请先生成"}
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

    function renderActions() {
        var container = document.getElementById("licenseActions");
        if (AppAuth.isSuperAdmin() || !AppAuth.isTenantAdmin()) {
            container.innerHTML = "";
            return;
        }
        container.innerHTML = '<button type="button" class="layui-btn" data-action="contact">升级套餐</button>'
            + '<button type="button" class="layui-btn layui-btn-normal" data-action="contact">续费</button>';
        container.querySelectorAll('[data-action="contact"]').forEach(function (button) {
            button.addEventListener("click", showContactMessage);
        });
    }

    function renderGenerationButtons() {
        toggleActionButton(document.getElementById("generateSingleCodeButton"), canGenerateActivationCode());
        toggleActionButton(document.getElementById("generateBatchCodeButton"), canGenerateActivationCode());
    }

    function openActivationDialog(quantity) {
        var index = layer.open({
            type: 1,
            title: quantity === 1 ? "生成激活码" : "批量生成激活码",
            area: ["560px", "340px"],
            content: AppUtils.getTemplateHtml("activationCodeFormTemplate"),
            success: function (layero) {
                form.render();
                form.val("activationCodeForm", {
                    quantity: String(quantity || 1),
                    expireTime: "",
                    remark: ""
                });
                laydate.render({
                    elem: "#activationCodeExpireTime",
                    type: "datetime",
                    format: "yyyy-MM-dd HH:mm:ss"
                });
                layero.find('[data-action="close"]').on("click", function () {
                    layer.close(index);
                });
            }
        });
    }

    function showGeneratedCodes(codes) {
        if (!Array.isArray(codes) || !codes.length) {
            return;
        }
        var html = '<div class="generated-code-dialog">'
            + '<div class="generated-code-tip">本次共生成 ' + codes.length + ' 个激活码，请分发给需要首次激活的客户端。</div>'
            + '<textarea class="layui-textarea generated-code-textarea" readonly>'
            + codes.map(function (item) { return item.code || ""; }).join("\n")
            + "</textarea>"
            + "</div>";
        layer.open({
            type: 1,
            title: "生成结果",
            area: ["560px", "420px"],
            content: html
        });
    }

    function buildMetaHtml() {
        var statusText = currentLicense.effective === false ? (currentLicense.message || "未授权") : "有效";
        var expireText = currentLicense.expireTime ? AppUtils.formatDateTime(currentLicense.expireTime) : "长期有效";
        var alert = expireAlertHtml(currentLicense.expireTime);
        return "状态：" + escapeHtml(statusText) + "，到期时间：" + escapeHtml(expireText) + alert;
    }

    function renderQuota(type, used, limit) {
        var safeUsed = Number(used || 0);
        var safeLimit = Number(limit || 0);
        var text = safeLimit > 0 ? safeUsed + "/" + safeLimit : safeUsed + "/不限";
        var percent = safeLimit > 0 ? Math.min(100, Math.round(safeUsed * 100 / safeLimit)) : 0;
        document.getElementById(type + "QuotaText").textContent = text;
        document.getElementById(type + "QuotaBar").style.width = percent + "%";
    }

    function activationStatusTpl(d) {
        var status = String(d.status || "NEW").toUpperCase();
        if (status === "USED") {
            return '<span class="status-tag success">已使用</span>';
        }
        if (status === "DISABLED") {
            return '<span class="status-tag fail">已禁用</span>';
        }
        return '<span class="status-tag warn">未使用</span>';
    }

    function featureSummary(features) {
        var list = Array.isArray(features) ? features : [];
        return list.length ? list.join(" / ") : "-";
    }

    function planActionHtml(isCurrent) {
        if (AppAuth.isSuperAdmin() || !AppAuth.isTenantAdmin()) {
            return "";
        }
        return '<button type="button" class="layui-btn layui-btn-fluid ' + (isCurrent ? "layui-btn-primary" : "") + '" data-action="contact">'
            + (isCurrent ? "续费" : "升级套餐") + "</button>";
    }

    function featureHtml(features) {
        var list = Array.isArray(features) ? features : [];
        if (!list.length) {
            return '<span class="feature-pill muted">无功能项</span>';
        }
        return list.map(function (feature) {
            return '<span class="feature-pill">' + escapeHtml(feature) + "</span>";
        }).join("");
    }

    function showContactMessage() {
        layer.msg("请联系平台管理员或销售开通、续费套餐", {icon: 0});
    }

    function canGenerateActivationCode() {
        return currentLicense && currentLicense.effective !== false;
    }

    function toggleActionButton(button, enabled) {
        if (!button) {
            return;
        }
        button.disabled = !enabled;
        button.classList.toggle("layui-btn-disabled", !enabled);
        if (!enabled) {
            button.title = currentLicense && currentLicense.message
                ? currentLicense.message
                : "当前租户暂无有效套餐，无法生成激活码";
            return;
        }
        button.title = "";
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

    function formatRemaining(remaining) {
        return remaining == null ? "不限" : remaining;
    }

    function expireAlertHtml(expireTime) {
        var days = daysUntilExpire(expireTime);
        if (days == null || days > 30) {
            return "";
        }
        var level = days <= 7 ? "danger" : "warn";
        return ' <span class="expire-alert ' + level + '">剩余 ' + days + " 天</span>";
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
