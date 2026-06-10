layui.use(["table", "form", "layer"], function () {
    var table = layui.table;
    var form = layui.form;
    var layer = layui.layer;
    var tableId = "patchTable";
    var editingId = null;

    AppTable.renderPageTable(table, {
        elem: "#" + tableId,
        url: "/api/installed-patch/list",
        cols: [[
            {type: "checkbox", width: 54, fixed: "left"},
            {field: "hostId", title: "host_id", width: 90},
            {field: "patchId", title: "patch_id", minWidth: 130, templet: function (d) { return d.patchId || "-"; }},
            {field: "patchType", title: "patch_type", minWidth: 110, templet: function (d) { return d.patchType || "-"; }},
            {field: "productName", title: "product_name", minWidth: 170, templet: function (d) { return d.productName || "-"; }},
            {field: "productVersion", title: "product_version", minWidth: 120, templet: function (d) { return d.productVersion || "-"; }},
            {field: "installTime", title: "install_time", width: 168, templet: function (d) { return AppUtils.formatDateTime(d.installTime); }},
            {field: "installStatus", title: "install_status", width: 120, templet: function (d) { return d.installStatus || "-"; }},
            {field: "rebootRequired", title: "reboot_required", width: 110, templet: function (d) {
                return d.rebootRequired === 1
                        ? '<span class="status-tag fail">是</span>'
                        : '<span class="status-tag success">否</span>';
            }},
            {field: "isSecurityPatch", title: "is_security_patch", width: 118, templet: function (d) {
                return d.isSecurityPatch === 1
                        ? '<span class="status-tag success">是</span>'
                        : '<span class="status-tag info">否</span>';
            }},
            {title: "操作", width: 220, fixed: "right", templet: function () {
                var buttons = '<button type="button" class="layui-btn layui-btn-primary layui-btn-xs" lay-event="detail">详情</button>';
                if (AppAuth.hasPermission("installed-patch:update")) {
                    buttons += '<button type="button" class="layui-btn layui-btn-xs" lay-event="edit">编辑</button>';
                }
                if (AppAuth.hasPermission("installed-patch:delete")) {
                    buttons += '<button type="button" class="layui-btn layui-btn-danger layui-btn-xs" lay-event="delete">删除</button>';
                }
                return buttons;
            }}
        ]]
    });

    form.on("submit(patchSearchSubmit)", function (data) {
        AppTable.reload(table, tableId, normalizeSearch(data.field));
        return false;
    });

    form.on("submit(savePatch)", function (data) {
        var payload = normalizePayload(data.field);
        (async function () {
            try {
                if (editingId) {
                    await AppRequest.request("/api/installed-patch/" + editingId, {
                        method: "PUT",
                        body: payload
                    }, {successMessage: "保存成功"});
                } else {
                    await AppRequest.request("/api/installed-patch", {
                        method: "POST",
                        body: payload
                    }, {successMessage: "保存成功"});
                }
                layer.closeAll("page");
                table.reload(tableId);
            } catch (error) {
                return;
            }
        })();
        return false;
    });

    table.on("tool(patchTable)", function (obj) {
        if (obj.event === "detail") {
            openDetailDialog(obj.data);
        }
        if (obj.event === "edit") {
            openFormDialog(obj.data);
        }
        if (obj.event === "delete") {
            confirmDelete(obj.data);
        }
    });

    var addButton = document.getElementById("addButton");
    if (AppAuth.hasPermission("installed-patch:create")) {
        addButton.addEventListener("click", function () {
            openFormDialog(null);
        });
    } else {
        addButton.style.display = "none";
    }

    var batchDeleteButton = document.getElementById("batchDeleteButton");
    if (AppAuth.hasPermission("installed-patch:delete")) {
        batchDeleteButton.addEventListener("click", confirmBatchDelete);
    } else {
        batchDeleteButton.style.display = "none";
    }

    document.getElementById("refreshButton").addEventListener("click", function () {
        table.reloadData(tableId, {scrollPos: "fixed"});
    });

    document.getElementById("resetButton").addEventListener("click", function () {
        form.val("patchSearchForm", {
            keyword: "",
            installStatus: ""
        });
        form.render("select");
        AppTable.reload(table, tableId, {});
    });

    function openFormDialog(row) {
        editingId = row ? row.id : null;
        var viewportHeight = window.innerHeight || 760;
        var viewportWidth = window.innerWidth || 640;
        var dialogHeight = Math.min(760, viewportHeight - 30);
        var dialogWidth = Math.min(640, viewportWidth - 30);
        var index = layer.open({
            type: 1,
            title: editingId ? "编辑补丁记录" : "新增补丁记录",
            area: [dialogWidth + "px", dialogHeight + "px"],
            content: AppUtils.getTemplateHtml("patchFormTemplate"),
            success: function (layero) {
                form.val("patchForm", {
                    hostId: row && row.hostId != null ? row.hostId : "",
                    patchId: row ? row.patchId || "" : "",
                    patchType: row ? row.patchType || "" : "",
                    productName: row ? row.productName || "" : "",
                    productVersion: row ? row.productVersion || "" : "",
                    installTime: formatDateTimeLocal(row ? row.installTime : ""),
                    installStatus: row ? row.installStatus || "" : "",
                    source: row ? row.source || "" : "",
                    signatureStatus: row ? row.signatureStatus || "" : "",
                    rebootRequired: row ? row.rebootRequired === 1 : false,
                    isSecurityPatch: row ? row.isSecurityPatch === 1 : false,
                    supersededBy: row ? row.supersededBy || "" : "",
                    rawData: row ? row.rawData || "" : "",
                    scanTime: formatDateTimeLocal(row ? row.scanTime : "")
                });
                form.render(null, "patchForm");
                layero.find('[data-action="close"]').on("click", function () {
                    layer.close(index);
                });
                AppUtils.bindLiveValidation(layero[0], {
                    fields: [
                        {selector: 'input[name="hostId"]', required: true, message: "host_id 不能为空"},
                        {selector: 'input[name="patchId"]', required: true, message: "patch_id 不能为空"}
                    ],
                    saveButton: 'button[lay-filter="savePatch"]'
                });
            },
            end: function () {
                editingId = null;
            }
        });
    }

    function openDetailDialog(row) {
        var viewportHeight = window.innerHeight || 640;
        var viewportWidth = window.innerWidth || 700;
        var dialogHeight = Math.min(620, viewportHeight - 30);
        var dialogWidth = Math.min(720, viewportWidth - 30);
        layer.open({
            type: 1,
            title: "补丁详情",
            area: [dialogWidth + "px", dialogHeight + "px"],
            content: buildDetailHtml(row)
        });
    }

    function buildDetailHtml(row) {
        var sections = [
            {title: "基础字段", items: [
                {label: "host_id", value: row.hostId},
                {label: "patch_id", value: row.patchId},
                {label: "patch_type", value: row.patchType},
                {label: "product_name", value: row.productName},
                {label: "product_version", value: row.productVersion},
                {label: "install_time", value: AppUtils.formatDateTime(row.installTime)},
                {label: "install_status", value: row.installStatus},
                {label: "reboot_required", value: row.rebootRequired === 1 ? '<span class="status-tag fail">是</span>' : '<span class="status-tag success">否</span>', raw: true},
                {label: "is_security_patch", value: row.isSecurityPatch === 1 ? '<span class="status-tag success">是</span>' : '<span class="status-tag info">否</span>', raw: true}
            ]},
            {title: "扩展信息", items: [
                {label: "source", value: row.source},
                {label: "signature_status", value: row.signatureStatus},
                {label: "superseded_by", value: row.supersededBy},
                {label: "raw_data", value: row.rawData, full: true},
                {label: "scan_time", value: AppUtils.formatDateTime(row.scanTime)}
            ]},
            {title: "时间信息", items: [
                {label: "created_at", value: AppUtils.formatDateTime(row.createdAt)},
                {label: "updated_at", value: AppUtils.formatDateTime(row.updatedAt)}
            ]}
        ];

        var html = '<div class="patch-detail">';
        sections.forEach(function (section) {
            html += '<div class="patch-detail-section">';
            html += '<div class="patch-detail-section-title">' + section.title + '</div>';
            html += '<div class="patch-detail-grid">';
            section.items.forEach(function (item) {
                html += '<div class="patch-detail-item' + (item.full ? ' full' : '') + '">';
                html += '<span class="patch-detail-label">' + item.label + '</span>';
                html += '<span class="patch-detail-value">' + (item.raw ? item.value : formatValue(item.value)) + '</span>';
                html += '</div>';
            });
            html += '</div></div>';
        });
        html += '</div>';
        return html;
    }

    function normalizePayload(field) {
        return {
            hostId: field.hostId === "" ? null : Number(field.hostId),
            patchId: field.patchId || "",
            patchType: field.patchType || "",
            productName: field.productName || "",
            productVersion: field.productVersion || "",
            installTime: field.installTime ? normalizeDateTime(field.installTime) : null,
            installStatus: field.installStatus || "",
            source: field.source || "",
            signatureStatus: field.signatureStatus || "",
            rebootRequired: field.rebootRequired === "on" ? 1 : 0,
            supersededBy: field.supersededBy || "",
            isSecurityPatch: field.isSecurityPatch === "on" ? 1 : 0,
            rawData: field.rawData || "",
            scanTime: field.scanTime ? normalizeDateTime(field.scanTime) : null
        };
    }

    function normalizeSearch(field) {
        var data = {};
        if (field.keyword) {
            data.keyword = field.keyword;
        }
        if (field.installStatus) {
            data.installStatus = field.installStatus;
        }
        return data;
    }

    function confirmDelete(row) {
        AppDialog.confirm(layer, "确定删除补丁“" + (row.patchId || row.id) + "”吗？", async function (index) {
            try {
                await AppRequest.request("/api/installed-patch/" + row.id, {
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
                await AppRequest.request("/api/installed-patch/batch-delete", {
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

    function formatValue(value) {
        if (value == null || value === "") {
            return "-";
        }
        return escapeHtml(String(value));
    }

    function formatDateTimeLocal(value) {
        if (!value) {
            return "";
        }
        var date = new Date(value);
        if (Number.isNaN(date.getTime())) {
            return "";
        }
        var year = date.getFullYear();
        var month = String(date.getMonth() + 1).padStart(2, "0");
        var day = String(date.getDate()).padStart(2, "0");
        var hour = String(date.getHours()).padStart(2, "0");
        var minute = String(date.getMinutes()).padStart(2, "0");
        return year + "-" + month + "-" + day + "T" + hour + ":" + minute;
    }

    function normalizeDateTime(value) {
        return value ? value.replace("T", " ") + ":00" : null;
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
