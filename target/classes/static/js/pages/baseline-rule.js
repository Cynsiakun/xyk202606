layui.use(["table", "form", "layer"], function () {
    var table = layui.table;
    var form = layui.form;
    var layer = layui.layer;

    var tableId = "baselineRuleTable";
    var state = {page: 1, size: 10, keyword: "", category: "", severity: "", status: "", enabled: ""};
    var editingRule = null;

    init();

    function init() {
        bindToolbar();
        bindDrawer();
        renderTable();
    }

    function bindToolbar() {
        document.getElementById("searchButton").addEventListener("click", applyFilters);
        document.getElementById("refreshButton").addEventListener("click", function () {
            reloadTable(false);
        });
        document.getElementById("addButton").addEventListener("click", function () {
            openFormDrawer(null);
        });
        document.getElementById("keywordInput").addEventListener("keydown", function (event) {
            if (event.key === "Enter") {
                applyFilters();
            }
        });
        document.getElementById("categoryInput").addEventListener("keydown", function (event) {
            if (event.key === "Enter") {
                applyFilters();
            }
        });
        form.on("select(severityFilter)", function (data) {
            state.severity = data.value || "";
            state.page = 1;
            reloadTable(false);
        });
        form.on("select(statusFilter)", function (data) {
            state.status = data.value || "";
            state.page = 1;
            reloadTable(false);
        });
        form.on("select(enabledFilter)", function (data) {
            state.enabled = data.value || "";
            state.page = 1;
            reloadTable(false);
        });
    }

    function bindDrawer() {
        document.getElementById("drawerMask").addEventListener("click", closeDrawer);
        document.getElementById("drawerCloseButton").addEventListener("click", closeDrawer);
    }

    function applyFilters() {
        state.keyword = document.getElementById("keywordInput").value.trim();
        state.category = document.getElementById("categoryInput").value.trim();
        state.page = 1;
        reloadTable(false);
    }

    function renderTable() {
        table.render({
            elem: "#" + tableId,
            id: tableId,
            url: "/api/baseline/rule-management",
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
                {field: "ruleCode", title: "规则编码", width: 120, templet: function (d) {
                    return escapeHtml(d.ruleCode || "-");
                }},
                {field: "ruleName", title: "规则名称", minWidth: 220, templet: function (d) {
                    return escapeHtml(d.ruleName || "-");
                }},
                {field: "category", title: "分类", width: 120, templet: function (d) {
                    return escapeHtml(d.category || "-");
                }},
                {field: "osType", title: "适用系统", width: 105, templet: function (d) {
                    return escapeHtml(d.osType || "-");
                }},
                {field: "checkMethod", title: "检测方式", width: 120},
                {field: "severity", title: "风险等级", width: 105, align: "center", templet: function (d) {
                    return severityTag(d.severity);
                }},
                {field: "score", title: "评分", width: 75, align: "center"},
                {field: "status", title: "状态", width: 105, align: "center", templet: function (d) {
                    return statusTag(d.status);
                }},
                {field: "enabled", title: "启用状态", width: 95, align: "center", templet: function (d) {
                    return enabledTag(d.enabled);
                }},
                {field: "version", title: "版本", width: 75, align: "center"},
                {field: "createTime", title: "创建时间", width: 170, templet: function (d) {
                    return AppUtils.formatDateTime(d.createTime);
                }},
                {field: "updateTime", title: "更新时间", width: 170, templet: function (d) {
                    return AppUtils.formatDateTime(d.updateTime);
                }},
                {title: "操作", width: 220, fixed: "right", align: "center", templet: function (d) {
                    var html = '<button class="layui-btn layui-btn-primary layui-btn-xs" lay-event="detail">查看详情</button>'
                        + '<button class="layui-btn layui-btn-xs" lay-event="edit">编辑</button>';
                    html += '<button class="layui-btn layui-btn-normal layui-btn-xs" lay-event="toggle">'
                        + (Number(d.enabled) === 1 ? "停用" : "启用") + '</button>';
                    html += '<button class="layui-btn layui-btn-danger layui-btn-xs" lay-event="delete">删除</button>';
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
                openDetailDrawer(obj.data.id);
            } else if (obj.event === "edit") {
                openFormDrawer(obj.data.id);
            } else if (obj.event === "toggle") {
                toggleEnabled(obj.data);
            } else if (obj.event === "delete") {
                archiveRule(obj.data);
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
        return {
            keyword: state.keyword,
            category: state.category,
            severity: state.severity,
            status: state.status,
            enabled: state.enabled
        };
    }

    function parsePageData(res) {
        if (res.code !== 200) {
            AppRequest.showMessage(res.message || "请求失败", 2, 2200);
        }
        var pageData = res.data || {};
        return {code: res.code === 200 ? 0 : res.code, msg: res.message, count: pageData.total || 0, data: pageData.list || []};
    }

    function openFormDrawer(id) {
        editingRule = null;
        if (id) {
            AppRequest.request("/api/baseline/rule-management/" + encodeURIComponent(id), {method: "GET"}, {showErrorMessage: true})
                .then(function (res) {
                    editingRule = res.data;
                    renderForm(editingRule);
                });
        } else {
            renderForm(null);
        }
    }

    function renderForm(rule) {
        document.getElementById("drawerKicker").textContent = rule ? "Edit Rule" : "Create Rule";
        document.getElementById("drawerTitle").textContent = rule ? "编辑规则" : "新增规则";
        document.getElementById("drawerHint").textContent = rule ? "保存后当前规则 version 自动 +1。" : "新增后可立即被新建任务选择。";
        document.getElementById("drawerBody").innerHTML = AppUtils.getTemplateHtml("ruleFormTemplate");
        var root = document.getElementById("drawerBody");
        root.querySelector('[data-action="close"]').addEventListener("click", closeDrawer);
        root.querySelector("#saveRuleButton").addEventListener("click", saveRule);
        if (rule) {
            root.querySelector("#riskWarning").style.display = "";
            root.querySelector('input[name="ruleCode"]').disabled = true;
            form.val("ruleForm", {
                ruleCode: rule.ruleCode,
                ruleName: rule.ruleName,
                category: rule.category,
                severity: normalizeSeverity(rule.severity),
                osType: rule.osType || "Windows",
                checkMethod: rule.checkMethod || "POWERSHELL",
                remediationType: rule.remediationType || "MANUAL",
                score: rule.score || 1,
                status: rule.status || "PUBLISHED",
                enabled: Number(rule.enabled) === 1,
                isMandatory: Number(rule.isMandatory) !== 0,
                description: rule.description || "",
                checkScript: rule.checkScript || "",
                remediationScript: rule.remediationScript || ""
            });
            root.querySelector('[name="enabled"]').checked = Number(rule.enabled) === 1;
            root.querySelector('[name="isMandatory"]').checked = Number(rule.isMandatory) !== 0;
        } else {
            form.val("ruleForm", {
                severity: "MEDIUM",
                osType: "Windows",
                checkMethod: "POWERSHELL",
                remediationType: "MANUAL",
                score: 1,
                status: "PUBLISHED",
                enabled: true,
                isMandatory: true
            });
            root.querySelector('[name="enabled"]').checked = true;
            root.querySelector('[name="isMandatory"]').checked = true;
        }
        form.render(null, "ruleForm");
        openDrawer();
    }

    function openDetailDrawer(id) {
        AppRequest.request("/api/baseline/rule-management/" + encodeURIComponent(id), {method: "GET"}, {showErrorMessage: true})
            .then(function (res) {
                var rule = res.data || {};
                document.getElementById("drawerKicker").textContent = "Rule Detail";
                document.getElementById("drawerTitle").textContent = rule.ruleName || ("规则#" + rule.id);
                document.getElementById("drawerHint").textContent = "规则编码：" + (rule.ruleCode || "-") + " / version " + (rule.version || 1);
                document.getElementById("drawerBody").innerHTML = buildDetailHtml(rule);
                openDrawer();
            });
    }

    function saveRule() {
        var root = document.getElementById("drawerBody");
        var body = collectForm(root);
        if (!body) {
            return;
        }
        var url = "/api/baseline/rule-management" + (editingRule ? "/" + editingRule.id : "");
        var method = editingRule ? "PUT" : "POST";
        var btn = root.querySelector("#saveRuleButton");
        btn.disabled = true;
        btn.classList.add("layui-btn-disabled");
        AppRequest.request(url, {method: method, body: body}, {showErrorMessage: true})
            .then(function () {
                AppRequest.showMessage(editingRule ? "规则已更新，版本已递增" : "规则已创建", 1, 1800);
                closeDrawer();
                reloadTable(false);
            })
            .catch(function () {
                btn.disabled = false;
                btn.classList.remove("layui-btn-disabled");
            });
    }

    function collectForm(root) {
        var body = {
            ruleCode: value(root, "ruleCode"),
            ruleName: value(root, "ruleName"),
            category: value(root, "category"),
            description: value(root, "description"),
            severity: value(root, "severity"),
            score: Number(value(root, "score") || 1),
            osType: value(root, "osType"),
            checkMethod: value(root, "checkMethod"),
            checkScript: value(root, "checkScript"),
            remediationType: value(root, "remediationType"),
            remediationScript: value(root, "remediationScript"),
            isMandatory: root.querySelector('[name="isMandatory"]').checked ? 1 : 0,
            enabled: root.querySelector('[name="enabled"]').checked ? 1 : 0,
            status: value(root, "status")
        };
        var required = [
            ["ruleCode", "规则编码"], ["ruleName", "规则名称"], ["category", "分类"],
            ["severity", "风险等级"], ["osType", "适用系统"], ["checkMethod", "检测方式"],
            ["checkScript", "检测脚本"], ["remediationType", "修复方式"]
        ];
        for (var i = 0; i < required.length; i++) {
            if (!body[required[i][0]]) {
                AppRequest.showMessage("请填写" + required[i][1], 2);
                return null;
            }
        }
        return body;
    }

    function toggleEnabled(rule) {
        var next = Number(rule.enabled) === 1 ? 0 : 1;
        var text = next === 1 ? "启用" : "停用";
        layer.confirm("确定" + text + "规则“" + escapeHtml(rule.ruleName || rule.ruleCode) + "”吗？", {icon: 3, title: text + "规则"}, function (index) {
            layer.close(index);
            AppRequest.request("/api/baseline/rule-management/" + rule.id + "/enabled?enabled=" + next,
                {method: "PATCH"}, {showErrorMessage: true})
                .then(function () {
                    AppRequest.showMessage("规则已" + text, 1, 1600);
                    reloadTable(true);
                });
        });
    }

    function archiveRule(rule) {
        layer.confirm("删除会将规则归档为 ARCHIVED，并停用该规则，不会物理删除历史引用。确定继续吗？",
            {icon: 3, title: "删除规则"}, function (index) {
                layer.close(index);
                AppRequest.request("/api/baseline/rule-management/" + rule.id, {method: "DELETE"}, {showErrorMessage: true})
                    .then(function () {
                        AppRequest.showMessage("规则已归档", 1, 1600);
                        reloadTable(false);
                    });
            });
    }

    function openDrawer() {
        document.getElementById("drawerMask").style.display = "";
        document.getElementById("ruleDrawer").style.display = "";
        setTimeout(function () {
            document.getElementById("ruleDrawer").classList.add("open");
        }, 20);
    }

    function closeDrawer() {
        document.getElementById("ruleDrawer").classList.remove("open");
        setTimeout(function () {
            document.getElementById("drawerMask").style.display = "none";
            document.getElementById("ruleDrawer").style.display = "none";
            document.getElementById("drawerBody").innerHTML = "";
            editingRule = null;
        }, 180);
    }

    function buildDetailHtml(rule) {
        return '<div class="detail-grid">'
            + detailItem("规则编码", rule.ruleCode)
            + detailItem("分类", rule.category)
            + detailItem("适用系统", rule.osType)
            + detailItem("检测方式", rule.checkMethod)
            + detailItem("风险等级", severityTag(rule.severity), true)
            + detailItem("评分", rule.score)
            + detailItem("状态", statusTag(rule.status), true)
            + detailItem("启用状态", enabledTag(rule.enabled), true)
            + detailItem("强制项", Number(rule.isMandatory) === 1 ? "是" : "否")
            + detailItem("创建时间", AppUtils.formatDateTime(rule.createTime))
            + detailItem("更新时间", AppUtils.formatDateTime(rule.updateTime))
            + '</div>'
            + '<div class="detail-section"><h3>规则说明</h3><pre>' + escapeHtml(rule.description || "暂无说明") + '</pre></div>'
            + '<div class="detail-section"><h3>检测脚本</h3><pre>' + escapeHtml(rule.checkScript || "-") + '</pre></div>'
            + '<div class="detail-section"><h3>修复脚本</h3><pre>' + escapeHtml(rule.remediationScript || "-") + '</pre></div>';
    }

    function detailItem(label, value, raw) {
        return '<div class="detail-item"><label>' + escapeHtml(label) + '</label><div>' + (raw ? value : escapeHtml(value == null ? "-" : value)) + '</div></div>';
    }

    function value(root, name) {
        var el = root.querySelector('[name="' + name + '"]');
        return el ? (el.value || "").trim() : "";
    }

    function severityTag(severity) {
        var value = normalizeSeverity(severity);
        return '<span class="severity ' + value.toLowerCase() + '">' + value + '</span>';
    }

    function statusTag(status) {
        var value = String(status || "-").toUpperCase();
        return '<span class="rule-status ' + value.toLowerCase() + '">' + value + '</span>';
    }

    function enabledTag(enabled) {
        return Number(enabled) === 1
            ? '<span class="enabled on">启用</span>'
            : '<span class="enabled off">停用</span>';
    }

    function normalizeSeverity(severity) {
        return String(severity || "MEDIUM").toUpperCase();
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
