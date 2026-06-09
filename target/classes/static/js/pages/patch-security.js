layui.use(["table", "form", "layer"], function () {
    var table = layui.table;
    var form = layui.form;
    var layer = layui.layer;
    var tableId = "patchRiskHostTable";
    var AUTO_STRATEGY_KEY = "patchSecurityAutoScanStrategy";
    var autoScanTimer = null;

    var API = {
        summary: "/api/patch-security/summary",
        hosts: "/api/patch-security/risk-hosts",
        risks: function (hostId) { return "/api/patch-security/hosts/" + hostId + "/risks"; },
        analyzeAll: "/api/patch-security/analyze",
        scanAll: "/api/patch-security/scan",
        analyzeHost: function (hostId) { return "/api/patch-security/hosts/" + hostId + "/analyze"; },
        scanHost: function (hostId) { return "/api/patch-security/hosts/" + hostId + "/scan"; }
    };

    init();

    function init() {
        form.render();
        applyPermissionState();
        renderTable();
        loadSummary();
        bindEvents();
        setupAutoScanStrategy();
    }

    function renderTable() {
        AppTable.renderPageTable(table, {
            elem: "#" + tableId,
            url: API.hosts,
            limit: 10,
            limits: [10, 20, 50],
            text: {
                none: "当前没有开放的补丁风险主机"
            },
            cols: [[
                {field: "hostname", title: "主机", minWidth: 120, templet: function (d) {
                    return '<div class="host-cell">'
                        + '<strong>' + escapeHtml(d.hostname || "-") + '</strong>'
                        + '<span>' + escapeHtml(d.ipv4 || "-") + '</span>'
                        + '</div>';
                }},
                {field: "macAddress", title: "MAC", minWidth: 150, templet: function (d) { return escapeHtml(d.macAddress || "-"); }},
                {field: "osName", title: "系统", minWidth: 180, templet: function (d) {
                    var os = [d.osName, d.osVersion].filter(Boolean).join(" ");
                    return '<div class="os-cell">'
                        + '<strong>' + escapeHtml(os || "-") + '</strong>'
                        + '<span>Build ' + escapeHtml(d.osBuild || "-") + '</span>'
                        + '</div>';
                }},
                {field: "highestRiskLevel", title: "最高风险", width: 110, templet: function (d) {
                    return renderRiskLevel(d.highestRiskLevel);
                }},
                {field: "riskCount", title: "风险数", width: 80, align: "center", templet: function (d) {
                    return '<strong class="risk-count">' + (d.riskCount || 0) + '</strong>';
                }},
                {field: "riskTypes", title: "风险类型", minWidth: 100, templet: function (d) {
                    return renderRiskTypes(d.riskTypes);
                }},
                {field: "pendingReboot", title: "重启", width: 96, templet: function (d) {
                    return d.pendingReboot === 1
                        ? '<span class="status-tag warn">待重启</span>'
                        : '<span class="status-tag success">正常</span>';
                }},
                {field: "lastPatchScanTime", title: "最近扫描", width: 168, templet: function (d) {
                    return AppUtils.formatDateTime(d.lastPatchScanTime);
                }},
                {title: "操作", width: 220, fixed: "right", templet: function () {
                    var html = '<button type="button" class="layui-btn layui-btn-primary layui-btn-xs" lay-event="detail">详情</button>';
                    if (AppAuth.hasPermission("patch-security:analyze")) {
                        html += '<button type="button" class="layui-btn layui-btn-normal layui-btn-xs" lay-event="analyze">分析</button>';
                    }
                    if (AppAuth.hasPermission("patch-security:scan")) {
                        html += '<button type="button" class="layui-btn layui-btn-xs" lay-event="scan">扫描</button>';
                    }
                    return html;
                }}
            ]]
        });
    }

    function bindEvents() {
        form.on("submit(patchRiskSearchSubmit)", function (data) {
            AppTable.reload(table, tableId, normalizeFilters(data.field));
            return false;
        });

        document.getElementById("resetButton").addEventListener("click", function () {
            form.val("patchRiskSearchForm", {
                keyword: "",
                riskLevel: "",
                riskType: "",
                pendingReboot: "",
                osName: ""
            });
            form.render("select");
            AppTable.reload(table, tableId, {});
        });

        document.getElementById("refreshSummaryButton").addEventListener("click", function () {
            reloadPageData();
        });

        document.getElementById("analyzeAllButton").addEventListener("click", function () {
            if (!AppAuth.hasPermission("patch-security:analyze")) {
                AppRequest.showMessage("当前账号没有重新分析权限", 2);
                return;
            }
            confirmAction("确认对所有已采集补丁状态的主机重新分析风险吗？", async function () {
                await postAction(API.analyzeAll, {}, "风险分析已完成");
            });
        });

        document.getElementById("scanAllButton").addEventListener("click", function () {
            if (!AppAuth.hasPermission("patch-security:scan")) {
                AppRequest.showMessage("当前账号没有补丁扫描权限", 2);
                return;
            }
            confirmAction("确认向在线客户端下发补丁扫描任务吗？", async function () {
                await postAction(API.scanAll, {}, "补丁扫描任务已下发");
            });
        });

        document.getElementById("autoStrategyButton").addEventListener("click", function () {
            if (!AppAuth.hasPermission("patch-security:scan")) {
                AppRequest.showMessage("当前账号没有自动扫描策略权限", 2);
                return;
            }
            openAutoStrategyDialog();
        });

        form.on("submit(savePatchAutoStrategy)", function (data) {
            var strategy = {
                enabled: data.field.enabled === "on",
                periodMinutes: parseInt(data.field.periodMinutes || "60", 10),
                scanOnlyRiskHosts: data.field.scanScope === "risk",
                analyzeAfterScan: data.field.analyzeAfterScan === "on",
                updatedAt: new Date().toISOString()
            };
            saveAutoStrategy(strategy);
            setupAutoScanStrategy();
            layer.closeAll("page");
            AppRequest.showMessage(strategy.enabled ? "自动扫描策略已启用" : "自动扫描策略已关闭", 1);
            return false;
        });

        table.on("tool(patchRiskHostTable)", function (obj) {
            if (obj.event === "detail") {
                openRiskDetail(obj.data);
            }
            if (obj.event === "analyze") {
                confirmAction("确认重新分析该主机的补丁风险吗？", async function () {
                    await postAction(API.analyzeHost(obj.data.hostId), null, "主机风险分析已完成");
                });
            }
            if (obj.event === "scan") {
                confirmAction("确认向该主机下发补丁扫描任务吗？", async function () {
                    await postAction(API.scanHost(obj.data.hostId), null, "补丁扫描任务已下发");
                });
            }
        });
    }

    function applyPermissionState() {
        if (!AppAuth.hasPermission("patch-security:analyze")) {
            document.getElementById("analyzeAllButton").style.display = "none";
        }
        if (!AppAuth.hasPermission("patch-security:scan")) {
            document.getElementById("scanAllButton").style.display = "none";
            document.getElementById("autoStrategyButton").style.display = "none";
        }
    }

    async function loadSummary() {
        try {
            var result = await AppRequest.request(API.summary, {method: "GET"}, {showErrorMessage: false});
            renderSummary(result.data || {});
        } catch (error) {
            renderSummary({});
        }
    }

    function renderSummary(summary) {
        var metrics = [
            {label: "风险主机总数", value: summary.riskyHostCount, cls: ""},
            {label: "Critical 风险数量", value: summary.criticalCount, cls: "danger"},
            {label: "High 风险数量", value: summary.highCount, cls: "warning"},
            {label: "未扫描主机数量", value: summary.unscannedHostCount, cls: ""}
        ];
        document.getElementById("summaryMetrics").innerHTML = metrics.map(function (item) {
            return '<div class="metric-tile ' + item.cls + '">'
                + '<span>' + item.label + '</span>'
                + '<strong>' + formatMetricValue(item.value) + '</strong>'
                + '</div>';
        }).join("");
    }

    function openAutoStrategyDialog() {
        var strategy = loadAutoStrategy();
        layer.open({
            type: 1,
            title: "自动扫描策略",
            area: ["520px", "420px"],
            shadeClose: true,
            content: buildAutoStrategyHtml(strategy),
            success: function (layero) {
                form.render(null, "patchAutoStrategyForm");
                layero.find("[data-action='close']").on("click", function () {
                    layer.closeAll("page");
                });
            }
        });
    }

    function buildAutoStrategyHtml(strategy) {
        return '<form class="layui-form popup-form patch-strategy-form" lay-filter="patchAutoStrategyForm">'
            + '<div class="strategy-note">策略保存在当前浏览器，打开补丁安全页面期间自动执行；手动扫描入口会一直保留。</div>'
            + '<div class="layui-form-item">'
            + '<label class="layui-form-label">自动扫描</label>'
            + '<div class="layui-input-block">'
            + '<input type="checkbox" name="enabled" lay-skin="switch" lay-text="开启|关闭" ' + (strategy.enabled ? "checked" : "") + '>'
            + '</div>'
            + '</div>'
            + '<div class="layui-form-item">'
            + '<label class="layui-form-label">扫描周期</label>'
            + '<div class="layui-input-block">'
            + '<select name="periodMinutes">'
            + optionHtml("15", "每 15 分钟", strategy.periodMinutes)
            + optionHtml("30", "每 30 分钟", strategy.periodMinutes)
            + optionHtml("60", "每 1 小时", strategy.periodMinutes)
            + optionHtml("240", "每 4 小时", strategy.periodMinutes)
            + optionHtml("720", "每 12 小时", strategy.periodMinutes)
            + '</select>'
            + '</div>'
            + '</div>'
            + '<div class="layui-form-item">'
            + '<label class="layui-form-label">扫描范围</label>'
            + '<div class="layui-input-block">'
            + '<input type="radio" name="scanScope" value="online" title="全部在线主机" ' + (!strategy.scanOnlyRiskHosts ? "checked" : "") + '>'
            + '<input type="radio" name="scanScope" value="risk" title="当前页风险主机" ' + (strategy.scanOnlyRiskHosts ? "checked" : "") + '>'
            + '</div>'
            + '</div>'
            + '<div class="layui-form-item">'
            + '<label class="layui-form-label">扫描后</label>'
            + '<div class="layui-input-block">'
            + '<input type="checkbox" name="analyzeAfterScan" title="自动重新分析风险" lay-skin="primary" ' + (strategy.analyzeAfterScan ? "checked" : "") + '>'
            + '</div>'
            + '</div>'
            + '<div class="strategy-status">当前状态：' + strategyStatusText(strategy) + '</div>'
            + '<div class="popup-actions">'
            + '<button type="button" class="layui-btn layui-btn-primary" data-action="close">取消</button>'
            + '<button type="submit" class="layui-btn" lay-submit lay-filter="savePatchAutoStrategy">保存策略</button>'
            + '</div>'
            + '</form>';
    }

    function optionHtml(value, label, selectedValue) {
        return '<option value="' + value + '" ' + (String(selectedValue) === value ? "selected" : "") + '>' + label + '</option>';
    }

    function setupAutoScanStrategy() {
        if (autoScanTimer) {
            clearInterval(autoScanTimer);
            autoScanTimer = null;
        }
        var strategy = loadAutoStrategy();
        if (!strategy.enabled || !AppAuth.hasPermission("patch-security:scan")) {
            return;
        }
        autoScanTimer = setInterval(function () {
            runAutoScan(strategy);
        }, Math.max(strategy.periodMinutes || 60, 1) * 60 * 1000);
    }

    async function runAutoScan(strategy) {
        try {
            var body = strategy.scanOnlyRiskHosts ? {hostIds: getCurrentRiskHostIds()} : {};
            await AppRequest.request(API.scanAll, {method: "POST", body: body}, {
                showErrorMessage: false
            });
            if (strategy.analyzeAfterScan && AppAuth.hasPermission("patch-security:analyze")) {
                await AppRequest.request(API.analyzeAll, {method: "POST", body: body}, {
                    showErrorMessage: false
                });
            }
            reloadPageData();
        } catch (error) {
            return;
        }
    }

    function getCurrentRiskHostIds() {
        var rows = table.cache[tableId] || [];
        return rows.map(function (row) { return row.hostId; }).filter(Boolean);
    }

    function loadAutoStrategy() {
        try {
            var stored = JSON.parse(localStorage.getItem(AUTO_STRATEGY_KEY) || "{}");
            return {
                enabled: stored.enabled === true,
                periodMinutes: stored.periodMinutes || 60,
                scanOnlyRiskHosts: stored.scanOnlyRiskHosts === true,
                analyzeAfterScan: stored.analyzeAfterScan === true,
                updatedAt: stored.updatedAt || ""
            };
        } catch (error) {
            return {
                enabled: false,
                periodMinutes: 60,
                scanOnlyRiskHosts: false,
                analyzeAfterScan: false,
                updatedAt: ""
            };
        }
    }

    function saveAutoStrategy(strategy) {
        localStorage.setItem(AUTO_STRATEGY_KEY, JSON.stringify(strategy));
    }

    function strategyStatusText(strategy) {
        if (!strategy.enabled) {
            return "未启用";
        }
        return "已启用，每 " + strategy.periodMinutes + " 分钟执行一次";
    }

    async function openRiskDetail(row) {
        var loadingIndex = layer.load(2);
        try {
            var result = await AppRequest.request(API.risks(row.hostId), {method: "GET"});
            layer.close(loadingIndex);
            var risks = result.data || [];
            layer.open({
                type: 1,
                title: false,
                shadeClose: true,
                area: [getDrawerWidth(), "100%"],
                offset: "r",
                anim: 5,
                content: buildDetailHtml(row, risks)
            });
        } catch (error) {
            layer.close(loadingIndex);
        }
    }

    async function postAction(url, body, message) {
        await AppRequest.request(url, {
            method: "POST",
            body: body || undefined
        }, {successMessage: message});
        reloadPageData();
    }

    function reloadPageData() {
        loadSummary();
        table.reloadData(tableId, {scrollPos: "fixed"});
    }

    function confirmAction(message, action) {
        AppDialog.confirm(layer, message, async function (index) {
            try {
                await action();
                layer.close(index);
            } catch (error) {
                return;
            }
        });
    }

    function buildDetailHtml(row, risks) {
        var riskCards = risks.length > 0
            ? risks.map(renderRiskCard).join("")
            : '<div class="risk-empty">当前主机没有开放补丁风险。</div>';
        return '<div class="risk-drawer">'
            + '<div class="risk-drawer-head">'
            + '<div>'
            + '<div class="section-kicker">Risk Evidence</div>'
            + '<h2>' + escapeHtml(row.hostname || "-") + '</h2>'
            + '<p>' + escapeHtml(row.ipv4 || "-") + ' · ' + escapeHtml(row.macAddress || "-") + '</p>'
            + '</div>'
            + renderRiskLevel(row.highestRiskLevel)
            + '</div>'
            + '<div class="risk-host-meta">'
            + '<span>系统：' + escapeHtml([row.osName, row.osVersion].filter(Boolean).join(" ") || "-") + '</span>'
            + '<span>Build：' + escapeHtml(row.osBuild || "-") + '</span>'
            + '<span>最近扫描：' + escapeHtml(AppUtils.formatDateTime(row.lastPatchScanTime)) + '</span>'
            + '</div>'
            + '<div class="risk-card-list">' + riskCards + '</div>'
            + '</div>';
    }

    function renderRiskCard(risk) {
        return '<article class="risk-card risk-card-' + escapeHtml((risk.riskLevel || "unknown").toLowerCase()) + '">'
            + '<div class="risk-card-head">'
            + '<div>'
            + '<h3>' + escapeHtml(risk.riskName || "-") + '</h3>'
            + '<div class="risk-subline">'
            + '<span>' + escapeHtml(typeName(risk.riskType)) + '</span>'
            + (risk.relatedPatchId ? '<span>' + escapeHtml(risk.relatedPatchId) + '</span>' : '')
            + (risk.relatedCve ? '<span>' + escapeHtml(risk.relatedCve) + '</span>' : '')
            + '</div>'
            + '</div>'
            + renderRiskLevel(risk.riskLevel)
            + '</div>'
            + '<div class="risk-section">'
            + '<span>风险证据</span>'
            + '<p>' + escapeHtml(risk.evidence || "-") + '</p>'
            + '</div>'
            + '<div class="risk-section recommendation">'
            + '<span>修复建议</span>'
            + '<p>' + escapeHtml(risk.recommendation || "-") + '</p>'
            + '</div>'
            + '<div class="risk-time">扫描时间：' + escapeHtml(AppUtils.formatDateTime(risk.scanTime)) + '</div>'
            + '</article>';
    }

    function renderRiskLevel(level) {
        var normalized = (level || "unknown").toLowerCase();
        return '<span class="risk-level risk-level-' + normalized + '">' + escapeHtml(normalized.toUpperCase()) + '</span>';
    }

    function renderRiskTypes(value) {
        if (!value) {
            return '<span class="muted">-</span>';
        }
        return String(value).split(",").filter(Boolean).map(function (type) {
            return '<span class="risk-type-tag">' + escapeHtml(typeName(type)) + '</span>';
        }).join("");
    }

    function normalizeFilters(fields) {
        var data = {};
        Object.keys(fields || {}).forEach(function (key) {
            if (fields[key] !== "") {
                data[key] = fields[key];
            }
        });
        return data;
    }

    function typeName(type) {
        var map = {
            missing_patch: "缺失基线",
            bad_patch: "已知问题",
            patch_not_effective: "补丁未生效",
            patch_install_failure: "安装失败",
            eol: "停止支持"
        };
        return map[type] || type || "-";
    }

    function formatMetricValue(value) {
        if (value == null || value === "") {
            return "0";
        }
        return value;
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
