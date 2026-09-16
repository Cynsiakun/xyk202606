layui.use(["table", "form", "layer"], function () {
    var table = layui.table;
    var form = layui.form;
    var layer = layui.layer;
    var tableId = "machineTable";
    var editingId = null;
    var importFile = null;

    AppTable.renderPageTable(table, {
        elem: "#" + tableId,
        url: "/api/tenant-machines/list",
        cols: [[
            {field: "id", title: "ID", width: 80},
            {field: "macAddress", title: "MAC", minWidth: 160, templet: function (d) { return d.macAddress || "-"; }},
            {field: "machineId", title: "MachineId", minWidth: 180, templet: function (d) { return d.machineId || '<span class="status-tag warn">未绑定</span>'; }},
            {field: "machineBoundAt", title: "绑定时间", minWidth: 170, templet: function (d) { return AppUtils.formatDateTime(d.machineBoundAt); }},
            {field: "hostName", title: "主机名", minWidth: 150, templet: function (d) { return d.hostName || "-"; }},
            {field: "remark", title: "备注", minWidth: 160, templet: function (d) { return d.remark || "-"; }},
            {field: "status", title: "状态", width: 90, templet: function (d) {
                return d.status === 1
                    ? '<span class="status-tag success">启用</span>'
                    : '<span class="status-tag fail">禁用</span>';
            }},
            {field: "createdAt", title: "创建时间", minWidth: 170, templet: function (d) {
                return AppUtils.formatDateTime(d.createdAt);
            }},
            {title: "操作", width: 150, fixed: "right", templet: function () {
                var buttons = "";
                if (AppAuth.canPolicy("TENANT_MACHINE_UPDATE")) {
                    buttons += '<button type="button" class="layui-btn layui-btn-xs" lay-event="edit">编辑</button>';
                }
                if (AppAuth.canPolicy("TENANT_MACHINE_DELETE")) {
                    buttons += '<button type="button" class="layui-btn layui-btn-danger layui-btn-xs" lay-event="delete">删除</button>';
                }
                return buttons || '<span class="empty-text">-</span>';
            }}
        ]]
    });

    form.on("submit(machineSearchSubmit)", function (data) {
        AppTable.reload(table, tableId, {keyword: data.field.keyword || ""});
        return false;
    });

    form.on("submit(saveMachine)", function (data) {
        var formEl = document.querySelector('form[lay-filter="machineForm"]');
        AppUtils.clearFormError(formEl);
        var payload = {
            macAddress: data.field.macAddress || "",
            hostName: data.field.hostName || "",
            remark: data.field.remark || "",
            status: Number(data.field.status)
        };
        if (!payload.macAddress) {
            AppUtils.showFormError(formEl, "MAC 必填，MachineId 由客户端首次上线自动绑定");
            return false;
        }

        (async function () {
            try {
                await AppRequest.request(editingId ? "/api/tenant-machines/" + editingId : "/api/tenant-machines", {
                    method: editingId ? "PUT" : "POST",
                    body: payload
                }, {
                    successMessage: "保存成功",
                    showErrorMessage: false
                });
                layer.closeAll("page");
                table.reload(tableId);
            } catch (error) {
                AppUtils.showFormError(formEl, error.message);
            }
        })();
        return false;
    });

    table.on("tool(machineTable)", function (obj) {
        if (obj.event === "edit") {
            openDialog(obj.data);
        }
        if (obj.event === "delete") {
            confirmDelete(obj.data);
        }
    });

    var addMachineButton = document.getElementById("addMachineButton");
    if (AppAuth.canPolicy("TENANT_MACHINE_CREATE")) {
        addMachineButton.addEventListener("click", function () {
            openDialog(null);
        });
    } else {
        addMachineButton.style.display = "none";
    }

    var importMachineButton = document.getElementById("importMachineButton");
    if (AppAuth.canPolicy("TENANT_MACHINE_CREATE")) {
        importMachineButton.addEventListener("click", openImportDialog);
    } else {
        importMachineButton.style.display = "none";
    }

    document.getElementById("resetButton").addEventListener("click", function () {
        form.val("machineSearchForm", {keyword: ""});
        AppTable.reload(table, tableId, {keyword: ""});
    });

    function openDialog(machine) {
        editingId = machine ? machine.id : null;
        var index = layer.open({
            type: 1,
            title: editingId ? "编辑授权主机" : "新增授权主机",
            area: ["560px", "520px"],
            content: AppUtils.getTemplateHtml("machineFormTemplate"),
            success: function (layero) {
                form.render();
                form.val("machineForm", {
                    macAddress: machine ? machine.macAddress || "" : "",
                    hostName: machine ? machine.hostName || "" : "",
                    remark: machine ? machine.remark || "" : "",
                    status: machine && machine.status === 0 ? "0" : "1"
                });
                layero.find('[data-action="close"]').on("click", function () {
                    layer.close(index);
                });
            },
            end: function () {
                editingId = null;
            }
        });
    }

    function confirmDelete(machine) {
        AppDialog.confirm(layer, "确定删除该授权主机吗？", async function (index) {
            try {
                await AppRequest.request("/api/tenant-machines/" + machine.id, {method: "DELETE"}, {
                    successMessage: "删除成功"
                });
                layer.close(index);
                table.reload(tableId);
            } catch (error) {
                return null;
            }
            return null;
        });
    }

    function openImportDialog() {
        importFile = null;
        var viewportWidth = window.innerWidth || 560;
        var dialogWidth = Math.min(560, viewportWidth - 30);
        var index = layer.open({
            type: 1,
            title: "导入授权主机 CSV",
            area: [dialogWidth + "px", "360px"],
            content: AppUtils.getTemplateHtml("machineImportTemplate"),
            success: function (layero) {
                var fileInput = layero[0].querySelector("#machineCsvFile");
                var fileName = layero[0].querySelector("#machineCsvFileName");
                var submitButton = layero[0].querySelector("#submitMachineImportButton");
                var closeButton = layero[0].querySelector('[data-action="close"]');

                closeButton.addEventListener("click", function () {
                    layer.close(index);
                });

                fileInput.addEventListener("change", function (event) {
                    importFile = event.target.files && event.target.files[0] ? event.target.files[0] : null;
                    fileName.textContent = importFile ? ("已选择文件: " + importFile.name) : "尚未选择文件";
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
            var result = await AppRequest.request("/api/tenant-machines/import", {
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
            + '<div class="import-result-row"><span>成功总数</span><strong>' + Number(data.successCount || 0) + "</strong></div>"
            + '<div class="import-result-row"><span>新增数量</span><strong>' + Number(data.insertedCount || 0) + "</strong></div>"
            + '<div class="import-result-row"><span>更新数量</span><strong>' + Number(data.updatedCount || 0) + "</strong></div>"
            + '<div class="import-result-row"><span>失败数量</span><strong>' + Number(data.failureCount || 0) + "</strong></div>";
        if (errors.length > 0) {
            content += '<div class="import-result-errors">';
            errors.forEach(function (item) {
                content += '<div class="import-result-error">' + escapeHtml(item) + "</div>";
            });
            content += "</div>";
        }
        content += "</div>";
        layer.open({
            type: 1,
            title: "导入结果",
            area: ["560px", "440px"],
            content: content
        });
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
