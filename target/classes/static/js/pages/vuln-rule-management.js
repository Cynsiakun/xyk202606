layui.use(["table", "form", "layer"], function () {
    var table = layui.table;
    var form = layui.form;
    var layer = layui.layer;
    var tableId = "vulnRuleTable";
    var editingId = null;
    var importFile = null;

    AppTable.renderPageTable(table, {
        elem: "#" + tableId,
        url: "/api/vuln-rule/list",
        cols: [[
            {type: "checkbox", width: 54, fixed: "left"},
            {field: "ruleCode", title: "规则编码", minWidth: 160, templet: fallbackCell("ruleCode")},
            {field: "cveId", title: "CVE编号", minWidth: 150, templet: fallbackCell("cveId")},
            {field: "productName", title: "产品名称", minWidth: 160, templet: fallbackCell("productName")},
            {field: "productType", title: "产品类型", width: 110, templet: fallbackCell("productType")},
            {field: "severity", title: "风险等级", width: 110, templet: function (d) { return buildSeverityTag(d.severity); }},
            {field: "title", title: "漏洞标题", minWidth: 220, templet: fallbackCell("title")},
            {field: "verifyType", title: "验证方式", width: 120, templet: fallbackCell("verifyType")},
            {field: "enabled", title: "启用状态", width: 110, templet: function (d) { return buildEnabledTag(d.enabled); }},
            {field: "createdAt", title: "创建时间", width: 170, templet: function (d) { return AppUtils.formatDateTime(d.createdAt); }},
            {title: "操作", width: 220, fixed: "right", templet: function () {
                var buttons = '<button type="button" class="layui-btn layui-btn-primary layui-btn-xs" lay-event="detail">查看</button>';
                if (AppAuth.hasPermission("vuln-rule:update")) {
                    buttons += '<button type="button" class="layui-btn layui-btn-xs" lay-event="edit">编辑</button>';
                }
                if (AppAuth.hasPermission("vuln-rule:delete")) {
                    buttons += '<button type="button" class="layui-btn layui-btn-danger layui-btn-xs" lay-event="delete">删除</button>';
                }
                return buttons;
            }}
        ]]
    });

    form.on("submit(vulnRuleSearchSubmit)", function (data) {
        AppTable.reload(table, tableId, normalizeSearch(data.field));
        return false;
    });

    form.on("submit(saveVulnRule)", function (data) {
        var payload = normalizePayload(data.field);
        var container = document.querySelector(".vuln-rule-form");
        AppUtils.clearFormError(container);
        if (!validateVerifyRule(payload.verifyRule, container)) {
            return false;
        }
        (async function () {
            try {
                if (editingId) {
                    await AppRequest.request("/api/vuln-rule/" + editingId, {
                        method: "PUT",
                        body: payload
                    }, {successMessage: "保存成功"});
                } else {
                    await AppRequest.request("/api/vuln-rule", {
                        method: "POST",
                        body: payload
                    }, {successMessage: "保存成功"});
                }
                layer.closeAll("page");
                table.reload(tableId);
            } catch (error) {
                AppUtils.showFormError(container, error.message);
            }
        })();
        return false;
    });

    table.on("tool(vulnRuleTable)", function (obj) {
        if (obj.event === "detail") {
            openDetailDialog(obj.data.id);
        }
        if (obj.event === "edit") {
            openFormDialog(obj.data.id);
        }
        if (obj.event === "delete") {
            confirmDelete(obj.data);
        }
    });

    bindToolbar();

    function bindToolbar() {
        var addButton = document.getElementById("addButton");
        if (AppAuth.hasPermission("vuln-rule:create")) {
            addButton.addEventListener("click", function () {
                openFormDialog(null);
            });
        } else {
            addButton.style.display = "none";
        }

        var importButton = document.getElementById("importButton");
        if (AppAuth.hasPermission("vuln-rule:create")) {
            importButton.addEventListener("click", openImportDialog);
        } else {
            importButton.style.display = "none";
        }

        var batchDeleteButton = document.getElementById("batchDeleteButton");
        if (AppAuth.hasPermission("vuln-rule:delete")) {
            batchDeleteButton.addEventListener("click", confirmBatchDelete);
        } else {
            batchDeleteButton.style.display = "none";
        }

        document.getElementById("refreshButton").addEventListener("click", function () {
            table.reloadData(tableId, {scrollPos: "fixed"});
        });

        document.getElementById("resetButton").addEventListener("click", function () {
            form.val("vulnRuleSearchForm", {
                ruleCode: "",
                cveId: "",
                productName: "",
                severity: "",
                enabled: ""
            });
            form.render("select");
            AppTable.reload(table, tableId, {});
        });
    }

    async function openFormDialog(id) {
        editingId = id;
        var row = id ? await fetchById(id) : null;
        if (id && !row) {
            return;
        }
        var viewportHeight = window.innerHeight || 860;
        var viewportWidth = window.innerWidth || 940;
        var dialogHeight = Math.min(840, viewportHeight - 30);
        var dialogWidth = Math.min(920, viewportWidth - 30);
        var index = layer.open({
            type: 1,
            title: editingId ? "编辑漏洞规则" : "新增漏洞规则",
            area: [dialogWidth + "px", dialogHeight + "px"],
            content: AppUtils.getTemplateHtml("vulnRuleFormTemplate"),
            success: function (layero) {
                form.val("vulnRuleForm", buildFormValues(row));
                form.render(null, "vulnRuleForm");
                layero.find('[data-action="close"]').on("click", function () {
                    layer.close(index);
                });
                AppUtils.bindLiveValidation(layero[0], {
                    fields: [
                        {selector: 'input[name="ruleCode"]', required: true, message: "规则编码不能为空"},
                        {selector: 'input[name="title"]', required: true, message: "漏洞标题不能为空"},
                        {selector: 'input[name="productName"]', required: true, message: "产品名称不能为空"}
                    ],
                    saveButton: 'button[lay-filter="saveVulnRule"]'
                });
            },
            end: function () {
                editingId = null;
            }
        });
    }

    function openImportDialog() {
        importFile = null;
        var viewportWidth = window.innerWidth || 560;
        var dialogWidth = Math.min(560, viewportWidth - 30);
        var index = layer.open({
            type: 1,
            title: "导入漏洞规则 CSV",
            area: [dialogWidth + "px", "380px"],
            content: AppUtils.getTemplateHtml("vulnRuleImportTemplate"),
            success: function (layero) {
                var fileInput = layero[0].querySelector("#vulnRuleCsvFile");
                var fileName = layero[0].querySelector("#vulnRuleCsvFileName");
                var submitButton = layero[0].querySelector("#submitImportButton");
                var closeButton = layero[0].querySelector('[data-action="close"]');

                closeButton.addEventListener("click", function () {
                    layer.close(index);
                });

                fileInput.addEventListener("change", function (event) {
                    importFile = event.target.files && event.target.files[0] ? event.target.files[0] : null;
                    fileName.textContent = importFile ? ("已选择文件：" + importFile.name) : "尚未选择文件";
                });

                submitButton.addEventListener("click", function () {
                    submitImport(submitButton, index);
                });
            },
            end: function () {
                importFile = null;
            }
        });
    }

    async function submitImport(button, dialogIndex) {
        if (!importFile) {
            AppRequest.showMessage("请先选择 CSV 文件", 2);
            return;
        }
        var formData = new FormData();
        formData.append("file", importFile);
        button.disabled = true;
        button.classList.add("layui-btn-disabled");
        button.textContent = "导入中...";
        try {
            var result = await AppRequest.request("/vulnRule/import", {
                method: "POST",
                body: formData
            }, {
                successMessage: "导入完成"
            });
            layer.close(dialogIndex);
            table.reload(tableId);
            showImportResult(result.data || {});
        } catch (error) {
            button.disabled = false;
            button.classList.remove("layui-btn-disabled");
            button.textContent = "开始导入";
        }
    }

    function showImportResult(data) {
        var errors = Array.isArray(data.errorMessages) ? data.errorMessages : [];
        var content = '<div class="import-result">'
            + '<div class="import-result-row"><span>成功总数</span><strong>' + Number(data.successCount || 0) + '</strong></div>'
            + '<div class="import-result-row"><span>新增数量</span><strong>' + Number(data.insertedCount || 0) + '</strong></div>'
            + '<div class="import-result-row"><span>更新数量</span><strong>' + Number(data.updatedCount || 0) + '</strong></div>'
            + '<div class="import-result-row"><span>失败数量</span><strong>' + Number(data.failureCount || 0) + '</strong></div>';
        if (errors.length > 0) {
            content += '<div class="import-result-errors">';
            errors.forEach(function (item) {
                content += '<div class="import-result-error">' + escapeHtml(item) + '</div>';
            });
            content += '</div>';
        }
        content += '</div>';
        layer.open({
            type: 1,
            title: "导入结果",
            area: ["560px", "440px"],
            content: content
        });
    }

    async function openDetailDialog(id) {
        var row = await fetchById(id);
        if (!row) {
            return;
        }
        var viewportHeight = window.innerHeight || 860;
        var viewportWidth = window.innerWidth || 940;
        var dialogHeight = Math.min(840, viewportHeight - 30);
        var dialogWidth = Math.min(920, viewportWidth - 30);
        layer.open({
            type: 1,
            title: "漏洞规则详情",
            area: [dialogWidth + "px", dialogHeight + "px"],
            content: buildDetailHtml(row)
        });
    }

    async function fetchById(id) {
        try {
            var result = await AppRequest.request("/api/vuln-rule/" + id, {method: "GET"});
            return result.data || null;
        } catch (error) {
            return null;
        }
    }

    function buildFormValues(row) {
        return {
            ruleCode: row ? row.ruleCode || "" : "",
            cveId: row ? row.cveId || "" : "",
            category: row ? row.category || "" : "",
            severity: row ? row.severity || "" : "",
            title: row ? row.title || "" : "",
            description: row ? row.description || "" : "",
            suggestion: row ? row.suggestion || "" : "",
            productType: row ? row.productType || "" : "",
            productName: row ? row.productName || "" : "",
            matchType: row ? row.matchType || "" : "",
            affectedVersionExpr: row ? row.affectedVersionExpr || "" : "",
            verifyType: row ? row.verifyType || "" : "",
            verifyRule: row ? row.verifyRule || "" : "",
            enabled: row ? row.enabled !== 0 : true
        };
    }

    function buildDetailHtml(row) {
        var sections = [
            {
                title: "基础信息",
                items: [
                    {label: "规则编码", value: row.ruleCode},
                    {label: "CVE编号", value: row.cveId},
                    {label: "分类", value: row.category},
                    {label: "风险等级", value: row.severity},
                    {label: "漏洞标题", value: row.title, full: true},
                    {label: "描述", value: row.description, full: true},
                    {label: "处置建议", value: row.suggestion, full: true}
                ]
            },
            {
                title: "匹配规则",
                items: [
                    {label: "产品类型", value: row.productType},
                    {label: "产品名称", value: row.productName},
                    {label: "匹配方式", value: row.matchType},
                    {label: "版本表达式", value: row.affectedVersionExpr}
                ]
            },
            {
                title: "验证配置",
                items: [
                    {label: "验证方式", value: row.verifyType},
                    {label: "启用状态", value: row.enabled === 1 ? '<span class="status-tag success">已启用</span>' : '<span class="status-tag info">已禁用</span>', raw: true},
                    {label: "验证规则", value: row.verifyRule, full: true, code: true}
                ]
            },
            {
                title: "时间信息",
                items: [
                    {label: "创建时间", value: AppUtils.formatDateTime(row.createdAt)},
                    {label: "更新时间", value: AppUtils.formatDateTime(row.updatedAt)}
                ]
            }
        ];

        var html = '<div class="vuln-rule-detail">';
        sections.forEach(function (section) {
            html += '<div class="vuln-rule-detail-section">';
            html += '<div class="vuln-rule-detail-section-title">' + section.title + '</div>';
            html += '<div class="vuln-rule-detail-grid">';
            section.items.forEach(function (item) {
                html += '<div class="vuln-rule-detail-item' + (item.full ? ' full' : '') + '">';
                html += '<span class="vuln-rule-detail-label">' + item.label + '</span>';
                if (item.raw) {
                    html += '<span class="vuln-rule-detail-value">' + item.value + '</span>';
                } else if (item.code) {
                    html += '<pre class="vuln-rule-detail-code">' + formatValue(item.value) + '</pre>';
                } else {
                    html += '<span class="vuln-rule-detail-value">' + formatValue(item.value) + '</span>';
                }
                html += '</div>';
            });
            html += '</div></div>';
        });
        html += '</div>';
        return html;
    }

    function normalizePayload(field) {
        return {
            ruleCode: field.ruleCode || "",
            cveId: field.cveId || "",
            category: field.category || "",
            severity: field.severity || "",
            title: field.title || "",
            description: field.description || "",
            suggestion: field.suggestion || "",
            productType: field.productType || "",
            productName: field.productName || "",
            matchType: field.matchType || "",
            affectedVersionExpr: field.affectedVersionExpr || "",
            verifyType: field.verifyType || "",
            verifyRule: field.verifyRule || "",
            enabled: field.enabled === "on" ? 1 : 0
        };
    }

    function normalizeSearch(field) {
        var data = {};
        if (field.ruleCode) {
            data.ruleCode = field.ruleCode;
        }
        if (field.cveId) {
            data.cveId = field.cveId;
        }
        if (field.productName) {
            data.productName = field.productName;
        }
        if (field.severity) {
            data.severity = field.severity;
        }
        if (field.enabled !== "") {
            data.enabled = Number(field.enabled);
        }
        return data;
    }

    function validateVerifyRule(verifyRule, formEl) {
        if (!verifyRule || !verifyRule.trim()) {
            return true;
        }
        var trimmed = verifyRule.trim();
        if (!(isJsonLike(trimmed))) {
            return true;
        }
        try {
            JSON.parse(trimmed);
            return true;
        } catch (error) {
            AppUtils.showFormError(formEl, "verify_rule 看起来像 JSON，但格式不合法");
            return false;
        }
    }

    function isJsonLike(value) {
        return (value.startsWith("{") && value.endsWith("}"))
            || (value.startsWith("[") && value.endsWith("]"));
    }

    function confirmDelete(row) {
        AppDialog.confirm(layer, '确定删除规则 "' + escapeHtml(row.ruleCode || row.id) + '" 吗？', async function (index) {
            try {
                await AppRequest.request("/api/vuln-rule/" + row.id, {
                    method: "DELETE"
                }, {successMessage: "删除成功"});
                layer.close(index);
                table.reload(tableId);
            } catch (error) {
                return;
            }
        });
    }

    function confirmBatchDelete() {
        var checkStatus = table.checkStatus(tableId);
        var ids = (checkStatus.data || []).map(function (item) { return item.id; });
        if (ids.length === 0) {
            AppRequest.showMessage("请先选择需要删除的记录", 2);
            return;
        }
        AppDialog.confirm(layer, "确定批量删除选中的 " + ids.length + " 条记录吗？", async function (index) {
            try {
                await AppRequest.request("/api/vuln-rule/batch-delete", {
                    method: "POST",
                    body: {ids: ids}
                }, {successMessage: "批量删除成功"});
                layer.close(index);
                table.reload(tableId);
            } catch (error) {
                return;
            }
        });
    }

    function fallbackCell(field) {
        return function (d) {
            return formatValue(d[field]);
        };
    }

    function buildEnabledTag(enabled) {
        if (enabled === 1) {
            return '<span class="status-tag success">已启用</span>';
        }
        return '<span class="status-tag info">已禁用</span>';
    }

    function buildSeverityTag(severity) {
        if (!severity) {
            return "-";
        }
        var typeMap = {
            CRITICAL: "danger",
            HIGH: "warn",
            MEDIUM: "medium",
            LOW: "success"
        };
        var type = typeMap[String(severity).toUpperCase()] || "info";
        return '<span class="risk-tag ' + type + '">' + escapeHtml(String(severity)) + '</span>';
    }

    function formatValue(value) {
        if (value == null || value === "") {
            return "-";
        }
        return escapeHtml(String(value));
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
