layui.use(["layer"], function () {
    var layer = layui.layer;
    var currentFilter = "all";
    var hostCards = [];

    var API = {
        summary: "/api/vuln-detection/summary",
        hosts: "/api/vuln-detection/hosts",
        evaluateAll: "/api/vuln-detection/evaluate-all",
        batchVerify: "/api/vuln-verification/batch-verify",
        verifyTask: function (hostId) {
            return "/api/vuln-verification/hosts/" + hostId + "/verify";
        },
        hostDetail: function (hostId) {
            return "/api/vuln-detection/hosts/" + hostId + "/results";
        }
    };

    init();

    function init() {
        bindEvents();
        refreshPage(false);
    }

    function bindEvents() {
        document.getElementById("refreshButton").addEventListener("click", function () {
            refreshPage(false);
        });

        document.getElementById("rematchButton").addEventListener("click", function () {
            layer.confirm("重新匹配会基于当前资产和规则库重建静态结果，正在验证中的状态可能被刷新。确认继续？", {
                title: "重新匹配",
                btn: ["确认", "取消"]
            }, function (index) {
                layer.close(index);
                refreshPage(true);
            });
        });

        document.getElementById("batchVerifyButton").addEventListener("click", function () {
            batchVerify();
        });

        document.querySelectorAll(".filter-pill").forEach(function (button) {
            button.addEventListener("click", function () {
                document.querySelectorAll(".filter-pill").forEach(function (item) {
                    item.classList.remove("is-active");
                });
                button.classList.add("is-active");
                currentFilter = button.dataset.filter || "all";
                renderHosts();
            });
        });

        document.getElementById("hostCardGrid").addEventListener("click", function (event) {
            var verifyButton = event.target.closest("[data-action='verify']");
            if (verifyButton) {
                event.stopPropagation();
                createVerifyTask(verifyButton.dataset.hostId);
                return;
            }

            var card = event.target.closest(".host-card");
            if (card) {
                openHostDetail(card.dataset.hostId);
            }
        });

        document.addEventListener("click", function (event) {
            var drawerVerifyButton = event.target.closest("[data-action='drawer-verify']");
            if (drawerVerifyButton) {
                createVerifyTask(drawerVerifyButton.dataset.hostId);
            }
        });
    }

    async function refreshPage(runEvaluate) {
        var loadingIndex = layer.load(2);
        try {
            if (runEvaluate && AppAuth.hasPermission("vuln-detection:analyze")) {
                await AppRequest.request(API.evaluateAll, {method: "POST"}, {
                    showErrorMessage: false,
                    successMessage: "静态规则匹配已刷新"
                });
            }
            await Promise.all([loadSummary(), loadHosts()]);
        } finally {
            layer.close(loadingIndex);
        }
    }

    async function loadSummary() {
        try {
            var result = await AppRequest.request(API.summary, {method: "GET"}, {
                showErrorMessage: false,
                redirectOnUnauthorized: false
            });
            renderSummary(result.data || {});
        } catch (error) {
            renderSummary({});
        }
    }

    async function loadHosts() {
        try {
            var result = await AppRequest.request(API.hosts, {method: "GET"}, {
                showErrorMessage: false,
                redirectOnUnauthorized: false
            });
            hostCards = Array.isArray(result.data) ? result.data : [];
        } catch (error) {
            hostCards = [];
        }
        renderHosts();
    }

    function renderSummary(summary) {
        var data = Object.assign({
            criticalCount: 0,
            highCount: 0,
            mediumCount: 0,
            lowCount: 0,
            pendingCount: 0,
            verifyingCount: 0,
            verifiedCount: 0,
            repairCount: 0,
            ignoredCount: 0
        }, summary || {});

        document.getElementById("severityMetrics").innerHTML = [
            metricHtml("严重", data.criticalCount, "critical"),
            metricHtml("高危", data.highCount, "high"),
            metricHtml("中危", data.mediumCount, "medium"),
            metricHtml("低危", data.lowCount, "low")
        ].join("");

        document.getElementById("statusMetrics").innerHTML = [
            metricHtml("待验证", data.pendingCount, "pending"),
            metricHtml("验证中", data.verifyingCount, "verifying"),
            metricHtml("已验证", data.verifiedCount, "verified"),
            metricHtml("待修复", data.repairCount, "repair"),
            metricHtml("误报/忽略", data.ignoredCount, "ignored")
        ].join("");
    }

    function renderHosts() {
        var rows = hostCards.filter(matchFilter);
        var grid = document.getElementById("hostCardGrid");
        var empty = document.getElementById("hostEmpty");

        if (rows.length === 0) {
            grid.innerHTML = "";
            empty.style.display = "flex";
            return;
        }

        empty.style.display = "none";
        grid.innerHTML = rows.map(renderHostCard).join("");
    }

    function matchFilter(host) {
        if (currentFilter === "pending") {
            return numberValue(host.pendingCount) > 0;
        }
        if (currentFilter === "verifying") {
            return numberValue(host.verifyingCount) > 0;
        }
        if (currentFilter === "verified") {
            return numberValue(host.verifiedCount) > 0;
        }
        if (currentFilter === "repair") {
            return numberValue(host.repairCount) > 0;
        }
        return true;
    }

    function renderHostCard(host) {
        var severity = normalizeSeverity(host.highestSeverity);
        var hostId = host.hostId || "";
        var verifyButton = numberValue(host.pendingRuleCount) > 0
            ? '<button type="button" class="layui-btn layui-btn-warm layui-btn-sm" data-action="verify" data-host-id="' + escapeHtml(hostId) + '">下发验证</button>'
            : '<button type="button" class="layui-btn layui-btn-primary layui-btn-sm">查看漏洞</button>';

        return '<article class="host-card host-card-' + severity + '" data-host-id="' + escapeHtml(hostId) + '">'
            + '<div class="host-card-main">'
            + '<div class="host-icon"><i class="layui-icon layui-icon-engine"></i></div>'
            + '<div class="host-info">'
            + '<div class="host-name-row">'
            + '<h3>' + escapeHtml(host.hostname || ("Host #" + (hostId || "-"))) + '</h3>'
            + renderSeverityTag(severity)
            + '</div>'
            + '<div class="host-meta">'
            + '<span>' + escapeHtml(host.ipv4 || "-") + '</span>'
            + '<span>' + escapeHtml(host.macAddress || "-") + '</span>'
            + '<span>' + escapeHtml([host.osName, host.osVersion].filter(Boolean).join(" ") || "-") + '</span>'
            + '</div>'
            + '</div>'
            + '</div>'
            + '<div class="host-card-stats">'
            + statHtml("漏洞", host.totalVulnCount, "total")
            + statHtml("待验证", host.pendingCount, "pending")
            + statHtml("可下发", host.pendingRuleCount, "dispatchable")
            + statHtml("验证中", host.verifyingCount, "verifying")
            + statHtml("已验证", host.verifiedCount, "verified")
            + '</div>'
            + '<div class="host-card-foot">'
            + '<span>最近检测：' + escapeHtml(formatDateTime(host.latestScanTime)) + '</span>'
            + verifyButton
            + '</div>'
            + '</article>';
    }

    async function createVerifyTask(hostId) {
        if (!hostId) {
            AppRequest.showMessage("缺少主机ID，无法下发验证", 2, 1800);
            return;
        }
        try {
            var result = await AppRequest.request(API.verifyTask(hostId), {method: "POST"});
            if (result.data && result.data.sent === false) {
                AppRequest.showMessage(result.data.message || "任务已创建但下发失败，可稍后重试", 2, 2200);
            } else {
                AppRequest.showMessage(result.data && result.data.message ? result.data.message : "验证任务已下发", 1, 1800);
            }
            await refreshPage(false);
        } catch (error) {
            return;
        }
    }

    async function batchVerify() {
        var hostIds = hostCards
            .filter(function (host) {
                return numberValue(host.pendingRuleCount) > 0;
            })
            .map(function (host) {
                return host.hostId;
            })
            .filter(Boolean);

        if (hostIds.length === 0) {
            AppRequest.showMessage("当前没有可下发验证的主机", 0, 1800);
            return;
        }

        var loadingIndex = layer.load(2);
        try {
            var result = await AppRequest.request(API.batchVerify, {
                method: "POST",
                body: {hostIds: hostIds}
            });
            var data = result.data || {};
            var successCount = Object.keys(data).filter(function (key) {
                return data[key] != null;
            }).length;
            AppRequest.showMessage("批量验证已提交：" + successCount + "/" + hostIds.length, successCount > 0 ? 1 : 2, 2200);
            await refreshPage(false);
        } finally {
            layer.close(loadingIndex);
        }
    }

    async function openHostDetail(hostId) {
        if (!hostId) {
            AppRequest.showMessage("缺少主机ID，无法查看详情", 2, 1800);
            return;
        }

        var loadingIndex = layer.load(2);
        try {
            var result = await AppRequest.request(API.hostDetail(hostId), {method: "GET"}, {
                showErrorMessage: false,
                redirectOnUnauthorized: false
            });
            layer.close(loadingIndex);
            showDetailDrawer(hostId, Array.isArray(result.data) ? result.data : []);
        } catch (error) {
            layer.close(loadingIndex);
            showDetailDrawer(hostId, []);
        }
    }

    function showDetailDrawer(hostId, vulnerabilities) {
        layer.open({
            type: 1,
            title: false,
            shadeClose: true,
            area: [getDrawerWidth(), "100%"],
            offset: "r",
            anim: 5,
            content: buildDetailHtml(hostId, vulnerabilities)
        });
    }

    function buildDetailHtml(hostId, vulnerabilities) {
        var host = hostCards.find(function (item) {
            return String(item.hostId) === String(hostId);
        }) || {};
        var content = vulnerabilities.length > 0
            ? vulnerabilities.map(renderVulnItem).join("")
            : '<div class="detail-empty">该主机暂无漏洞明细。</div>';
        var verifyAction = numberValue(host.pendingRuleCount) > 0
            ? '<button type="button" class="layui-btn layui-btn-warm" data-action="drawer-verify" data-host-id="' + escapeHtml(hostId) + '">下发验证</button>'
            : "";

        return '<div class="host-detail-drawer">'
            + '<div class="drawer-head">'
            + '<div>'
            + '<div class="section-kicker">Host Detail</div>'
            + '<h2>' + escapeHtml(host.hostname || ("Host #" + hostId)) + '</h2>'
            + '<p>' + escapeHtml(host.ipv4 || "-") + ' / ' + escapeHtml(host.macAddress || "-") + '</p>'
            + '</div>'
            + '<div class="drawer-actions">' + renderSeverityTag(normalizeSeverity(host.highestSeverity)) + verifyAction + '</div>'
            + '</div>'
            + '<div class="drawer-summary">'
            + statHtml("漏洞", host.totalVulnCount, "total")
            + statHtml("待验证", host.pendingCount, "pending")
            + statHtml("可下发", host.pendingRuleCount, "dispatchable")
            + statHtml("验证中", host.verifyingCount, "verifying")
            + statHtml("已验证", host.verifiedCount, "verified")
            + '</div>'
            + '<div class="vuln-list">' + content + '</div>'
            + '</div>';
    }

    function renderVulnItem(item) {
        var severity = normalizeSeverity(item.severity);
        return '<article class="vuln-item">'
            + '<div class="vuln-item-head">'
            + '<h3>' + escapeHtml(item.vulnName || "-") + '</h3>'
            + '<div class="vuln-item-tags">' + renderSeverityTag(severity) + renderStatusTag(item.verifyStatus, item.status) + '</div>'
            + '</div>'
            + '<div class="vuln-item-meta">'
            + '<span>资产：' + escapeHtml(item.productName || "-") + '</span>'
            + '<span>版本：' + escapeHtml(item.productVersion || "-") + '</span>'
            + '<span>规则：#' + escapeHtml(item.ruleId || "-") + '</span>'
            + '</div>'
            + '<p>' + escapeHtml(item.suggestion || "-") + '</p>'
            + '<pre>' + escapeHtml(formatEvidence(item.evidenceJson)) + '</pre>'
            + '</article>';
    }

    function metricHtml(label, value, cls) {
        return '<div class="metric-card ' + cls + '"><span>' + label + '</span><strong>' + numberValue(value) + '</strong></div>';
    }

    function statHtml(label, value, cls) {
        return '<div class="host-stat ' + cls + '"><span>' + label + '</span><strong>' + numberValue(value) + '</strong></div>';
    }

    function renderSeverityTag(severity) {
        var labelMap = {
            critical: "严重",
            high: "高危",
            medium: "中危",
            low: "低危",
            clean: "健康",
            unknown: "未知"
        };
        return '<span class="severity-tag severity-' + severity + '">' + (labelMap[severity] || "未知") + '</span>';
    }

    function renderStatusTag(verifyStatus, status) {
        var label = statusLabel(verifyStatus, status);
        var cls = label === "待验证" ? "pending" : (label === "已验证" ? "verified" : (label === "验证中" ? "verifying" : "ignored"));
        return '<span class="status-chip status-' + cls + '">' + label + '</span>';
    }

    function normalizeSeverity(value) {
        var normalized = String(value || "").toLowerCase();
        if (["critical", "严重", "severe"].indexOf(normalized) >= 0) return "critical";
        if (["high", "高危"].indexOf(normalized) >= 0) return "high";
        if (["medium", "中危"].indexOf(normalized) >= 0) return "medium";
        if (["low", "低危"].indexOf(normalized) >= 0) return "low";
        if (["clean", "none", "healthy"].indexOf(normalized) >= 0) return "clean";
        return "unknown";
    }

    function statusLabel(verifyStatus, status) {
        if (Number(status) === 0) {
            return "已忽略";
        }
        var normalized = String(verifyStatus || "").toUpperCase();
        if (normalized === "VERIFYING") {
            return "验证中";
        }
        if (normalized === "VERIFIED") {
            return "已验证";
        }
        if (normalized === "FAILED") {
            return "误报";
        }
        return "待验证";
    }

    function formatEvidence(value) {
        if (!value) {
            return "{}";
        }
        if (typeof value === "object") {
            return JSON.stringify(value, null, 2);
        }
        try {
            return JSON.stringify(JSON.parse(value), null, 2);
        } catch (error) {
            return String(value);
        }
    }

    function numberValue(value) {
        var number = Number(value);
        return Number.isFinite(number) ? number : 0;
    }

    function formatDateTime(value) {
        if (window.AppUtils && typeof window.AppUtils.formatDateTime === "function") {
            return window.AppUtils.formatDateTime(value);
        }
        return value || "-";
    }

    function getDrawerWidth() {
        return window.innerWidth < 760 ? "94%" : "720px";
    }

    function escapeHtml(value) {
        return String(value == null ? "" : value)
            .replace(/&/g, "&amp;")
            .replace(/</g, "&lt;")
            .replace(/>/g, "&gt;")
            .replace(/"/g, "&quot;")
            .replace(/'/g, "&#39;");
    }
});
