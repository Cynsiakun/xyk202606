layui.use(["layer", "laypage", "table", "form"], function () {
    var layer = layui.layer;
    var laypage = layui.laypage;
    var table = layui.table;
    var form = layui.form;

    var state = {page: 1, size: 12, keyword: "", level: "", total: 0};
    var hosts = [];
    var hostMap = {};
    var selectedHostIds = {};
    var canRemediate = AppAuth.hasPermission("baseline:remediate");
    var canScan = AppAuth.hasPermission("baseline:create");

    init();

    function init() {
        bindToolbar();
        loadGrid();
        startPolling();
    }

    function bindToolbar() {
        document.getElementById("refreshButton").addEventListener("click", loadGrid);

        document.getElementById("searchButton").addEventListener("click", function () {
            state.keyword = document.getElementById("keywordInput").value.trim();
            state.page = 1;
            loadGrid();
        });
        document.getElementById("keywordInput").addEventListener("keydown", function (e) {
            if (e.key === "Enter") {
                state.keyword = this.value.trim();
                state.page = 1;
                loadGrid();
            }
        });

        form.on("select(levelFilter)", function (data) {
            state.level = data.value || "";
            state.page = 1;
            loadGrid();
        });

        var batchScan = document.getElementById("batchScanButton");
        if (canScan) {
            batchScan.addEventListener("click", batchScan_onClick);
        } else {
            batchScan.style.display = "none";
        }
    }

    function startPolling() {
        setInterval(function () {
            if (document.visibilityState === "visible") {
                loadGrid(true);
            }
        }, 10000);
    }

    /* ============ 卡片网格 ============ */

    function loadGrid(silent) {
        var url = "/api/baseline/hosts?page=" + state.page + "&size=" + state.size
            + "&keyword=" + encodeURIComponent(state.keyword)
            + "&level=" + encodeURIComponent(state.level);
        AppRequest.request(url, {method: "GET"}, {showErrorMessage: !silent})
            .then(function (res) {
                var data = res.data || {};
                hosts = data.list || [];
                state.total = data.total || 0;
                hostMap = {};
                hosts.forEach(function (h) {
                    hostMap[h.hostId] = h;
                });
                renderGrid();
                renderPager();
            })
            .catch(function () {
                if (!silent) {
                    document.getElementById("hostCardGrid").innerHTML =
                        '<div class="grid-empty">加载失败，请稍后重试</div>';
                }
            });
    }

    function renderGrid() {
        var grid = document.getElementById("hostCardGrid");
        if (hosts.length === 0) {
            grid.innerHTML = '<div class="grid-empty">暂无主机合规数据</div>';
            updateSelectedCount();
            return;
        }
        grid.innerHTML = hosts.map(buildCard).join("");
        bindCardEvents(grid);
        updateSelectedCount();
    }

    function buildCard(host) {
        var rate = host.complianceRate;
        var level = rateLevel(rate);
        var deg = (rate == null || rate === "") ? 0 : Math.max(0, Math.min(100, Number(rate))) * 3.6;
        var checked = selectedHostIds[host.hostId] ? "checked" : "";
        var selectedCls = selectedHostIds[host.hostId] ? " selected" : "";
        var total = host.totalCount != null ? host.totalCount : 0;
        var pass = host.passCount != null ? host.passCount : 0;
        var fail = host.failCount != null ? host.failCount : 0;

        return '<div class="host-card' + selectedCls + '" data-host-id="' + host.hostId + '">'
            + '<div class="card-head">'
            + '<label class="card-check"><input type="checkbox" value="' + host.hostId + '" ' + checked + '></label>'
            + '<div class="host-id-meta">'
            + '<div class="host-name" title="' + escapeHtml(host.hostName || "") + '">'
            + escapeHtml(host.hostName || ("主机#" + host.hostId)) + '</div>'
            + '<div class="host-ip">' + escapeHtml(host.ipv4 || "-") + '</div>'
            + '</div>'
            + '<div class="host-os" title="' + escapeHtml(host.osName || "") + '">' + escapeHtml(host.osName || "未知系统") + '</div>'
            + '</div>'
            + '<div class="card-body">'
            + '<div class="rate-ring ' + level + '" style="--deg:' + deg + 'deg;">'
            + '<span class="ring-num">' + (level === "none" ? "无数据" : formatRate(rate)) + '</span>'
            + '</div>'
            + '<div class="count-group">'
            + countRow("检测总数", total, "")
            + countRow("符合", pass, "pass")
            + countRow("不符合", fail, "fail")
            + '</div>'
            + '</div>'
            + '<div class="card-foot">'
            + '<span class="last-scan">最后检测 ' + (host.lastScanTime ? AppUtils.formatDateTime(host.lastScanTime) : "-") + '</span>'
            + '<div class="card-actions">'
            + '<button type="button" class="layui-btn layui-btn-primary layui-btn-xs" data-event="detail">查看详情</button>'
            + (canScan ? '<button type="button" class="layui-btn layui-btn-xs" data-event="scan">检测</button>' : "")
            + '</div>'
            + '</div>'
            + '</div>';
    }

    function countRow(label, value, cls) {
        return '<div class="count-row"><span class="c-label">' + label + '</span>'
            + '<span class="c-val ' + cls + '">' + value + '</span></div>';
    }

    function bindCardEvents(grid) {
        grid.querySelectorAll(".host-card").forEach(function (card) {
            var hostId = Number(card.getAttribute("data-host-id"));
            var checkbox = card.querySelector(".card-check input");
            checkbox.addEventListener("change", function () {
                if (this.checked) {
                    selectedHostIds[hostId] = true;
                    card.classList.add("selected");
                } else {
                    delete selectedHostIds[hostId];
                    card.classList.remove("selected");
                }
                updateSelectedCount();
            });
            card.querySelector('[data-event="detail"]').addEventListener("click", function () {
                openDetailDialog(hostMap[hostId]);
            });
            var scanBtn = card.querySelector('[data-event="scan"]');
            if (scanBtn) {
                scanBtn.addEventListener("click", function () {
                    scanHosts([hostId], hostMap[hostId] ? hostMap[hostId].hostName : null);
                });
            }
        });
    }

    function renderPager() {
        laypage.render({
            elem: "hostPager",
            count: state.total,
            curr: state.page,
            limit: state.size,
            limits: [12, 24, 48],
            layout: ["count", "prev", "page", "next", "limit", "skip"],
            jump: function (obj, first) {
                if (first) {
                    return;
                }
                state.page = obj.curr;
                state.size = obj.limit;
                loadGrid();
            }
        });
    }

    function updateSelectedCount() {
        document.getElementById("selectedCount").textContent = Object.keys(selectedHostIds).length;
    }

    /* ============ 立即检测 ============ */

    function batchScan_onClick() {
        var ids = Object.keys(selectedHostIds).map(Number);
        if (ids.length === 0) {
            AppRequest.showMessage("请先勾选主机", 2);
            return;
        }
        AppDialog.confirm(layer, "确认对已选 " + ids.length + " 台主机下发基线检测？", function (idx) {
            layer.close(idx);
            scanHosts(ids, null);
        });
    }

    function scanHosts(hostIds, hostName) {
        var tip = hostName ? ("主机 [" + hostName + "]") : (hostIds.length + " 台主机");
        AppRequest.request("/api/baseline/hosts/scan", {method: "POST", body: {hostIds: hostIds}},
            {showErrorMessage: true})
            .then(function (res) {
                var d = res.data || {};
                AppRequest.showMessage(d.message || ("已对 " + tip + " 下发检测"), 1, 2200);
                setTimeout(function () {
                    loadGrid(true);
                }, 1500);
            });
    }

    /* ============ 主机详情 ============ */

    function openDetailDialog(host) {
        if (!host) {
            return;
        }
        var viewportHeight = window.innerHeight || 720;
        var viewportWidth = window.innerWidth || 960;
        var dialogHeight = Math.min(720, viewportHeight - 30);
        var dialogWidth = Math.min(1000, viewportWidth - 30);

        var ctx = {hostId: host.hostId, onlyFail: true, rows: [], pollTimer: null};

        layer.open({
            type: 1,
            title: "主机详情 - " + (host.hostName || ("#" + host.hostId)),
            area: [dialogWidth + "px", dialogHeight + "px"],
            content: AppUtils.getTemplateHtml("hostDetailTemplate"),
            success: function (layero, index) {
                var root = layero[0];
                ctx.index = index;
                renderDetailOverview(root, host);
                bindDetailToolbar(root, ctx);
                table.on("tool(hostResultTable)", function (obj) {
                    if (obj.event === "fix") {
                        autoFix(ctx, [obj.data.resultId]);
                    } else if (obj.event === "rollback") {
                        rollback(ctx, [obj.data.resultId]);
                    } else if (obj.event === "recheck") {
                        recheck(ctx, [obj.data.resultId]);
                    } else if (obj.event === "evidence") {
                        openEvidenceDialog(obj.data);
                    } else if (obj.event === "ticket") {
                        openWorkorderDialog(ctx, [obj.data.resultId]);
                    }
                });
                ctx.root = root;
                loadResults(root, ctx);
                ctx.pollTimer = setInterval(function () {
                    if (hasInProgress(ctx.rows)) {
                        loadResults(root, ctx, true);
                    }
                }, 10000);
            },
            end: function () {
                if (ctx.pollTimer) {
                    clearInterval(ctx.pollTimer);
                }
            }
        });
    }

    function renderDetailOverview(root, host) {
        var rate = host.complianceRate;
        var level = rateLevel(rate);
        root.querySelector("#detailOverview").innerHTML =
            ovItem("主机 / IP", escapeHtml(host.hostName || ("#" + host.hostId)) + "<br><span style='font-size:12px;color:#667085;'>" + escapeHtml(host.ipv4 || "-") + "</span>", "")
            + ovItem("合规率", formatRate(rate), "rate " + (level === "none" ? "" : level))
            + ovItem("符合 / 检测", (host.passCount != null ? host.passCount : 0) + " / " + (host.totalCount != null ? host.totalCount : 0), "pass")
            + ovItem("不符合", (host.failCount != null ? host.failCount : 0), "fail");
    }

    function ovItem(label, value, valCls) {
        return '<div class="ov-item"><div class="lab">' + label + '</div>'
            + '<div class="val ' + valCls + '">' + value + '</div></div>';
    }

    function bindDetailToolbar(root, ctx) {
        root.querySelectorAll("#failSeg .seg-btn").forEach(function (btn) {
            btn.addEventListener("click", function () {
                root.querySelectorAll("#failSeg .seg-btn").forEach(function (b) {
                    b.classList.remove("active");
                });
                this.classList.add("active");
                ctx.onlyFail = this.getAttribute("data-fail") === "true";
                loadResults(root, ctx);
            });
        });

        var batch = root.querySelector("#detailBatch");
        if (!canRemediate && !canScan) {
            batch.style.display = "none";
            return;
        }
        bindBatchButton(root, "#batchFixBtn", canRemediate, function () {
            batchAction(ctx, "fix");
        });
        bindBatchButton(root, "#batchRollbackBtn", canRemediate, function () {
            batchAction(ctx, "rollback");
        });
        bindBatchButton(root, "#batchRecheckBtn", canScan, function () {
            batchAction(ctx, "recheck");
        });
        bindBatchButton(root, "#batchTicketBtn", canRemediate, function () {
            batchAction(ctx, "ticket");
        });
    }

    function bindBatchButton(root, selector, visible, handler) {
        var btn = root.querySelector(selector);
        if (!btn) {
            return;
        }
        if (!visible) {
            btn.style.display = "none";
            return;
        }
        btn.addEventListener("click", handler);
    }

    function loadResults(root, ctx, silent) {
        var url = "/api/baseline/hosts/" + ctx.hostId + "/results?onlyFail=" + ctx.onlyFail;
        AppRequest.request(url, {method: "GET"}, {showErrorMessage: !silent})
            .then(function (res) {
                ctx.rows = res.data || [];
                renderResultTable(root, ctx);
            });
    }

    function renderResultTable(root, ctx) {
        table.render({
            elem: root.querySelector("#hostResultTable"),
            data: ctx.rows,
            page: true,
            limit: 10,
            limits: [10, 20, 50],
            cols: [[
                {type: "checkbox", width: 45},
                {field: "ruleName", title: "规则名称", minWidth: 150, templet: function (d) {
                    return escapeHtml(d.ruleName || ("规则#" + d.ruleId));
                }},
                {field: "category", title: "分类", width: 100, templet: function (d) {
                    return escapeHtml(d.category || "-");
                }},
                {field: "checkKey", title: "检测项", minWidth: 130, templet: function (d) {
                    return escapeHtml(d.checkKey || "-");
                }},
                {field: "expectedValue", title: "期望值", width: 110, templet: function (d) {
                    return escapeHtml(d.expectedValue || "-");
                }},
                {field: "actualValue", title: "实际值", width: 110, templet: function (d) {
                    return escapeHtml(d.actualValue || "-");
                }},
                {field: "status", title: "状态", width: 80, align: "center", templet: function (d) {
                    return buildResultStatus(d.status);
                }},
                {field: "evidence", title: "证据", minWidth: 180, templet: function (d) {
                    return buildEvidenceCell(d);
                }},
                {title: "操作", width: 210, align: "center", fixed: "right", templet: buildRowActions}
            ]]
        });
    }

    function buildRowActions(d) {
        var rs = (d.remediationStatus || "").toUpperCase();
        if (isFixedStatus(rs)) {
            var afterFixBtns = [];
            if (canRemediate) {
                afterFixBtns.push('<button class="layui-btn layui-btn-xs layui-btn-warm" lay-event="rollback">回滚</button>');
            }
            if (canScan) {
                afterFixBtns.push('<button class="layui-btn layui-btn-xs layui-btn-primary" lay-event="recheck">复检</button>');
            }
            return afterFixBtns.length ? afterFixBtns.join(" ") : '<span class="rmd rmd-done">已修复</span>';
        }
        if (d.status === "PASS") {
            return '<span class="muted">—</span>';
        }
        if (rs === "IN_PROGRESS") {
            return '<span class="rmd rmd-progress">修复中</span>';
        }
        if (rs === "TICKETED") {
            return '<span class="rmd rmd-ticket">已派单</span>';
        }
        if (!canRemediate) {
            return '<span class="muted">—</span>';
        }
        var type = (d.remediationType || "").toUpperCase();
        var btns = [];
        if (type === "AUTO" || type === "SEMI") {
            btns.push('<button class="layui-btn layui-btn-xs" lay-event="fix">自动修复</button>');
        }
        if (type === "MANUAL" || type === "SEMI") {
            btns.push('<button class="layui-btn layui-btn-xs layui-btn-normal" lay-event="ticket">创建工单</button>');
        }
        return btns.length ? btns.join(" ") : '<span class="muted">—</span>';
    }

    function buildEvidenceCell(d) {
        var evidence = d.evidence || d.message || "";
        if (!evidence) {
            return '<span class="muted">—</span>';
        }
        var text = String(evidence);
        var preview = text.length > 48 ? text.slice(0, 48) + "..." : text;
        return '<div class="evidence-cell">'
            + '<span title="' + escapeHtml(text) + '">' + escapeHtml(preview) + '</span>'
            + '<button type="button" class="layui-btn layui-btn-primary layui-btn-xs evidence-btn" lay-event="evidence">完整</button>'
            + '</div>';
    }

    /* ============ 加固动作 ============ */

    function collectActionable(ctx, action) {
        var checked = table.checkStatus("hostResultTable").data;
        var ids = [];
        checked.forEach(function (d) {
            var rs = (d.remediationStatus || "").toUpperCase();
            if (rs === "IN_PROGRESS") {
                return;
            }
            if (action === "rollback") {
                if (isFixedStatus(rs)) {
                    ids.push(d.resultId);
                }
                return;
            }
            if (action === "recheck") {
                ids.push(d.resultId);
                return;
            }
            if (d.status === "PASS") {
                return;
            }
            if (isFixedStatus(rs) || rs === "TICKETED") {
                return;
            }
            var type = (d.remediationType || "").toUpperCase();
            if (action === "fix" && (type === "AUTO" || type === "SEMI")) {
                ids.push(d.resultId);
            } else if (action === "ticket" && (type === "MANUAL" || type === "SEMI")) {
                ids.push(d.resultId);
            }
        });
        return ids;
    }

    function batchAction(ctx, action) {
        var ids = collectActionable(ctx, action);
        if (ids.length === 0) {
            var tips = {
                fix: "请勾选支持自动修复的不合规项",
                rollback: "请勾选已修复且可回滚的项",
                recheck: "请先勾选需要复检的项",
                ticket: "请勾选支持创建工单的不合规项"
            };
            AppRequest.showMessage(tips[action] || "请选择要操作的项", 2);
            return;
        }
        if (action === "fix") {
            autoFix(ctx, ids);
        } else if (action === "rollback") {
            rollback(ctx, ids);
        } else if (action === "recheck") {
            recheck(ctx, ids);
        } else {
            openWorkorderDialog(ctx, ids);
        }
    }

    function autoFix(ctx, resultIds) {
        AppDialog.confirm(layer, "确认对所选 " + resultIds.length + " 项执行自动修复？", function (idx) {
            layer.close(idx);
            AppRequest.request("/api/baseline/remediations", {method: "POST", body: {resultIds: resultIds}},
                {showErrorMessage: true})
                .then(function (res) {
                    var d = res.data || {};
                    AppRequest.showMessage(d.message || "修复已下发", 1, 2200);
                    reloadDialogResults(ctx);
                });
        });
    }

    function rollback(ctx, resultIds) {
        AppDialog.confirm(layer, "确认回滚所选 " + resultIds.length + " 项修复？", function (idx) {
            layer.close(idx);
            AppRequest.request("/api/baseline/remediations/rollback", {method: "POST", body: {resultIds: resultIds}},
                {showErrorMessage: true})
                .then(function (res) {
                    var d = res.data || {};
                    AppRequest.showMessage(d.message || "回滚已下发", 1, 2200);
                    reloadDialogResults(ctx);
                });
        });
    }

    function recheck(ctx, resultIds) {
        AppRequest.request("/api/baseline/results/recheck", {method: "POST", body: {resultIds: resultIds}},
            {showErrorMessage: true})
            .then(function (res) {
                var d = res.data || {};
                AppRequest.showMessage(d.message || "复检已下发", 1, 2200);
                setTimeout(function () {
                    reloadDialogResults(ctx);
                }, 1500);
            });
    }

    function openEvidenceDialog(row) {
        var evidence = row.evidence || row.message || "暂无证据";
        layer.open({
            type: 1,
            title: "检测证据 - " + (row.ruleName || ("规则#" + row.ruleId)),
            area: ["720px", "520px"],
            content: '<pre class="evidence-full">' + escapeHtml(evidence) + '</pre>'
        });
    }

    function openWorkorderDialog(ctx, resultIds) {
        var idx = layer.open({
            type: 1,
            title: "创建整改工单",
            area: ["460px", "330px"],
            content: AppUtils.getTemplateHtml("workorderFormTemplate"),
            success: function (layero) {
                var root = layero[0];
                root.querySelector("#workorderTip").textContent = "将为 " + resultIds.length + " 项不合规规则创建工单";
                root.querySelector('[data-action="close"]').addEventListener("click", function () {
                    layer.close(idx);
                });
                root.querySelector("#submitWorkorderButton").addEventListener("click", function () {
                    var assignee = (root.querySelector('input[name="assignee"]').value || "").trim();
                    var remark = (root.querySelector('textarea[name="remark"]').value || "").trim();
                    if (!assignee) {
                        AppRequest.showMessage("请输入负责人", 2);
                        return;
                    }
                    var btn = this;
                    btn.disabled = true;
                    btn.classList.add("layui-btn-disabled");
                    AppRequest.request("/api/baseline/workorders",
                        {method: "POST", body: {resultIds: resultIds, assignee: assignee, remark: remark}},
                        {showErrorMessage: true})
                        .then(function (res) {
                            var d = res.data || {};
                            layer.close(idx);
                            AppRequest.showMessage(d.message || "工单已创建", 1, 2200);
                            reloadDialogResults(ctx);
                        })
                        .catch(function () {
                            btn.disabled = false;
                            btn.classList.remove("layui-btn-disabled");
                        });
                });
            }
        });
    }

    function reloadDialogResults(ctx) {
        if (ctx.root) {
            loadResults(ctx.root, ctx, true);
        }
        loadGrid(true);
    }

    /* ============ 工具函数 ============ */

    function hasInProgress(rows) {
        return (rows || []).some(function (r) {
            return (r.remediationStatus || "").toUpperCase() === "IN_PROGRESS";
        });
    }

    function isFixedStatus(status) {
        return status === "COMPLETED" || status === "FIXED" || status === "SUCCESS";
    }

    function buildResultStatus(status) {
        var map = {
            PASS: {cls: "pass", text: "PASS"},
            FAIL: {cls: "fail", text: "FAIL"},
            ERROR: {cls: "error", text: "ERROR"},
            UNKNOWN: {cls: "error", text: "UNKNOWN"}
        };
        var item = map[status] || {cls: "error", text: status || "-"};
        return '<span class="res-status ' + item.cls + '">' + item.text + '</span>';
    }

    function rateLevel(rate) {
        if (rate == null || rate === "") {
            return "none";
        }
        var num = Number(rate);
        return num >= 90 ? "good" : (num >= 60 ? "warn" : "bad");
    }

    function formatRate(rate) {
        if (rate == null || rate === "") {
            return "-";
        }
        return Number(rate).toFixed(0) + "%";
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
