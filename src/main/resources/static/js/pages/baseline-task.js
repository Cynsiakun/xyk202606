layui.use(["table", "form", "layer"], function () {
    var table = layui.table;
    var form = layui.form;
    var layer = layui.layer;

    var tableId = "baselineTaskTable";
    var currentStatus = "";
    var hasInProgress = false;

    // 新建任务弹窗内的选择状态
    var allHosts = [];
    var allRules = [];
    var selectedHostIds = {};
    var selectedRuleIds = {};

    renderTaskTable();
    bindToolbar();
    startPolling();

    function renderTaskTable() {
        table.render({
            elem: "#" + tableId,
            url: "/api/baseline/tasks",
            method: "GET",
            headers: {Authorization: "Bearer " + AppAuth.getToken()},
            page: true,
            limit: 10,
            limits: [10, 20, 50],
            request: {pageName: "page", limitName: "size"},
            where: {status: currentStatus},
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
            cols: [[
                {field: "taskName", title: "任务名称", minWidth: 180, templet: function (d) {
                    return escapeHtml(d.taskName || "-");
                }},
                {field: "executeType", title: "执行方式", width: 110, templet: function (d) {
                    return d.executeType === "SCHEDULED" ? "定时" : "立即";
                }},
                {field: "ruleCount", title: "规则数", width: 90, align: "center", templet: function (d) {
                    return d.ruleCount != null ? d.ruleCount : 0;
                }},
                {field: "hostCount", title: "主机数", width: 90, align: "center", templet: function (d) {
                    return d.hostCount != null ? d.hostCount : 0;
                }},
                {field: "avgPassRate", title: "平均通过率", width: 120, align: "center", templet: function (d) {
                    return buildRateCell(d.avgPassRate);
                }},
                {field: "status", title: "状态", width: 110, align: "center", templet: function (d) {
                    return buildStatusTag(d.status);
                }},
                {field: "createTime", title: "创建时间", width: 180, templet: function (d) {
                    return AppUtils.formatDateTime(d.createTime);
                }},
                {title: "操作", width: 120, fixed: "right", align: "center", templet: function () {
                    return '<button type="button" class="layui-btn layui-btn-primary layui-btn-xs" lay-event="result">查看结果</button>';
                }}
            ]],
            done: function (res) {
                var rows = (res && res.data) || [];
                hasInProgress = rows.some(function (row) {
                    return row.status === "PENDING" || row.status === "RUNNING";
                });
            }
        });

        table.on("tool(" + tableId + ")", function (obj) {
            if (obj.event === "result") {
                openResultDialog(obj.data);
            }
        });
    }

    function bindToolbar() {
        var addButton = document.getElementById("addButton");
        if (AppAuth.hasPermission("baseline:create")) {
            addButton.addEventListener("click", openTaskDialog);
        } else {
            addButton.style.display = "none";
        }

        document.getElementById("refreshButton").addEventListener("click", function () {
            table.reloadData(tableId, {scrollPos: "fixed"});
        });

        form.on("select(statusFilter)", function (data) {
            currentStatus = data.value || "";
            AppTable.reload(table, tableId, {status: currentStatus});
        });
    }

    function startPolling() {
        setInterval(function () {
            if (hasInProgress) {
                table.reloadData(tableId, {scrollPos: "fixed"});
            }
        }, 10000);
    }

    /* ============ 新建任务 ============ */

    function openTaskDialog() {
        allHosts = [];
        allRules = [];
        selectedHostIds = {};
        selectedRuleIds = {};

        var viewportHeight = window.innerHeight || 760;
        var viewportWidth = window.innerWidth || 720;
        var dialogHeight = Math.min(720, viewportHeight - 30);
        var dialogWidth = Math.min(720, viewportWidth - 30);

        var index = layer.open({
            type: 1,
            title: "新建基线任务",
            area: [dialogWidth + "px", dialogHeight + "px"],
            content: AppUtils.getTemplateHtml("baselineTaskFormTemplate"),
            success: function (layero) {
                var root = layero[0];
                form.render("radio", "baselineTaskForm");

                form.on("radio(executeType)", function (data) {
                    var cronItem = root.querySelector("#cronItem");
                    cronItem.style.display = data.value === "SCHEDULED" ? "" : "none";
                });

                root.querySelector('[data-action="close"]').addEventListener("click", function () {
                    layer.close(index);
                });
                root.querySelector("#submitTaskButton").addEventListener("click", function () {
                    submitTask(root, index, this);
                });

                bindHostPicker(root);
                bindRulePicker(root);
                loadHosts(root);
                loadRules(root);
            }
        });
    }

    function bindHostPicker(root) {
        var search = root.querySelector("#hostSearch");
        search.addEventListener("input", function () {
            renderHostList(root, this.value.trim().toLowerCase());
        });
        root.querySelector("#hostList").addEventListener("change", function (event) {
            var box = event.target.closest("input[type=checkbox]");
            if (!box) {
                return;
            }
            var id = box.value;
            if (box.checked) {
                selectedHostIds[id] = true;
            } else {
                delete selectedHostIds[id];
            }
            updateHostCount(root);
        });
    }

    function bindRulePicker(root) {
        var search = root.querySelector("#ruleSearch");
        search.addEventListener("input", function () {
            renderRuleList(root, this.value.trim().toLowerCase());
        });
        root.querySelector("#ruleList").addEventListener("change", function (event) {
            var box = event.target.closest("input[type=checkbox]");
            if (!box) {
                return;
            }
            var id = box.value;
            if (box.checked) {
                selectedRuleIds[id] = true;
            } else {
                delete selectedRuleIds[id];
            }
            syncRuleCheckAll(root);
            updateRuleCount(root);
        });
        root.querySelector("#ruleCheckAll").addEventListener("change", function () {
            var checked = this.checked;
            allRules.forEach(function (rule) {
                if (checked) {
                    selectedRuleIds[rule.id] = true;
                } else {
                    delete selectedRuleIds[rule.id];
                }
            });
            renderRuleList(root, root.querySelector("#ruleSearch").value.trim().toLowerCase());
            updateRuleCount(root);
        });
    }

    function loadHosts(root) {
        AppRequest.request("/api/host/list?page=1&size=500", {method: "GET"}, {showErrorMessage: false})
            .then(function (res) {
                var data = res.data || {};
                allHosts = data.list || [];
                renderHostList(root, "");
                updateHostCount(root);
            })
            .catch(function () {
                root.querySelector("#hostList").innerHTML = '<div class="picker-empty">主机加载失败</div>';
            });
    }

    function loadRules(root) {
        AppRequest.request("/api/baseline/rules", {method: "GET"}, {showErrorMessage: false})
            .then(function (res) {
                allRules = res.data || [];
                // 默认全选
                allRules.forEach(function (rule) {
                    selectedRuleIds[rule.id] = true;
                });
                renderRuleList(root, "");
                syncRuleCheckAll(root);
                updateRuleCount(root);
            })
            .catch(function () {
                root.querySelector("#ruleList").innerHTML = '<div class="picker-empty">规则加载失败</div>';
            });
    }

    function renderHostList(root, keyword) {
        var listEl = root.querySelector("#hostList");
        var filtered = allHosts.filter(function (host) {
            if (!keyword) {
                return true;
            }
            var text = ((host.hostname || "") + " " + (host.ipv4 || "")).toLowerCase();
            return text.indexOf(keyword) > -1;
        });
        if (filtered.length === 0) {
            listEl.innerHTML = '<div class="picker-empty">没有匹配的主机</div>';
            return;
        }
        listEl.innerHTML = filtered.map(function (host) {
            var checked = selectedHostIds[host.id] ? "checked" : "";
            return '<label class="picker-option">'
                + '<input type="checkbox" value="' + host.id + '" ' + checked + '>'
                + '<span class="opt-main">' + escapeHtml(host.hostname || ("主机#" + host.id)) + '</span>'
                + '<span class="opt-sub">' + escapeHtml(host.ipv4 || "-") + '</span>'
                + '</label>';
        }).join("");
    }

    function renderRuleList(root, keyword) {
        var listEl = root.querySelector("#ruleList");
        var filtered = allRules.filter(function (rule) {
            if (!keyword) {
                return true;
            }
            var text = ((rule.ruleCode || "") + " " + (rule.ruleName || "") + " " + (rule.category || "")).toLowerCase();
            return text.indexOf(keyword) > -1;
        });
        if (filtered.length === 0) {
            listEl.innerHTML = '<div class="picker-empty">没有匹配的规则</div>';
            return;
        }
        listEl.innerHTML = filtered.map(function (rule) {
            var checked = selectedRuleIds[rule.id] ? "checked" : "";
            var sub = escapeHtml(rule.ruleCode || "") + (rule.category ? " · " + escapeHtml(rule.category) : "");
            return '<label class="picker-option">'
                + '<input type="checkbox" value="' + rule.id + '" ' + checked + '>'
                + '<span class="opt-main">' + escapeHtml(rule.ruleName || ("规则#" + rule.id)) + '</span>'
                + '<span class="opt-sub">' + sub + '</span>'
                + (rule.severity ? '<span class="opt-tag">' + escapeHtml(rule.severity) + '</span>' : "")
                + '</label>';
        }).join("");
    }

    function syncRuleCheckAll(root) {
        var checkAll = root.querySelector("#ruleCheckAll");
        if (!checkAll) {
            return;
        }
        checkAll.checked = allRules.length > 0 && allRules.every(function (rule) {
            return selectedRuleIds[rule.id];
        });
    }

    function updateHostCount(root) {
        root.querySelector("#hostCount").textContent = "已选 " + Object.keys(selectedHostIds).length;
    }

    function updateRuleCount(root) {
        root.querySelector("#ruleCount").textContent = "已选 " + Object.keys(selectedRuleIds).length;
    }

    function submitTask(root, dialogIndex, button) {
        var taskName = (root.querySelector('input[name="taskName"]').value || "").trim();
        var executeType = root.querySelector('input[name="executeType"]:checked').value;
        var cronExpr = (root.querySelector('input[name="cronExpr"]').value || "").trim();
        var hostIds = Object.keys(selectedHostIds).map(Number);
        var ruleIds = Object.keys(selectedRuleIds).map(Number);

        if (!taskName) {
            AppRequest.showMessage("请输入任务名称", 2);
            return;
        }
        if (executeType === "SCHEDULED" && !cronExpr) {
            AppRequest.showMessage("定时执行需填写 Cron 表达式", 2);
            return;
        }
        if (hostIds.length === 0) {
            AppRequest.showMessage("请至少选择一台主机", 2);
            return;
        }
        if (ruleIds.length === 0) {
            AppRequest.showMessage("请至少选择一条规则", 2);
            return;
        }

        var payload = {
            taskName: taskName,
            executeType: executeType,
            cronExpr: executeType === "SCHEDULED" ? cronExpr : null,
            hostIds: hostIds,
            ruleIds: ruleIds
        };

        button.disabled = true;
        button.classList.add("layui-btn-disabled");
        AppRequest.request("/api/baseline/tasks", {method: "POST", body: payload}, {successMessage: "任务已创建并下发"})
            .then(function () {
                layer.close(dialogIndex);
                table.reloadData(tableId, {scrollPos: "fixed"});
            })
            .catch(function () {
                button.disabled = false;
                button.classList.remove("layui-btn-disabled");
            });
    }

    /* ============ 查看结果 ============ */

    function openResultDialog(task) {
        var viewportHeight = window.innerHeight || 720;
        var viewportWidth = window.innerWidth || 860;
        var dialogHeight = Math.min(700, viewportHeight - 30);
        var dialogWidth = Math.min(900, viewportWidth - 30);

        layer.open({
            type: 1,
            title: "任务结果 - " + (task.taskName || ("#" + task.id)),
            area: [dialogWidth + "px", dialogHeight + "px"],
            content: AppUtils.getTemplateHtml("baselineResultTemplate"),
            success: function (layero) {
                var root = layero[0];
                loadOverview(root, task.id);
            }
        });
    }

    function loadOverview(root, taskId) {
        AppRequest.request("/api/baseline/tasks/" + taskId + "/result", {method: "GET"}, {showErrorMessage: false})
            .then(function (res) {
                var ov = res.data || {};
                renderOverviewCards(root, ov);
                if ((ov.failHostCount || 0) > 0) {
                    renderProblemHostTable(root, taskId);
                } else {
                    root.querySelector("#problemHostTable").style.display = "none";
                    root.querySelector("#allPassTip").style.display = "";
                }
            })
            .catch(function () {
                root.querySelector("#overviewCards").innerHTML = '<div class="picker-empty">结果加载失败</div>';
            });
    }

    function renderOverviewCards(root, ov) {
        var cards = [
            {label: "总主机数", value: ov.totalHostCount != null ? ov.totalHostCount : 0, cls: ""},
            {label: "已完成主机", value: ov.finishedHostCount != null ? ov.finishedHostCount : 0, cls: ""},
            {label: "平均合规率", value: formatRate(ov.avgComplianceRate), cls: "rate"},
            {label: "PASS 主机", value: ov.passHostCount != null ? ov.passHostCount : 0, cls: "pass"},
            {label: "FAIL 主机", value: ov.failHostCount != null ? ov.failHostCount : 0, cls: "fail"}
        ];
        root.querySelector("#overviewCards").innerHTML = cards.map(function (card) {
            return '<div class="overview-card ' + card.cls + '">'
                + '<div class="ov-label">' + card.label + '</div>'
                + '<div class="ov-value">' + card.value + '</div>'
                + '</div>';
        }).join("");
    }

    function renderProblemHostTable(root, taskId) {
        AppTable.renderPageTable(table, {
            elem: root.querySelector("#problemHostTable"),
            url: "/api/baseline/tasks/" + taskId + "/problem-hosts",
            limit: 10,
            limits: [10, 20, 50],
            cols: [[
                {field: "hostName", title: "主机名称", minWidth: 180, templet: function (d) {
                    return escapeHtml(d.hostName || ("主机#" + d.hostId));
                }},
                {field: "ipv4", title: "IP 地址", width: 160, templet: function (d) {
                    return escapeHtml(d.ipv4 || "-");
                }},
                {field: "complianceRate", title: "合规率", width: 120, align: "center", templet: function (d) {
                    return buildRateCell(d.complianceRate);
                }},
                {field: "failRuleCount", title: "失败规则数", width: 120, align: "center", templet: function (d) {
                    return '<span class="status-tag fail">' + (d.failRuleCount != null ? d.failRuleCount : 0) + '</span>';
                }}
            ]]
        });
    }

    /* ============ 工具函数 ============ */

    function buildStatusTag(status) {
        var map = {
            PENDING: {cls: "pending", text: "待执行"},
            RUNNING: {cls: "running", text: "执行中"},
            FINISHED: {cls: "finished", text: "已完成"},
            FAILED: {cls: "failed", text: "失败"}
        };
        var item = map[status] || {cls: "pending", text: status || "-"};
        return '<span class="task-status ' + item.cls + '">' + item.text + '</span>';
    }

    function buildRateCell(rate) {
        if (rate == null || rate === "") {
            return '<span class="rate-cell">-</span>';
        }
        var num = Number(rate);
        var cls = num >= 90 ? "good" : (num >= 60 ? "warn" : "bad");
        return '<span class="rate-cell ' + cls + '">' + formatRate(rate) + '</span>';
    }

    function formatRate(rate) {
        if (rate == null || rate === "") {
            return "-";
        }
        return Number(rate).toFixed(2) + "%";
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
