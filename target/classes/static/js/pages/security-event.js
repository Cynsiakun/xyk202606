layui.use(["table", "form", "layer", "element", "laydate"], function () {
    var table = layui.table;
    var form = layui.form;
    var layer = layui.layer;
    var element = layui.element;
    var laydate = layui.laydate;

    var TABLE_ID = "eventTable";
    var API = {
        list: "/api/security-event/list",
        stats: "/api/security-event/stats",
        detail: "/api/security-event/detail/",
        hosts: "/api/security-event/hosts",
        ack: "/api/security-event/ack",
        resolve: "/api/security-event/resolve",
        export: "/api/security-event/export"
    };

    var state = {
        level: "",
        status: "",
        keyword: "",
        adv: {eventId: "", hostId: "", alertName: "", startTime: "", endTime: ""},
        sortField: "",
        sortOrder: ""
    };
    var hostOptions = [];
    var detailLayerIndex = null;

    init();

    function init() {
        renderTable();
        loadStats();
        loadHostOptions();
        bindEvents();
        handleFocusParam();
    }

    function escapeHtml(value) {
        if (value == null) {
            return "";
        }
        return String(value)
            .replace(/&/g, "&amp;").replace(/</g, "&lt;").replace(/>/g, "&gt;")
            .replace(/"/g, "&quot;").replace(/'/g, "&#39;");
    }

    function buildWhere() {
        var where = {};
        if (state.level) { where.level = state.level; }
        if (state.status) { where.status = state.status; }
        if (state.keyword) { where.keyword = state.keyword; }
        if (state.adv.eventId) { where.eventId = state.adv.eventId; }
        if (state.adv.hostId) { where.hostId = state.adv.hostId; }
        if (state.adv.alertName) { where.alertName = state.adv.alertName; }
        if (state.adv.startTime) { where.startTime = state.adv.startTime; }
        if (state.adv.endTime) { where.endTime = state.adv.endTime; }
        if (state.sortField) {
            where.sortField = state.sortField;
            where.sortOrder = state.sortOrder;
        }
        return where;
    }

    function renderTable() {
        table.render({
            elem: "#" + TABLE_ID,
            url: API.list,
            method: "GET",
            headers: {Authorization: "Bearer " + AppAuth.getToken()},
            page: true,
            limit: 20,
            limits: [20, 50, 100],
            autoSort: false,
            request: {pageName: "page", limitName: "size"},
            where: buildWhere(),
            parseData: function (res) {
                if (res.code !== 200) {
                    AppRequest.showMessage(res.message || "请求失败", 2, 2200);
                    if ((res.code === 401 || res.code === 403) && window.AppAuth) {
                        AppAuth.clearLogin();
                        AppAuth.redirectToLogin();
                    }
                }
                var pageData = res.data || {};
                return {
                    code: res.code === 200 ? 0 : res.code,
                    msg: res.message,
                    count: pageData.total || 0,
                    data: pageData.list || []
                };
            },
            text: {none: "暂无符合条件的安全事件"},
            cols: [[
                {type: "checkbox", fixed: "left"},
                {field: "eventTime", title: "时间", width: 170, sort: true, templet: function (d) {
                    return AppUtils.formatDateTime(d.eventTime);
                }},
                {field: "hostname", title: "主机", minWidth: 150, templet: "#hostTpl"},
                {field: "alertName", title: "告警名称", minWidth: 180, templet: function (d) {
                    return escapeHtml(d.alertName) || "-";
                }},
                {field: "level", title: "等级", width: 110, sort: true, templet: "#levelTpl"},
                {field: "description", title: "描述", minWidth: 260, templet: function (d) {
                    return '<span class="msg-cell" title="' + escapeHtml(d.description) + '">' + escapeHtml(d.description) + "</span>";
                }},
                {field: "status", title: "状态", width: 110, sort: true, templet: "#statusTpl"},
                {title: "操作", width: 190, fixed: "right", align: "center", templet: "#opTpl"}
            ]],
            done: function () {
                updateBatchHint();
            }
        });
    }

    function reloadTable(resetPage) {
        table.reload(TABLE_ID, {
            where: buildWhere(),
            page: resetPage === false ? undefined : {curr: 1}
        });
    }

    function loadStats() {
        AppRequest.request(API.stats, {method: "GET"}, {showErrorMessage: false})
            .then(function (res) {
                var d = res.data || {};
                document.getElementById("statCritical").textContent = d.critical || 0;
                document.getElementById("statHigh").textContent = d.high || 0;
                document.getElementById("statMedium").textContent = d.medium || 0;
                document.getElementById("statUntreated").textContent = d.untreated || 0;
            })
            .catch(function () { /* ignore */ });
    }

    function loadHostOptions() {
        AppRequest.request(API.hosts, {method: "GET"}, {showErrorMessage: false})
            .then(function (res) { hostOptions = res.data || []; })
            .catch(function () { hostOptions = []; });
    }

    function bindEvents() {
        document.getElementById("levelTabs").addEventListener("click", function (e) {
            var btn = e.target.closest(".evt-level-tab");
            if (!btn) { return; }
            state.level = btn.dataset.level;
            this.querySelectorAll(".evt-level-tab").forEach(function (el) {
                el.classList.toggle("is-active", el === btn);
            });
            reloadTable(true);
        });

        form.on("select(statusSelect)", function (data) {
            state.status = data.value;
            reloadTable(true);
        });

        document.getElementById("searchButton").addEventListener("click", function () {
            state.keyword = document.getElementById("keywordInput").value.trim();
            reloadTable(true);
        });
        document.getElementById("keywordInput").addEventListener("keydown", function (e) {
            if (e.key === "Enter") {
                state.keyword = this.value.trim();
                reloadTable(true);
            }
        });

        document.getElementById("refreshButton").addEventListener("click", function () {
            reloadTable(false);
            loadStats();
        });

        document.getElementById("advSearchButton").addEventListener("click", openAdvSearch);
        document.getElementById("exportButton").addEventListener("click", exportEvents);

        document.getElementById("batchAckButton").addEventListener("click", function () {
            batchUpdate("ack");
        });
        document.getElementById("batchResolveButton").addEventListener("click", function () {
            batchUpdate("resolve");
        });

        table.on("sort(eventTable)", function (obj) {
            state.sortField = obj.type ? obj.field : "";
            state.sortOrder = obj.type || "";
            reloadTable(true);
        });

        table.on("checkbox(eventTable)", updateBatchHint);

        table.on("row(eventTable)", function (obj) {
            openDetail(obj.data.id);
        });

        table.on("tool(eventTable)", function (obj) {
            if (obj.event === "detail") {
                openDetail(obj.data.id);
            } else if (obj.event === "ack") {
                singleUpdate("ack", obj.data.id);
            } else if (obj.event === "resolve") {
                singleUpdate("resolve", obj.data.id);
            }
        });
    }

    function updateBatchHint() {
        var checked = table.checkStatus(TABLE_ID).data || [];
        document.getElementById("batchHint").textContent = "已选 " + checked.length + " 条";
    }

    function getCheckedIds() {
        return (table.checkStatus(TABLE_ID).data || []).map(function (r) { return r.id; });
    }

    function batchUpdate(action) {
        var ids = getCheckedIds();
        if (ids.length === 0) {
            layer.msg("请先勾选要操作的事件", {icon: 0});
            return;
        }
        doUpdate(action, ids);
    }

    function singleUpdate(action, id) {
        doUpdate(action, [id]);
    }

    function doUpdate(action, ids) {
        var url = action === "ack" ? API.ack : API.resolve;
        var label = action === "ack" ? "确认" : "处理";
        AppRequest.request(url, {method: "POST", body: {ids: ids}}, {successMessage: label + "成功（" + ids.length + " 条）"})
            .then(function () {
                reloadTable(false);
                loadStats();
            })
            .catch(function () { /* 错误已由请求模块提示 */ });
    }

    function openAdvSearch() {
        layer.open({
            type: 1,
            title: "高级搜索",
            area: ["520px", "auto"],
            shadeClose: false,
            content: AppUtils.getTemplateHtml("advSearchTemplate"),
            success: function (layero) {
                var select = layero[0].querySelector("#advHostSelect");
                hostOptions.forEach(function (h) {
                    var opt = document.createElement("option");
                    opt.value = h.id;
                    opt.textContent = h.hostname ? (h.hostname + (h.ipv4 ? " / " + h.ipv4 : "")) : ("主机#" + h.id);
                    select.appendChild(opt);
                });
                form.val("advSearchForm", {
                    eventId: state.adv.eventId,
                    hostId: state.adv.hostId,
                    alertName: state.adv.alertName
                });
                form.render(null, "advSearchForm");

                var rangeInput = layero[0].querySelector("#advTimeRange");
                if (state.adv.startTime && state.adv.endTime) {
                    rangeInput.value = state.adv.startTime + " - " + state.adv.endTime;
                }
                laydate.render({elem: rangeInput, type: "datetime", range: " - ", trigger: "click"});

                layero[0].querySelector("#advQueryButton").addEventListener("click", function () {
                    var field = form.val("advSearchForm");
                    state.adv.eventId = (field.eventId || "").trim();
                    state.adv.hostId = field.hostId || "";
                    state.adv.alertName = (field.alertName || "").trim();
                    var range = (rangeInput.value || "").trim();
                    if (range && range.indexOf(" - ") > -1) {
                        var parts = range.split(" - ");
                        state.adv.startTime = parts[0].trim();
                        state.adv.endTime = parts[1].trim();
                    } else {
                        state.adv.startTime = "";
                        state.adv.endTime = "";
                    }
                    renderActiveFilter();
                    reloadTable(true);
                    layer.closeAll();
                });

                layero[0].querySelector("#advResetButton").addEventListener("click", function () {
                    state.adv = {eventId: "", hostId: "", alertName: "", startTime: "", endTime: ""};
                    form.val("advSearchForm", {eventId: "", hostId: "", alertName: ""});
                    rangeInput.value = "";
                    form.render(null, "advSearchForm");
                    renderActiveFilter();
                    reloadTable(true);
                });
            }
        });
    }

    function renderActiveFilter() {
        var bar = document.getElementById("activeFilterBar");
        var chips = [];
        if (state.adv.eventId) { chips.push("EventID: " + state.adv.eventId); }
        if (state.adv.hostId) {
            var host = hostOptions.filter(function (h) { return String(h.id) === String(state.adv.hostId); })[0];
            chips.push("主机: " + (host && host.hostname ? host.hostname : "#" + state.adv.hostId));
        }
        if (state.adv.alertName) { chips.push("告警名称: " + state.adv.alertName); }
        if (state.adv.startTime && state.adv.endTime) {
            chips.push("时间: " + state.adv.startTime + " ~ " + state.adv.endTime);
        }
        if (chips.length === 0) {
            bar.style.display = "none";
            bar.innerHTML = "";
            return;
        }
        bar.style.display = "flex";
        bar.innerHTML = '<span class="active-filter-label">高级筛选：</span>'
            + chips.map(function (c) { return '<span class="filter-chip">' + escapeHtml(c) + "</span>"; }).join("")
            + '<button type="button" class="layui-btn layui-btn-primary layui-btn-xs" id="clearAdvFilter">清除</button>';
        var clearBtn = document.getElementById("clearAdvFilter");
        if (clearBtn) {
            clearBtn.addEventListener("click", function () {
                state.adv = {eventId: "", hostId: "", alertName: "", startTime: "", endTime: ""};
                renderActiveFilter();
                reloadTable(true);
            });
        }
    }

    function openDetail(id) {
        if (detailLayerIndex !== null) {
            layer.close(detailLayerIndex);
            detailLayerIndex = null;
        }
        detailLayerIndex = layer.open({
            type: 1,
            title: "安全事件详情",
            offset: "r",
            anim: -1,
            shadeClose: true,
            area: ["660px", "100%"],
            content: AppUtils.getTemplateHtml("eventDetailTemplate"),
            success: function (layero) {
                element.render("tab", "evtDetailTab");
                AppRequest.request(API.detail + encodeURIComponent(id), {method: "GET"})
                    .then(function (res) { fillDetail(layero[0], res.data || {}); })
                    .catch(function () {
                        layero[0].querySelector("#detailKvGrid").innerHTML = "<div class='detail-empty'>加载详情失败</div>";
                    });
            },
            end: function () { detailLayerIndex = null; }
        });
    }

    function kvHtml(rows) {
        return rows.map(function (r) {
            return '<div class="detail-kv-item">'
                + '<span class="detail-kv-label">' + escapeHtml(r[0]) + "</span>"
                + '<span class="detail-kv-value">' + escapeHtml(r[1] == null || r[1] === "" ? "-" : r[1]) + "</span>"
                + "</div>";
        }).join("");
    }

    function fillDetail(root, d) {
        root.querySelector("#detailKvGrid").innerHTML = kvHtml([
            ["告警ID", d.id],
            ["等级", d.level],
            ["状态", d.status],
            ["事件时间", AppUtils.formatDateTime(d.eventTime)],
            ["主机", d.hostname ? d.hostname : ("#" + (d.hostId == null ? "-" : d.hostId))],
            ["主机IP", d.ipv4 || "-"],
            ["EventID", d.eventId],
            ["规则编码", d.ruleCode || "-"],
            ["风险分", d.riskScore],
            ["关联日志ID", d.sourceLogId == null ? "-" : d.sourceLogId],
            ["入库时间", AppUtils.formatDateTime(d.createTime)]
        ]);
        root.querySelector("#detailDescription").textContent = d.description || "-";

        var hasLog = d.logMessage || d.rawXml;
        root.querySelector("#detailLogKvGrid").innerHTML = kvHtml([
            ["source_log_id", d.sourceLogId == null ? "-" : d.sourceLogId],
            ["日志类型", d.logType || "-"],
            ["日志级别", d.logLevel || "-"],
            ["日志用户", d.logUsername || "-"]
        ]);
        root.querySelector("#detailLogMessage").textContent = d.logMessage || (hasLog ? "-" : "（未获取到关联原始日志）");

        var rawXml = d.rawXml || "";
        root.querySelector("#detailRawXml").textContent = rawXml || "（无原始 XML）";
        var copyBtn = root.querySelector("#copyXmlButton");
        if (copyBtn) {
            copyBtn.addEventListener("click", function () { copyToClipboard(rawXml); });
        }
    }

    function copyToClipboard(text) {
        if (!text) { layer.msg("无可复制内容", {icon: 2}); return; }
        if (navigator.clipboard && navigator.clipboard.writeText) {
            navigator.clipboard.writeText(text).then(function () { layer.msg("已复制", {icon: 1}); })
                .catch(function () { fallbackCopy(text); });
        } else {
            fallbackCopy(text);
        }
    }

    function fallbackCopy(text) {
        var ta = document.createElement("textarea");
        ta.value = text;
        document.body.appendChild(ta);
        ta.select();
        try { document.execCommand("copy"); layer.msg("已复制", {icon: 1}); }
        catch (e) { layer.msg("复制失败", {icon: 2}); }
        document.body.removeChild(ta);
    }

    function buildExportQuery() {
        var where = buildWhere();
        var params = Object.keys(where).map(function (key) {
            return encodeURIComponent(key) + "=" + encodeURIComponent(where[key]);
        });
        return params.length ? "?" + params.join("&") : "";
    }

    async function exportEvents() {
        var token = AppAuth.getToken();
        var url = API.export + buildExportQuery();
        var loading = layer.load(2);
        try {
            var response = await fetch(url, {method: "GET", headers: token ? {Authorization: "Bearer " + token} : {}});
            if (!response.ok) {
                if (response.status === 401 || response.status === 403) {
                    AppAuth.clearLogin();
                    AppAuth.redirectToLogin();
                    return;
                }
                layer.msg("导出失败", {icon: 2});
                return;
            }
            var blob = await response.blob();
            triggerDownload(blob, "security_events_" + timestamp() + ".csv");
            layer.msg("导出成功", {icon: 1});
        } catch (e) {
            layer.msg(e.message || "导出失败", {icon: 2});
        } finally {
            layer.close(loading);
        }
    }

    function timestamp() {
        var now = new Date();
        function p(n) { return n < 10 ? "0" + n : String(n); }
        return now.getFullYear() + p(now.getMonth() + 1) + p(now.getDate())
            + p(now.getHours()) + p(now.getMinutes()) + p(now.getSeconds());
    }

    function triggerDownload(blob, fileName) {
        var url = URL.createObjectURL(blob);
        var link = document.createElement("a");
        link.href = url;
        link.download = fileName;
        document.body.appendChild(link);
        link.click();
        document.body.removeChild(link);
        URL.revokeObjectURL(url);
    }

    // 从全局告警弹窗「查看详情」跳转过来时，自动打开对应告警详情
    function handleFocusParam() {
        var match = (window.location.search || "").match(/[?&]focus=(\d+)/);
        if (match) {
            setTimeout(function () { openDetail(Number(match[1])); }, 400);
        }
    }
});
