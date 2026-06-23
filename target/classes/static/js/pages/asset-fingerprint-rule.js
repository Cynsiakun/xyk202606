layui.use(["table", "form", "layer"], function () {
    var table = layui.table;
    var form = layui.form;
    var layer = layui.layer;

    var tableId = "ruleTable";
    var state = {page: 1, size: 10, keyword: "", category: "", port: "", enabled: ""};
    var editingRule = null;

    init();

    function init() {
        if (!(AppAuth.isSuperAdmin() && AppAuth.getTenantId() === 0 && AppAuth.canPolicy("PLATFORM_ASSET_FINGERPRINT_RULE_VIEW"))) {
            document.body.innerHTML = '<div class="page-shell"><div class="table-card">无权访问该页面</div></div>';
            return;
        }
        bindToolbar();
        bindDrawer();
        renderTable();
    }

    function bindToolbar() {
        document.getElementById("searchButton").addEventListener("click", applyFilters);
        document.getElementById("refreshButton").addEventListener("click", function () { reloadTable(false); });
        document.getElementById("addButton").addEventListener("click", function () { openFormDrawer(null); });
        document.getElementById("enabledSelect").addEventListener("change", function () {
            state.enabled = this.value || "";
            state.page = 1;
            reloadTable(false);
        });
        ["keywordInput", "categoryInput", "portInput"].forEach(function (id) {
            document.getElementById(id).addEventListener("keydown", function (event) {
                if (event.key === "Enter") {
                    applyFilters();
                }
            });
        });
    }

    function bindDrawer() {
        document.getElementById("drawerMask").addEventListener("click", closeDrawer);
        document.getElementById("drawerCloseButton").addEventListener("click", closeDrawer);
    }

    function applyFilters() {
        state.keyword = document.getElementById("keywordInput").value.trim();
        state.category = document.getElementById("categoryInput").value.trim();
        state.port = document.getElementById("portInput").value.trim();
        state.page = 1;
        reloadTable(false);
    }

    function renderTable() {
        table.render({
            elem: "#" + tableId,
            id: tableId,
            url: "/api/asset-fingerprint-rules",
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
                {field: "ruleCode", title: "规则编码", width: 150, templet: escapeField("ruleCode")},
                {field: "name", title: "规则名称", minWidth: 180, templet: escapeField("name")},
                {field: "category", title: "分类", width: 120, templet: escapeField("category")},
                {field: "subCategory", title: "子类型", width: 120, templet: escapeField("subCategory")},
                {field: "port", title: "端口", width: 90, align: "center"},
                {field: "product", title: "产品", minWidth: 140, templet: escapeField("product")},
                {field: "confidence", title: "置信度", width: 90, align: "center"},
                {field: "priority", title: "优先级", width: 90, align: "center"},
                {field: "enabled", title: "状态", width: 90, align: "center", templet: function (d) {
                    return Number(d.enabled) === 1 ? '<span class="enabled on">启用</span>' : '<span class="enabled off">停用</span>';
                }},
                {field: "updatedAt", title: "更新时间", width: 170, templet: function (d) {
                    return AppUtils.formatDateTime(d.updatedAt);
                }},
                {title: "操作", width: 220, fixed: "right", align: "center", templet: function () {
                    return '<button class="layui-btn layui-btn-primary layui-btn-xs" lay-event="detail">详情</button>'
                        + '<button class="layui-btn layui-btn-xs" lay-event="edit">编辑</button>'
                        + '<button class="layui-btn layui-btn-danger layui-btn-xs" lay-event="delete">删除</button>';
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
            } else if (obj.event === "delete") {
                deleteRule(obj.data);
            }
        });
    }

    function reloadTable(silent) {
        table.reload(tableId, {page: {curr: state.page}, limit: state.size, where: buildQuery()}, silent);
    }

    function buildQuery() {
        return {
            keyword: state.keyword,
            category: state.category,
            port: state.port ? Number(state.port) : null,
            enabled: state.enabled
        };
    }

    function parsePageData(res) {
        var pageData = res.data || {};
        return {code: res.code === 200 ? 0 : res.code, msg: res.message, count: pageData.total || 0, data: pageData.list || []};
    }

    function openFormDrawer(id) {
        editingRule = null;
        if (id) {
            AppRequest.request("/api/asset-fingerprint-rules/" + encodeURIComponent(id), {method: "GET"}).then(function (res) {
                editingRule = res.data;
                renderForm(editingRule);
            });
        } else {
            renderForm(null);
        }
    }

    function renderForm(rule) {
        document.getElementById("drawerKicker").textContent = rule ? "EDIT RULE" : "CREATE RULE";
        document.getElementById("drawerTitle").textContent = rule ? "编辑规则" : "新增规则";
        document.getElementById("drawerHint").textContent = "固定三层：banner_regex > port+service > port fallback";
        document.getElementById("drawerBody").innerHTML = AppUtils.getTemplateHtml("ruleFormTemplate");
        var root = document.getElementById("drawerBody");
        root.querySelector('[data-action="close"]').addEventListener("click", closeDrawer);
        root.querySelector("#saveRuleButton").addEventListener("click", saveRule);

        form.val("ruleForm", {
            ruleCode: rule ? rule.ruleCode || "" : "",
            name: rule ? rule.name || "" : "",
            category: rule ? rule.category || "" : "",
            subCategory: rule ? rule.subCategory || "" : "",
            protocol: rule ? rule.protocol || "tcp" : "tcp",
            port: rule ? rule.port || "" : "",
            product: rule ? rule.product || "" : "",
            vendor: rule ? rule.vendor || "" : "",
            confidence: rule ? (rule.confidence == null ? 80 : rule.confidence) : 80,
            priority: rule ? (rule.priority == null ? 100 : rule.priority) : 100,
            versionExpr: rule ? rule.versionExpr || "" : "",
            bannerRegex: rule ? rule.bannerRegex || "" : "",
            description: rule ? rule.description || "" : "",
            enabled: !rule || Number(rule.enabled) === 1
        });
        root.querySelector('[name="enabled"]').checked = !rule || Number(rule.enabled) === 1;
        if (rule) {
            root.querySelector('input[name="ruleCode"]').disabled = true;
        }
        form.render(null, "ruleForm");
        openDrawer();
    }

    function openDetailDrawer(id) {
        AppRequest.request("/api/asset-fingerprint-rules/" + encodeURIComponent(id), {method: "GET"}).then(function (res) {
            var rule = res.data || {};
            document.getElementById("drawerKicker").textContent = "RULE DETAIL";
            document.getElementById("drawerTitle").textContent = rule.name || ("规则#" + rule.id);
            document.getElementById("drawerHint").textContent = "规则编码：" + (rule.ruleCode || "-");
            document.getElementById("drawerBody").innerHTML = buildDetailHtml(rule);
            openDrawer();
        });
    }

    function saveRule() {
        var root = document.getElementById("drawerBody");
        var body = collectForm(root);
        if (!body) return;
        var url = "/api/asset-fingerprint-rules" + (editingRule ? "/" + editingRule.id : "");
        var method = editingRule ? "PUT" : "POST";
        var button = root.querySelector("#saveRuleButton");
        button.disabled = true;
        button.classList.add("layui-btn-disabled");
        AppRequest.request(url, {method: method, body: body}, {showErrorMessage: true})
            .then(function () {
                AppRequest.showMessage(editingRule ? "规则已更新" : "规则已创建", 1, 1600);
                closeDrawer();
                reloadTable(false);
            })
            .catch(function () {
                button.disabled = false;
                button.classList.remove("layui-btn-disabled");
            });
    }

    function deleteRule(rule) {
        layer.confirm('确定删除规则 "' + escapeHtml(rule.name || rule.ruleCode) + '" 吗？', {icon: 3, title: "删除规则"}, function (index) {
            layer.close(index);
            AppRequest.request("/api/asset-fingerprint-rules/" + rule.id, {method: "DELETE"}, {showErrorMessage: true})
                .then(function () {
                    AppRequest.showMessage("删除成功", 1, 1600);
                    reloadTable(false);
                });
        });
    }

    function collectForm(root) {
        var body = {
            ruleCode: value(root, "ruleCode"),
            name: value(root, "name"),
            category: value(root, "category"),
            subCategory: value(root, "subCategory"),
            protocol: value(root, "protocol") || "tcp",
            port: Number(value(root, "port")),
            product: value(root, "product"),
            vendor: value(root, "vendor"),
            confidence: Number(value(root, "confidence") || 80),
            priority: Number(value(root, "priority") || 100),
            versionExpr: value(root, "versionExpr"),
            bannerRegex: value(root, "bannerRegex"),
            description: value(root, "description"),
            enabled: root.querySelector('[name="enabled"]').checked ? 1 : 0
        };
        var required = [
            ["ruleCode", "规则编码"],
            ["name", "规则名称"],
            ["category", "分类"],
            ["protocol", "协议"],
            ["product", "产品名"]
        ];
        for (var i = 0; i < required.length; i++) {
            if (!body[required[i][0]]) {
                AppRequest.showMessage("请填写" + required[i][1], 2);
                return null;
            }
        }
        if (!body.port || body.port < 1 || body.port > 65535) {
            AppRequest.showMessage("端口必须在 1-65535 之间", 2);
            return null;
        }
        return body;
    }

    function buildDetailHtml(rule) {
        return '<div class="detail-grid">'
            + detailItem("规则编码", rule.ruleCode)
            + detailItem("规则名称", rule.name)
            + detailItem("分类", rule.category)
            + detailItem("子类型", rule.subCategory)
            + detailItem("协议", rule.protocol)
            + detailItem("端口", rule.port)
            + detailItem("产品", rule.product)
            + detailItem("厂商", rule.vendor)
            + detailItem("置信度", rule.confidence)
            + detailItem("优先级", rule.priority)
            + detailItem("状态", Number(rule.enabled) === 1 ? "启用" : "停用")
            + detailItem("更新时间", AppUtils.formatDateTime(rule.updatedAt))
            + '</div>'
            + '<div class="detail-section"><h3>Banner Regex</h3><pre>' + escapeHtml(rule.bannerRegex || "-") + '</pre></div>'
            + '<div class="detail-section"><h3>描述</h3><pre>' + escapeHtml(rule.description || "-") + '</pre></div>';
    }

    function openDrawer() {
        document.getElementById("drawerMask").style.display = "";
        document.getElementById("ruleDrawer").style.display = "";
        setTimeout(function () { document.getElementById("ruleDrawer").classList.add("open"); }, 20);
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

    function detailItem(label, value) {
        return '<div class="detail-item"><label>' + escapeHtml(label) + '</label><div>' + escapeHtml(value == null || value === "" ? "-" : value) + '</div></div>';
    }

    function value(root, name) {
        var el = root.querySelector('[name="' + name + '"]');
        return el ? (el.value || "").trim() : "";
    }

    function escapeField(field) {
        return function (row) {
            return escapeHtml(row && row[field] != null && row[field] !== "" ? row[field] : "-");
        };
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
