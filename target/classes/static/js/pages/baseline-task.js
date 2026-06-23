layui.use(["table", "form", "layer"], function () {
    var table = layui.table;
    var form = layui.form;
    var layer = layui.layer;

    var taskTableId = "baselineTaskTable";
    var hostResultTableId = "taskHostResultTable";
    var ruleResultTableId = "taskRuleResultTable";
    var taskState = {page: 1, size: 10, keyword: "", executeType: "", taskType: "", status: ""};
    var resultState = {task: null, page: 1, size: 10, selectedHost: null, assetTypeCode: ""};
    var hasInProgress = false;

    var allHosts = [];
    var allRules = [];
    var selectedHostIds = {};
    var selectedRuleIds = {};
    var protectionLevels = [];
    var assetTypes = [];

    init();

    function init() {
        bindToolbar();
        bindResultPanel();
        renderTaskTable();
        startPolling();
    }

    function bindToolbar() {
        var addButton = document.getElementById("addButton");
        if (AppAuth.hasPermission("baseline:create")) {
            addButton.addEventListener("click", openTaskDialog);
        } else {
            addButton.style.display = "none";
        }
        document.getElementById("searchButton").addEventListener("click", applyFilters);
        document.getElementById("taskKeywordInput").addEventListener("keydown", function (e) {
            if (e.key === "Enter") {
                applyFilters();
            }
        });
        document.getElementById("refreshButton").addEventListener("click", function () {
            reloadTaskTable(false);
        });
        form.on("select(executeTypeFilter)", function (data) {
            taskState.executeType = data.value || "";
            taskState.page = 1;
            reloadTaskTable(false);
        });
        form.on("select(taskTypeFilter)", function (data) {
            taskState.taskType = data.value || "";
            taskState.page = 1;
            reloadTaskTable(false);
        });
        form.on("select(statusFilter)", function (data) {
            taskState.status = data.value || "";
            taskState.page = 1;
            reloadTaskTable(false);
        });
    }

    function bindResultPanel() {
        document.getElementById("backToListButton").addEventListener("click", function () {
            document.getElementById("taskResultPanel").style.display = "none";
            document.getElementById("taskListPanel").style.display = "";
            reloadTaskTable(true);
        });
        document.getElementById("resultRefreshButton").addEventListener("click", function () {
            refreshResultPanel();
        });
        document.getElementById("resultAssetTypeFilter").addEventListener("change", function () {
            resultState.assetTypeCode = this.value || "";
            resultState.page = 1;
            renderTaskHostTable(resultState.task.id);
        });
        document.getElementById("exportResultButton").addEventListener("click", function () {
            if (resultState.task) {
                openTaskExportMenu(resultState.task.id);
            }
        });
        document.getElementById("closeRulePanelButton").addEventListener("click", function () {
            hideTaskRulePanel();
        });
    }

    function applyFilters() {
        taskState.keyword = document.getElementById("taskKeywordInput").value.trim();
        taskState.page = 1;
        reloadTaskTable(false);
    }

    function renderTaskTable() {
        table.render({
            elem: "#" + taskTableId,
            id: taskTableId,
            url: "/api/baseline/tasks",
            method: "GET",
            headers: {Authorization: "Bearer " + AppAuth.getToken()},
            page: true,
            curr: taskState.page,
            limit: taskState.size,
            limits: [10, 20, 50],
            request: {pageName: "page", limitName: "size"},
            where: buildTaskQuery(),
            parseData: parsePageData,
            cols: [[
                {field: "taskName", title: "任务名称", minWidth: 190, templet: function (d) {
                    return escapeHtml(d.taskName || "-");
                }},
                {field: "executeType", title: "执行方式", width: 110, templet: function (d) {
                    return d.executeType === "SCHEDULED" ? "定时任务" : "立即执行";
                }},
                {field: "taskType", title: "任务类型", width: 110, templet: function (d) {
                    return buildTaskTypeTag(d.taskType);
                }},
                {field: "ruleCount", title: "规则数", width: 80, align: "center"},
                {field: "hostCount", title: "主机数", width: 80, align: "center"},
                {title: "进度", width: 180, templet: buildProgressCell},
                {field: "avgPassRate", title: "平均合规率", width: 120, align: "center", templet: function (d) {
                    return buildRateCell(d.avgPassRate);
                }},
                {field: "status", title: "状态", width: 100, align: "center", templet: function (d) {
                    return buildStatusTag(d.status);
                }},
                {field: "createTime", title: "创建时间", width: 170, templet: function (d) {
                    return AppUtils.formatDateTime(d.createTime);
                }},
                {title: "操作", width: 110, fixed: "right", align: "center", templet: function () {
                    return '<button type="button" class="layui-btn layui-btn-primary layui-btn-xs" lay-event="result">查看结果</button>';
                }}
            ]],
            done: function (res, curr) {
                taskState.page = curr;
                taskState.size = this.limit || taskState.size;
                var rows = (res && res.data) || [];
                hasInProgress = rows.some(function (row) {
                    return row.status === "PENDING" || row.status === "RUNNING";
                });
            }
        });

        table.on("tool(" + taskTableId + ")", function (obj) {
            if (obj.event === "result") {
                openResultPanel(obj.data);
            }
        });
    }

    function reloadTaskTable(silent) {
        table.reload(taskTableId, {
            page: {curr: taskState.page},
            limit: taskState.size,
            where: buildTaskQuery()
        }, silent);
    }

    function buildTaskQuery() {
        return {
            keyword: taskState.keyword,
            executeType: taskState.executeType,
            taskType: taskState.taskType,
            status: taskState.status
        };
    }

    function parsePageData(res) {
        if (res.code !== 200) {
            AppRequest.showMessage(res.message || "请求失败", 2, 2200);
            if ((res.code === 401 || res.code === 403) && window.AppAuth) {
                AppAuth.clearLogin();
                AppAuth.redirectToLogin();
            }
        }
        var pageData = res.data || {};
        return {code: res.code === 200 ? 0 : res.code, msg: res.message, count: pageData.total || 0, data: pageData.list || []};
    }

    function startPolling() {
        setInterval(function () {
            if (document.visibilityState !== "visible") {
                return;
            }
            if (resultState.task) {
                refreshResultPanel(true);
            } else if (hasInProgress) {
                reloadTaskTable(true);
            }
        }, 10000);
    }

    function openTaskDialog() {
        allHosts = [];
        allRules = [];
        selectedHostIds = {};
        selectedRuleIds = {};
        protectionLevels = [];
        assetTypes = [];

        var dialogHeight = Math.min(760, (window.innerHeight || 760) - 30);
        var dialogWidth = Math.min(820, (window.innerWidth || 860) - 30);
        var index = layer.open({
            type: 1,
            title: "新建基线任务",
            area: [dialogWidth + "px", dialogHeight + "px"],
            content: AppUtils.getTemplateHtml("baselineTaskFormTemplate"),
            success: function (layero) {
                var root = layero[0];
                form.render(null, "baselineTaskForm");
                form.on("radio(executeType)", function (data) {
                    root.querySelector("#scheduleItem").style.display = data.value === "SCHEDULED" ? "" : "none";
                });
                initSchedulePicker(root);
                root.querySelector('[data-action="close"]').addEventListener("click", function () {
                    layer.close(index);
                });
                root.querySelector("#submitTaskButton").addEventListener("click", function () {
                    submitTask(root, index, this);
                });
                bindHostPicker(root);
                bindRulePicker(root);
                bindScopePicker(root);
                updateSubmitState(root);
                loadHosts(root);
                loadScopeDictionaries(root).then(function () {
                    loadRules(root);
                });
            }
        });
    }

    function bindHostPicker(root) {
        root.querySelector("#hostSearch").addEventListener("input", function () {
            renderHostList(root, this.value.trim().toLowerCase());
        });
        root.querySelector("#hostList").addEventListener("change", function (event) {
            var box = event.target.closest("input[type=checkbox]");
            if (!box || box.disabled) {
                return;
            }
            toggleSelection(selectedHostIds, box.value, box.checked);
            syncPickerOptionState(box);
            updateHostCount(root);
            updateSubmitState(root);
        });
    }

    function bindRulePicker(root) {
        root.querySelector("#ruleSearch").addEventListener("input", function () {
            renderRuleList(root, this.value.trim().toLowerCase());
        });
        root.querySelector("#ruleList").addEventListener("change", function (event) {
            var box = event.target.closest("input[type=checkbox]");
            if (!box) {
                return;
            }
            toggleSelection(selectedRuleIds, box.value, box.checked);
            syncPickerOptionState(box);
            syncRuleCheckAll(root);
            updateRuleCount(root);
            updateSubmitState(root);
        });
        root.querySelector("#ruleCheckAll").addEventListener("change", function () {
            var checked = this.checked;
            allRules.forEach(function (rule) {
                toggleSelection(selectedRuleIds, rule.id, checked);
            });
            renderRuleList(root, root.querySelector("#ruleSearch").value.trim().toLowerCase());
            syncRuleCheckAll(root);
            updateRuleCount(root);
            updateSubmitState(root);
        });
        root.querySelector("#ruleCategoryActions").addEventListener("click", function (event) {
            var btn = event.target.closest("[data-category]");
            if (!btn) {
                return;
            }
            var category = btn.getAttribute("data-category");
            allRules.forEach(function (rule) {
                if ((rule.category || "未分类") === category) {
                    selectedRuleIds[rule.id] = true;
                }
            });
            renderRuleList(root, root.querySelector("#ruleSearch").value.trim().toLowerCase());
            syncRuleCheckAll(root);
            updateRuleCount(root);
            updateSubmitState(root);
        });
    }

    function bindScopePicker(root) {
        form.on("select(taskProtectionLevel)", function () {
            loadRules(root);
        });
        root.querySelector("#assetTypeOptions").addEventListener("change", function (event) {
            if (!event.target.closest("input[type=checkbox]")) {
                return;
            }
            loadRules(root);
        });
    }

    function loadHosts(root) {
        AppRequest.request("/api/host/list?page=1&size=500", {method: "GET"}, {showErrorMessage: false})
            .then(function (res) {
                allHosts = (res.data || {}).list || [];
                renderHostList(root, "");
                updateHostCount(root);
                updateSubmitState(root);
            })
            .catch(function () {
                root.querySelector("#hostList").innerHTML = '<div class="picker-empty">主机加载失败</div>';
            });
    }

    function loadScopeDictionaries(root) {
        return Promise.all([
            AppRequest.request("/api/baseline/protection-levels", {method: "GET"}, {showErrorMessage: false}).catch(function () {
                return {data: []};
            }),
            AppRequest.request("/api/baseline/asset-types", {method: "GET"}, {showErrorMessage: false}).catch(function () {
                return {data: []};
            })
        ]).then(function (items) {
            protectionLevels = items[0].data || [];
            assetTypes = (items[1].data || []).filter(function (assetType) {
                return ["OS_WINDOWS", "OS_LINUX", "MW_TOMCAT", "DB_MYSQL"].indexOf(assetType.typeCode) > -1;
            });
            renderProtectionLevels(root);
            renderAssetTypes(root);
            form.render("select", "baselineTaskForm");
        });
    }

    function loadRules(root) {
        selectedRuleIds = {};
        root.querySelector("#ruleList").innerHTML = '<div class="picker-empty">加载中...</div>';
        AppRequest.request(buildRuleUrl(root), {method: "GET"}, {showErrorMessage: false})
            .then(function (res) {
                allRules = res.data || [];
                allRules.forEach(function (rule) {
                    selectedRuleIds[rule.id] = true;
                });
                renderRuleCategoryActions(root);
                renderRuleList(root, "");
                syncRuleCheckAll(root);
                updateRuleCount(root);
                updateSubmitState(root);
            })
            .catch(function () {
                root.querySelector("#ruleList").innerHTML = '<div class="picker-empty">规则加载失败</div>';
            });
    }

    function renderProtectionLevels(root) {
        var select = root.querySelector('select[name="protectionLevelCode"]');
        var hiddenLevelCodes = ["S3", "S3_PLUS"];
        var levels = protectionLevels.length ? protectionLevels : [
            {levelCode: "L3", levelName: "等保三级"},
            {levelCode: "L4", levelName: "等保四级"},
            {levelCode: "L5", levelName: "等保五级"}
        ];
        levels = levels.filter(function (level) {
            return hiddenLevelCodes.indexOf((level.levelCode || "").toUpperCase()) === -1;
        });
        if (!levels.length) {
            levels = [{levelCode: "L3", levelName: "L3"}];
        }
        select.innerHTML = levels.map(function (level) {
            var selected = level.levelCode === "L3" ? " selected" : "";
            return '<option value="' + escapeHtml(level.levelCode) + '"' + selected + '>'
                + escapeHtml(level.levelName || level.levelCode) + "（" + escapeHtml(level.levelCode) + "）</option>";
        }).join("");
    }

    function renderAssetTypes(root) {
        var defaults = ["OS_WINDOWS", "OS_LINUX", "MW_TOMCAT", "DB_MYSQL"];
        var rows = assetTypes.length ? assetTypes : defaults.map(function (code) {
            return {typeCode: code, typeName: code};
        });
        root.querySelector("#assetTypeOptions").innerHTML = rows.map(function (assetType) {
            var checked = defaults.indexOf(assetType.typeCode) > -1 ? " checked" : "";
            return '<label class="asset-type-option">'
                + '<input type="checkbox" value="' + escapeHtml(assetType.typeCode) + '" lay-ignore' + checked + '>'
                + '<span>' + escapeHtml(assetType.typeName || assetType.typeCode) + '</span>'
                + '</label>';
        }).join("");
    }

    function buildRuleUrl(root) {
        var params = [];
        var level = getProtectionLevelCode(root);
        if (level) {
            params.push("protectionLevelCode=" + encodeURIComponent(level));
        }
        getSelectedAssetTypeCodes(root).forEach(function (code) {
            params.push("assetTypeCodes=" + encodeURIComponent(code));
        });
        return "/api/baseline/rules" + (params.length ? "?" + params.join("&") : "");
    }

    function renderHostList(root, keyword) {
        var filtered = allHosts.filter(function (host) {
            var text = ((host.hostname || "") + " " + (host.ipv4 || "")).toLowerCase();
            return !keyword || text.indexOf(keyword) > -1;
        });
        var listEl = root.querySelector("#hostList");
        if (!filtered.length) {
            listEl.innerHTML = '<div class="picker-empty">没有匹配的主机</div>';
            return;
        }
        listEl.innerHTML = filtered.map(function (host) {
            var online = isHostOnline(host);
            var checked = selectedHostIds[host.id] ? "checked" : "";
            var selected = selectedHostIds[host.id] ? " selected" : "";
            return '<label class="picker-option' + selected + (online ? "" : " disabled") + '">'
                + '<input type="checkbox" value="' + host.id + '" lay-ignore ' + checked + (online ? "" : " disabled") + '>'
                + '<span class="opt-main">' + escapeHtml(host.hostname || ("主机#" + host.id)) + '</span>'
                + '<span class="opt-sub">' + escapeHtml(host.ipv4 || "-") + '</span>'
                + '<span class="opt-tag ' + (online ? "online" : "offline") + '">' + (online ? "在线" : "离线") + '</span>'
                + '</label>';
        }).join("");
    }

    function renderRuleCategoryActions(root) {
        var categories = [];
        allRules.forEach(function (rule) {
            var category = rule.category || "未分类";
            if (categories.indexOf(category) === -1) {
                categories.push(category);
            }
        });
        root.querySelector("#ruleCategoryActions").innerHTML = categories.sort().map(function (category) {
            return '<button type="button" class="category-btn" data-category="' + escapeHtml(category) + '">' + escapeHtml(category) + '</button>';
        }).join("");
    }

    function renderRuleList(root, keyword) {
        var filtered = allRules.filter(function (rule) {
            var text = ((rule.ruleCode || "") + " " + (rule.ruleName || "") + " " + (rule.category || "") + " "
                + (rule.assetType || "") + " " + (rule.protectionLevelFlag || "")).toLowerCase();
            return !keyword || text.indexOf(keyword) > -1;
        });
        var listEl = root.querySelector("#ruleList");
        if (!filtered.length) {
            listEl.innerHTML = '<div class="picker-empty">没有匹配的规则</div>';
            return;
        }
        listEl.innerHTML = filtered.map(function (rule) {
            var checked = selectedRuleIds[rule.id] ? "checked" : "";
            var selected = selectedRuleIds[rule.id] ? " selected" : "";
            return '<label class="picker-option' + selected + '">'
                + '<input type="checkbox" value="' + rule.id + '" lay-ignore ' + checked + '>'
                + '<span class="opt-main">' + escapeHtml(rule.ruleName || ("规则#" + rule.id)) + '</span>'
                + '<span class="opt-sub">' + escapeHtml(rule.ruleCode || "")
                + (rule.assetType ? " · " + escapeHtml(rule.assetType) : "")
                + (rule.protectionLevelFlag ? " · " + escapeHtml(rule.protectionLevelFlag) : " · 通用")
                + (rule.category ? " · " + escapeHtml(rule.category) : "") + '</span>'
                + (rule.severity ? '<span class="opt-tag">' + escapeHtml(rule.severity) + '</span>' : "")
                + '</label>';
        }).join("");
    }

    function syncRuleCheckAll(root) {
        root.querySelector("#ruleCheckAll").checked = allRules.length > 0 && allRules.every(function (rule) {
            return selectedRuleIds[rule.id];
        });
    }

    function syncPickerOptionState(input) {
        var option = input && input.closest(".picker-option");
        if (!option) {
            return;
        }
        option.classList.toggle("selected", !!input.checked);
    }

    function updateHostCount(root) {
        root.querySelector("#hostCount").textContent = "已选 " + Object.keys(selectedHostIds).length;
    }

    function updateRuleCount(root) {
        root.querySelector("#ruleCount").textContent = "已选 " + Object.keys(selectedRuleIds).length;
    }

    function updateSubmitState(root) {
        var submit = root.querySelector("#submitTaskButton");
        var enabled = Object.keys(selectedHostIds).length > 0 && Object.keys(selectedRuleIds).length > 0;
        submit.disabled = !enabled;
        submit.classList.toggle("layui-btn-disabled", !enabled);
    }

    function submitTask(root, dialogIndex, button) {
        var taskName = (root.querySelector('input[name="taskName"]').value || "").trim();
        var executeType = root.querySelector('input[name="executeType"]:checked').value;
        var cronExpr = executeType === "SCHEDULED" ? buildCronExpr(root) : null;
        var hostIds = Object.keys(selectedHostIds).map(Number);
        var ruleIds = Object.keys(selectedRuleIds).map(Number);
        var protectionLevelCode = getProtectionLevelCode(root) || "L3";
        var assetTypeCodes = getSelectedAssetTypeCodes(root);
        if (!taskName) {
            AppRequest.showMessage("请输入任务名称", 2);
            return;
        }
        if (executeType === "SCHEDULED" && !cronExpr) {
            AppRequest.showMessage("请设置定时执行计划", 2);
            return;
        }
        if (!hostIds.length || !ruleIds.length) {
            AppRequest.showMessage("请选择主机和规则", 2);
            return;
        }
        button.disabled = true;
        button.classList.add("layui-btn-disabled");
        AppRequest.request("/api/baseline/tasks", {
            method: "POST",
            body: {
                taskName: taskName,
                executeType: executeType,
                cronExpr: executeType === "SCHEDULED" ? cronExpr : null,
                protectionLevelCode: protectionLevelCode,
                assetTypeCodes: assetTypeCodes,
                hostIds: hostIds,
                ruleIds: ruleIds
            }
        }, {successMessage: "任务已创建并下发"})
            .then(function () {
                layer.close(dialogIndex);
                reloadTaskTable(false);
            })
            .catch(function () {
                button.disabled = false;
                button.classList.remove("layui-btn-disabled");
            });
    }

    function initSchedulePicker(root) {
        var monthDay = root.querySelector('select[name="scheduleMonthDay"]');
        monthDay.innerHTML = Array.from({length: 28}, function (_, index) {
            var day = index + 1;
            return '<option value="' + day + '">' + day + '日</option>';
        }).join("");
        ["scheduleFrequency", "scheduleWeekday", "scheduleMonthDay", "scheduleTime"].forEach(function (name) {
            root.querySelector('[name="' + name + '"]').addEventListener("change", function () {
                updateSchedulePicker(root);
            });
        });
        updateSchedulePicker(root);
    }

    function updateSchedulePicker(root) {
        var frequency = root.querySelector('select[name="scheduleFrequency"]').value;
        var time = root.querySelector('input[name="scheduleTime"]').value || "02:00";
        root.querySelector(".schedule-weekly").style.display = frequency === "WEEKLY" ? "" : "none";
        root.querySelector(".schedule-monthly").style.display = frequency === "MONTHLY" ? "" : "none";
        var text = "每天 " + time + " 执行";
        if (frequency === "WEEKLY") {
            text = root.querySelector('select[name="scheduleWeekday"] option:checked').textContent + " " + time + " 执行";
        } else if (frequency === "MONTHLY") {
            text = "每月 " + root.querySelector('select[name="scheduleMonthDay"]').value + " 日 " + time + " 执行";
        }
        root.querySelector("#scheduleHint").textContent = text;
    }

    function buildCronExpr(root) {
        var frequency = root.querySelector('select[name="scheduleFrequency"]').value;
        var time = root.querySelector('input[name="scheduleTime"]').value || "";
        var parts = time.split(":");
        if (parts.length !== 2) {
            return "";
        }
        var hour = Number(parts[0]);
        var minute = Number(parts[1]);
        if (Number.isNaN(hour) || Number.isNaN(minute) || hour < 0 || hour > 23 || minute < 0 || minute > 59) {
            return "";
        }
        if (frequency === "WEEKLY") {
            return "0 " + minute + " " + hour + " ? * " + root.querySelector('select[name="scheduleWeekday"]').value;
        }
        if (frequency === "MONTHLY") {
            return "0 " + minute + " " + hour + " " + root.querySelector('select[name="scheduleMonthDay"]').value + " * ?";
        }
        return "0 " + minute + " " + hour + " * * ?";
    }

    function openResultPanel(task) {
        resultState.task = task;
        resultState.page = 1;
        resultState.selectedHost = null;
        resultState.assetTypeCode = "";
        document.getElementById("resultAssetTypeFilter").value = "";
        loadResultAssetTypeFilter();
        document.getElementById("taskListPanel").style.display = "none";
        document.getElementById("taskResultPanel").style.display = "";
        document.getElementById("resultTaskTitle").textContent = task.taskName || ("任务#" + task.id);
        hideTaskRulePanel();
        refreshResultPanel();
    }

    function refreshResultPanel(silent) {
        if (!resultState.task) {
            return;
        }
        var taskId = resultState.task.id;
        AppRequest.request("/api/baseline/tasks/" + taskId + "/result", {method: "GET"}, {showErrorMessage: !silent})
            .then(function (res) {
                renderOverviewCards(res.data || {});
                renderTaskHostTable(taskId);
            });
    }

    function renderOverviewCards(ov) {
        var cards = [
            {label: "总主机数", value: ov.totalHostCount || 0, cls: ""},
            {label: "已完成主机", value: ov.finishedHostCount || 0, cls: ""},
            {label: "平均合规率", value: formatRate(ov.avgComplianceRate), cls: "rate"},
            {label: "问题主机数", value: ov.failHostCount || 0, cls: "fail"},
            {label: "问题规则数", value: ov.problemRuleCount || 0, cls: "fail"},
            {label: "PASS 规则", value: ov.passRuleCount || 0, cls: "pass"},
            {label: "FAIL 规则", value: ov.failRuleCount || 0, cls: "fail"},
            {label: "ERROR 规则", value: ov.errorRuleCount || 0, cls: "error"}
        ];
        document.getElementById("overviewCards").innerHTML = cards.map(function (card) {
            return '<div class="overview-card ' + card.cls + '"><div class="ov-label">' + card.label + '</div><div class="ov-value">' + card.value + '</div></div>';
        }).join("");
    }

    function renderTaskHostTable(taskId) {
        table.render({
            elem: "#taskHostResultTable",
            id: hostResultTableId,
            url: "/api/baseline/tasks/" + taskId + "/problem-hosts",
            method: "GET",
            headers: {Authorization: "Bearer " + AppAuth.getToken()},
            page: true,
            curr: resultState.page,
            limit: resultState.size,
            limits: [10, 20, 50],
            request: {pageName: "page", limitName: "size"},
            where: {assetTypeCode: resultState.assetTypeCode},
            parseData: parsePageData,
            cols: [[
                {field: "hostName", title: "主机名称", minWidth: 170, templet: function (d) {
                    return escapeHtml(d.hostName || ("主机#" + d.hostId));
                }},
                {field: "ipv4", title: "IP 地址", width: 150, templet: function (d) {
                    return escapeHtml(d.ipv4 || "-");
                }},
                {field: "assetTypes", title: "资产类型", width: 150, templet: function (d) {
                    return escapeHtml(d.assetTypes || "-");
                }},
                {field: "protectionLevels", title: "等保等级", width: 115, templet: function (d) {
                    return escapeHtml(d.protectionLevels || "-");
                }},
                {field: "complianceRate", title: "合规率", width: 110, align: "center", templet: function (d) {
                    return buildRateCell(d.complianceRate);
                }},
                {field: "passRuleCount", title: "PASS", width: 90, align: "center"},
                {field: "failRuleCount", title: "FAIL", width: 90, align: "center"},
                {field: "errorRuleCount", title: "ERROR", width: 90, align: "center"},
                {title: "操作", width: 170, align: "center", fixed: "right", templet: function () {
                    return '<button class="layui-btn layui-btn-primary layui-btn-xs" lay-event="detail">查看详情</button>'
                        + '<button class="layui-btn layui-btn-xs" lay-event="recheck">重新检测</button>';
                }}
            ]],
            done: function (res, curr) {
                resultState.page = curr;
                resultState.size = this.limit || resultState.size;
            }
        });
        table.on("tool(" + hostResultTableId + ")", function (obj) {
            if (obj.event === "detail") {
                openTaskRulePanel(obj.data);
            } else if (obj.event === "recheck") {
                recheckHost(obj.data.hostId);
            }
        });
    }

    function openTaskRulePanel(host) {
        if (!resultState.task || !host) {
            return;
        }
        resultState.selectedHost = host;
        document.getElementById("taskRulePanel").style.display = "";
        document.getElementById("taskRuleTitle").textContent = "本次任务规则明细：" + (host.hostName || ("主机#" + host.hostId));
        table.render({
            elem: "#taskRuleResultTable",
            id: ruleResultTableId,
            url: "/api/baseline/tasks/" + encodeURIComponent(resultState.task.id)
                + "/hosts/" + encodeURIComponent(host.hostId) + "/results",
            method: "GET",
            headers: {Authorization: "Bearer " + AppAuth.getToken()},
            page: false,
            parseData: function (res) {
                if (res.code !== 200) {
                    AppRequest.showMessage(res.message || "规则明细加载失败", 2, 2200);
                }
                return {code: res.code === 200 ? 0 : res.code, msg: res.message, count: (res.data || []).length, data: res.data || []};
            },
            cols: [[
                {field: "ruleName", title: "规则", minWidth: 180, templet: function (d) {
                    return escapeHtml(d.ruleName || ("规则#" + d.ruleId));
                }},
                {field: "category", title: "分类", width: 120, templet: function (d) {
                    return escapeHtml(d.category || "-");
                }},
                {field: "assetType", title: "资产类型", width: 125, templet: function (d) {
                    return escapeHtml(d.assetType || "-");
                }},
                {field: "protectionLevel", title: "等保", width: 90, templet: function (d) {
                    return escapeHtml(d.protectionLevel || "-");
                }},
                {field: "status", title: "状态", width: 90, align: "center", templet: function (d) {
                    return buildResultStatusTag(d.status);
                }},
                {field: "expectedValue", title: "期望值", width: 130, templet: function (d) {
                    return escapeHtml(shortText(d.expectedValue, 32));
                }},
                {field: "actualValue", title: "实际值", width: 130, templet: function (d) {
                    return escapeHtml(shortText(d.actualValue, 32));
                }},
                {field: "checkKey", title: "检测项", minWidth: 180, templet: function (d) {
                    return escapeHtml(shortText(d.checkKey, 60));
                }},
                {field: "evidence", title: "证据", minWidth: 180, templet: function (d) {
                    return buildEvidenceCell(d);
                }},
                {field: "scanTime", title: "检测时间", width: 170, templet: function (d) {
                    return AppUtils.formatDateTime(d.scanTime);
                }}
            ]]
        });
        table.on("tool(" + ruleResultTableId + ")", function (obj) {
            if (obj.event === "evidence") {
                showEvidence(obj.data);
            }
        });
        setTimeout(function () {
            document.getElementById("taskRulePanel").scrollIntoView({behavior: "smooth", block: "start"});
        }, 80);
    }

    function hideTaskRulePanel() {
        resultState.selectedHost = null;
        document.getElementById("taskRulePanel").style.display = "none";
        document.getElementById("taskRuleTitle").textContent = "本次任务规则明细";
        try {
            table.reload(ruleResultTableId, {data: []});
        } catch (ignore) {
            // table may not have been rendered yet
        }
    }

    function loadResultAssetTypeFilter() {
        AppRequest.request("/api/baseline/asset-types", {method: "GET"}, {showErrorMessage: false})
            .then(function (res) {
                var rows = res.data || [];
                var select = document.getElementById("resultAssetTypeFilter");
                select.innerHTML = '<option value="">全部资产类型</option>' + rows.map(function (assetType) {
                    return '<option value="' + escapeHtml(assetType.typeCode || "") + '">'
                        + escapeHtml(assetType.typeName || assetType.typeCode || "-")
                        + "（" + escapeHtml(assetType.typeCode || "-") + "）</option>";
                }).join("");
                select.value = resultState.assetTypeCode || "";
            });
    }

    function recheckHost(hostId) {
        AppRequest.request("/api/baseline/hosts/scan", {method: "POST", body: {hostIds: [hostId]}}, {showErrorMessage: true})
            .then(function (res) {
                AppRequest.showMessage((res.data || {}).message || "重新检测已下发", 1, 2200);
                refreshResultPanel(true);
            });
    }

    async function exportTaskResult(taskId) {
        var url = "/api/baseline/tasks/" + encodeURIComponent(taskId) + "/export";
        var res = await fetch(url, {headers: {Authorization: "Bearer " + AppAuth.getToken()}});
        if (!res.ok) {
            AppRequest.showMessage("导出失败", 2);
            return;
        }
        var blob = await res.blob();
        var link = document.createElement("a");
        link.href = URL.createObjectURL(blob);
        link.download = "baseline_task_" + taskId + "_results.csv";
        document.body.appendChild(link);
        link.click();
        link.remove();
        URL.revokeObjectURL(link.href);
    }

    function openTaskExportMenu(taskId) {
        layer.open({
            type: 1,
            title: "导出结果",
            area: ["260px", "220px"],
            content: '<div class="export-menu">'
                + '<button type="button" class="layui-btn layui-btn-fluid" data-format="csv">导出 CSV</button>'
                + '<button type="button" class="layui-btn layui-btn-normal layui-btn-fluid" data-format="pdf">导出 PDF</button>'
                + '<button type="button" class="layui-btn layui-btn-primary layui-btn-fluid" data-format="html">导出 HTML</button>'
                + '</div>',
            success: function (layero, index) {
                layero.find("[data-format]").on("click", function () {
                    var format = this.getAttribute("data-format");
                    layer.close(index);
                    exportTaskResult(taskId, format);
                });
            }
        });
    }

    async function exportTaskResult(taskId, format) {
        var safeFormat = format || "csv";
        var url = "/api/baseline/tasks/" + encodeURIComponent(taskId) + "/export?format=" + encodeURIComponent(safeFormat);
        var loading = layer.load(2);
        try {
            var res = await fetch(url, {headers: {Authorization: "Bearer " + AppAuth.getToken()}});
            if (!res.ok) {
                AppRequest.showMessage("导出失败", 2);
                return;
            }
            var blob = await res.blob();
            triggerDownload(blob, "baseline_task_" + taskId + "_results_" + timestamp() + "." + safeFormat);
            AppRequest.showMessage("导出成功", 1);
        } catch (e) {
            AppRequest.showMessage(e.message || "导出失败", 2);
        } finally {
            layer.close(loading);
        }
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

    function timestamp() {
        var now = new Date();
        function p(n) { return n < 10 ? "0" + n : String(n); }
        return now.getFullYear() + p(now.getMonth() + 1) + p(now.getDate())
            + p(now.getHours()) + p(now.getMinutes()) + p(now.getSeconds());
    }

    function buildProgressCell(d) {
        var total = Number(d.hostCount || 0);
        var done = Number(d.finishedHostCount || 0);
        var pct = total > 0 ? Math.round(done * 100 / total) : 0;
        return '<div class="progress-cell"><div class="progress-text">' + done + ' / ' + total + ' (' + pct + '%)</div>'
            + '<div class="progress-bar"><span style="width:' + pct + '%"></span></div></div>';
    }

    function buildTaskTypeTag(type) {
        var isRecheck = type === "RECHECK";
        return '<span class="task-type ' + (isRecheck ? "recheck" : "scan") + '">' + (isRecheck ? "复检任务" : "检测任务") + '</span>';
    }

    function buildStatusTag(status) {
        var map = {PENDING: ["pending", "待执行"], RUNNING: ["running", "执行中"], FINISHED: ["finished", "已完成"], FAILED: ["failed", "失败"]};
        var item = map[status] || ["pending", status || "-"];
        return '<span class="task-status ' + item[0] + '">' + item[1] + '</span>';
    }

    function buildResultStatusTag(status) {
        var normalized = String(status || "").toUpperCase();
        var map = {PASS: ["finished", "PASS"], FAIL: ["failed", "FAIL"], ERROR: ["error", "ERROR"]};
        var item = map[normalized] || ["pending", status || "-"];
        return '<span class="task-status ' + item[0] + '">' + item[1] + '</span>';
    }

    function buildEvidenceCell(row) {
        var text = row.evidence || "";
        if (!text) {
            return "-";
        }
        return '<span class="evidence-inline">' + escapeHtml(shortText(text, 52)) + '</span>'
            + '<button type="button" class="layui-btn layui-btn-primary layui-btn-xs evidence-btn" lay-event="evidence">完整</button>';
    }

    function showEvidence(row) {
        layer.open({
            type: 1,
            title: "检测证据",
            area: ["720px", "520px"],
            content: '<pre class="evidence-full">' + escapeHtml(row.evidence || "暂无证据") + '</pre>'
        });
    }

    function buildRateCell(rate) {
        if (rate == null || rate === "") {
            return '<span class="rate-cell">-</span>';
        }
        var num = Number(rate);
        var cls = num >= 90 ? "good" : (num >= 60 ? "warn" : "bad");
        return '<span class="rate-cell ' + cls + '">' + formatRate(rate) + '</span>';
    }

    function isHostOnline(host) {
        var value = String(host.online != null ? host.online : (host.status || host.agentStatus || host.onlineStatus || "")).toUpperCase();
        if (host.online === true || host.status === 1 || value === "ONLINE" || value === "1" || value === "TRUE") {
            return true;
        }
        return false;
    }

    function toggleSelection(map, id, checked) {
        if (checked) {
            map[id] = true;
        } else {
            delete map[id];
        }
    }

    function getProtectionLevelCode(root) {
        var select = root.querySelector('select[name="protectionLevelCode"]');
        return select ? (select.value || "").trim() : "";
    }

    function getSelectedAssetTypeCodes(root) {
        return Array.from(root.querySelectorAll("#assetTypeOptions input[type=checkbox]:checked"))
            .map(function (input) {
                return input.value;
            });
    }

    function formatRate(rate) {
        if (rate == null || rate === "") {
            return "-";
        }
        return Number(rate).toFixed(2) + "%";
    }

    function shortText(text, maxLength) {
        var value = String(text == null ? "" : text);
        if (value.length <= maxLength) {
            return value || "-";
        }
        return value.slice(0, maxLength) + "...";
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
