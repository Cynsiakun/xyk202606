layui.use(["table", "form", "layer"], function () {
    var table = layui.table;
    var form = layui.form;
    var layer = layui.layer;

    var tableId = "workorderTable";
    var state = {page: 1, size: 10, keyword: "", status: "", priority: ""};
    var currentDetail = null;

    init();

    function init() {
        bindToolbar();
        bindDetailActions();
        renderTable();
    }

    function bindToolbar() {
        document.getElementById("searchButton").addEventListener("click", applyFilters);
        document.getElementById("refreshButton").addEventListener("click", function () {
            reloadTable(false);
        });
        document.getElementById("workorderKeywordInput").addEventListener("keydown", function (event) {
            if (event.key === "Enter") {
                applyFilters();
            }
        });
        form.on("select(statusFilter)", function (data) {
            state.status = data.value || "";
            state.page = 1;
            reloadTable(false);
        });
        form.on("select(priorityFilter)", function (data) {
            state.priority = data.value || "";
            state.page = 1;
            reloadTable(false);
        });
    }

    function bindDetailActions() {
        document.getElementById("backToListButton").addEventListener("click", function () {
            currentDetail = null;
            document.getElementById("workorderDetailPanel").style.display = "none";
            document.getElementById("workorderListToolbar").style.display = "";
            document.getElementById("workorderListPanel").style.display = "";
            reloadTable(true);
        });
        document.getElementById("detailRefreshButton").addEventListener("click", function () {
            if (currentDetail) {
                openDetail(currentDetail.id);
            }
        });
        document.getElementById("startButton").addEventListener("click", function () {
            if (currentDetail) {
                doAction("/api/baseline/workorders/" + currentDetail.id + "/start", "工单已开始处理");
            }
        });
        document.getElementById("completeButton").addEventListener("click", openCompleteDialog);
        document.getElementById("recheckButton").addEventListener("click", function () {
            if (currentDetail) {
                doAction("/api/baseline/workorders/" + currentDetail.id + "/recheck", "已下发重新检测");
            }
        });
    }

    function applyFilters() {
        state.keyword = document.getElementById("workorderKeywordInput").value.trim();
        state.page = 1;
        reloadTable(false);
    }

    function renderTable() {
        table.render({
            elem: "#" + tableId,
            id: tableId,
            url: "/api/baseline/workorders",
            method: "GET",
            headers: {Authorization: "Bearer " + AppAuth.getToken()},
            page: true,
            curr: state.page,
            limit: state.size,
            limits: [10, 20, 50],
            request: {pageName: "page", limitName: "size"},
            where: buildQuery(),
            parseData: parsePageData,
            cols: [[
                {field: "id", title: "工单ID", width: 90, align: "center"},
                {field: "title", title: "标题", minWidth: 220, templet: function (d) {
                    return escapeHtml(d.title || "-");
                }},
                {field: "hostName", title: "主机名称", minWidth: 150, templet: function (d) {
                    return escapeHtml(d.hostName || d.ipv4 || ("主机#" + d.hostId));
                }},
                {field: "ruleName", title: "规则名称", minWidth: 190, templet: function (d) {
                    return escapeHtml(d.ruleName || ("规则#" + d.ruleId));
                }},
                {field: "assigneeName", title: "处理人", width: 120, templet: function (d) {
                    return escapeHtml(d.assigneeName || "-");
                }},
                {field: "priority", title: "优先级", width: 105, align: "center", templet: function (d) {
                    return priorityTag(d.priority);
                }},
                {field: "status", title: "状态", width: 105, align: "center", templet: function (d) {
                    return statusTag(d.status);
                }},
                {field: "createTime", title: "创建时间", width: 170, templet: function (d) {
                    return AppUtils.formatDateTime(d.createTime);
                }},
                {field: "finishTime", title: "完成时间", width: 170, templet: function (d) {
                    return AppUtils.formatDateTime(d.finishTime);
                }},
                {title: "操作", width: 220, fixed: "right", align: "center", templet: function (d) {
                    var html = '<button class="layui-btn layui-btn-primary layui-btn-xs" lay-event="detail">查看详情</button>';
                    if (d.status === "OPEN") {
                        html += '<button class="layui-btn layui-btn-xs" lay-event="start">开始处理</button>';
                    }
                    if (d.status === "PROCESSING") {
                        html += '<button class="layui-btn layui-btn-warm layui-btn-xs" lay-event="complete">完成工单</button>';
                    }
                    if (d.status === "DONE") {
                        html += '<button class="layui-btn layui-btn-normal layui-btn-xs" lay-event="recheck">重新检测</button>';
                    }
                    return html;
                }}
            ]],
            done: function (res, curr) {
                state.page = curr;
                state.size = this.limit || state.size;
            }
        });
        table.on("tool(" + tableId + ")", function (obj) {
            if (obj.event === "detail") {
                openDetail(obj.data.id);
            } else if (obj.event === "start") {
                doAction("/api/baseline/workorders/" + obj.data.id + "/start", "工单已开始处理");
            } else if (obj.event === "complete") {
                currentDetail = obj.data;
                openCompleteDialog();
            } else if (obj.event === "recheck") {
                doAction("/api/baseline/workorders/" + obj.data.id + "/recheck", "已下发重新检测");
            }
        });
    }

    function reloadTable(silent) {
        table.reload(tableId, {
            page: {curr: state.page},
            limit: state.size,
            where: buildQuery()
        }, silent);
    }

    function buildQuery() {
        return {keyword: state.keyword, status: state.status, priority: state.priority};
    }

    function parsePageData(res) {
        if (res.code !== 200) {
            AppRequest.showMessage(res.message || "请求失败", 2, 2200);
        }
        var pageData = res.data || {};
        return {code: res.code === 200 ? 0 : res.code, msg: res.message, count: pageData.total || 0, data: pageData.list || []};
    }

    function openDetail(id) {
        AppRequest.request("/api/baseline/workorders/" + encodeURIComponent(id), {method: "GET"}, {showErrorMessage: true})
            .then(function (res) {
                currentDetail = res.data || {};
                document.getElementById("workorderListToolbar").style.display = "none";
                document.getElementById("workorderListPanel").style.display = "none";
                document.getElementById("workorderDetailPanel").style.display = "";
                renderDetail(currentDetail);
            });
    }

    function renderDetail(d) {
        document.getElementById("detailTitle").textContent = d.title || ("工单#" + d.id);
        document.getElementById("startButton").style.display = d.status === "OPEN" ? "" : "none";
        document.getElementById("completeButton").style.display = d.status === "PROCESSING" ? "" : "none";
        document.getElementById("recheckButton").style.display = d.status === "DONE" ? "" : "none";
        document.getElementById("detailBody").innerHTML =
            '<div class="detail-grid">'
            + detailItem("工单标题", d.title)
            + detailItem("主机信息", (d.hostName || "主机#" + d.hostId) + " / " + (d.ipv4 || "-") + " / " + (d.macAddress || "-"))
            + detailItem("规则名称", d.ruleName || ("规则#" + d.ruleId))
            + detailItem("分类", d.category || "-")
            + detailItem("优先级", priorityTag(d.priority), true)
            + detailItem("处理人", d.assigneeName || "-")
            + detailItem("状态", statusTag(d.status), true)
            + detailItem("创建时间", AppUtils.formatDateTime(d.createTime))
            + detailItem("开始时间", AppUtils.formatDateTime(d.startTime))
            + detailItem("完成时间", AppUtils.formatDateTime(d.finishTime))
            + detailItem("创建人", d.creatorName || "-")
            + detailItem("检测状态", resultStatusTag(d.resultStatus), true)
            + '</div>'
            + '<div class="detail-section"><h3>修复建议</h3><pre>' + escapeHtml(d.advice || "暂无修复建议") + '</pre></div>'
            + '<div class="detail-section"><h3>检测上下文</h3><pre>' + escapeHtml(buildContext(d)) + '</pre></div>'
            + '<div class="detail-section"><h3>处理说明</h3><pre>' + escapeHtml(d.closeRemark || "尚未填写") + '</pre></div>';
    }

    function openCompleteDialog() {
        if (!currentDetail) {
            return;
        }
        var idx = layer.open({
            type: 1,
            title: "完成工单",
            area: ["560px", "360px"],
            content: AppUtils.getTemplateHtml("completeFormTemplate"),
            success: function (layero) {
                var root = layero[0];
                root.querySelector('[data-action="close"]').addEventListener("click", function () {
                    layer.close(idx);
                });
                root.querySelector("#submitCompleteButton").addEventListener("click", function () {
                    var remark = (root.querySelector('textarea[name="closeRemark"]').value || "").trim();
                    if (!remark) {
                        AppRequest.showMessage("请填写处理说明", 2);
                        return;
                    }
                    var btn = this;
                    btn.disabled = true;
                    btn.classList.add("layui-btn-disabled");
                    AppRequest.request("/api/baseline/workorders/" + currentDetail.id + "/complete",
                        {method: "POST", body: {closeRemark: remark}},
                        {showErrorMessage: true})
                        .then(function (res) {
                            layer.close(idx);
                            AppRequest.showMessage((res.data || {}).message || "工单已完成", 1, 1800);
                            openDetail(currentDetail.id);
                        })
                        .catch(function () {
                            btn.disabled = false;
                            btn.classList.remove("layui-btn-disabled");
                        });
                });
            }
        });
    }

    function doAction(url, message) {
        AppRequest.request(url, {method: "POST"}, {showErrorMessage: true})
            .then(function (res) {
                AppRequest.showMessage((res.data || {}).message || message, 1, 1800);
                if (currentDetail && document.getElementById("workorderDetailPanel").style.display !== "none") {
                    openDetail(currentDetail.id);
                } else {
                    reloadTable(true);
                }
            });
    }

    function detailItem(label, value, raw) {
        return '<div class="detail-item"><label>' + escapeHtml(label) + '</label><div>' + (raw ? value : escapeHtml(value || "-")) + '</div></div>';
    }

    function buildContext(d) {
        return [
            "检测项：" + (d.checkKey || "-"),
            "期望值：" + (d.expectedValue || "-"),
            "实际值：" + (d.actualValue || "-"),
            "证据：" + (d.evidence || "-")
        ].join("\n");
    }

    function statusTag(status) {
        var map = {OPEN: ["open", "待处理"], PROCESSING: ["processing", "处理中"], DONE: ["done", "已完成"]};
        var item = map[status] || ["open", status || "-"];
        return '<span class="wo-status ' + item[0] + '">' + item[1] + '</span>';
    }

    function priorityTag(priority) {
        var value = String(priority || "MEDIUM").toUpperCase();
        return '<span class="wo-priority ' + value.toLowerCase() + '">' + value + '</span>';
    }

    function resultStatusTag(status) {
        var value = String(status || "-").toUpperCase();
        return '<span class="result-status ' + value.toLowerCase() + '">' + value + '</span>';
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
