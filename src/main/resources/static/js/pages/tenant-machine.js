layui.use(["table", "form", "layer"], function () {
    var table = layui.table;
    var form = layui.form;
    var layer = layui.layer;
    var tableId = "machineTable";
    var editingId = null;

    AppTable.renderPageTable(table, {
        elem: "#" + tableId,
        url: "/api/tenant-machines/list",
        cols: [[
            {field: "id", title: "ID", width: 80},
            {field: "machineId", title: "MachineId", minWidth: 180, templet: function (d) { return d.machineId || "-"; }},
            {field: "macAddress", title: "MAC", minWidth: 160, templet: function (d) { return d.macAddress || "-"; }},
            {field: "hostName", title: "\u4e3b\u673a\u540d", minWidth: 150, templet: function (d) { return d.hostName || "-"; }},
            {field: "remark", title: "\u5907\u6ce8", minWidth: 160, templet: function (d) { return d.remark || "-"; }},
            {field: "status", title: "\u72b6\u6001", width: 90, templet: function (d) {
                return d.status === 1
                    ? '<span class="status-tag success">\u542f\u7528</span>'
                    : '<span class="status-tag fail">\u7981\u7528</span>';
            }},
            {field: "createdAt", title: "\u521b\u5efa\u65f6\u95f4", minWidth: 170, templet: function (d) {
                return AppUtils.formatDateTime(d.createdAt);
            }},
            {title: "\u64cd\u4f5c", width: 150, fixed: "right", templet: function () {
                var buttons = "";
                if (AppAuth.canPolicy("TENANT_HOST_UPDATE")) {
                    buttons += '<button type="button" class="layui-btn layui-btn-xs" lay-event="edit">\u7f16\u8f91</button>';
                }
                if (AppAuth.canPolicy("TENANT_HOST_DELETE")) {
                    buttons += '<button type="button" class="layui-btn layui-btn-danger layui-btn-xs" lay-event="delete">\u5220\u9664</button>';
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
            machineId: data.field.machineId || "",
            macAddress: data.field.macAddress || "",
            hostName: data.field.hostName || "",
            remark: data.field.remark || "",
            status: Number(data.field.status)
        };
        if (!payload.machineId && !payload.macAddress) {
            AppUtils.showFormError(formEl, "MachineId \u548c MAC \u81f3\u5c11\u586b\u5199\u4e00\u4e2a");
            return false;
        }

        (async function () {
            try {
                await AppRequest.request(editingId ? "/api/tenant-machines/" + editingId : "/api/tenant-machines", {
                    method: editingId ? "PUT" : "POST",
                    body: payload
                }, {
                    successMessage: "\u4fdd\u5b58\u6210\u529f",
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
    if (AppAuth.canPolicy("TENANT_HOST_AUTHORIZE")) {
        addMachineButton.addEventListener("click", function () {
            openDialog(null);
        });
    } else {
        addMachineButton.style.display = "none";
    }

    document.getElementById("resetButton").addEventListener("click", function () {
        form.val("machineSearchForm", {keyword: ""});
        AppTable.reload(table, tableId, {keyword: ""});
    });

    function openDialog(machine) {
        editingId = machine ? machine.id : null;
        var index = layer.open({
            type: 1,
            title: editingId ? "\u7f16\u8f91\u6388\u6743\u4e3b\u673a" : "\u65b0\u589e\u6388\u6743\u4e3b\u673a",
            area: ["560px", "520px"],
            content: AppUtils.getTemplateHtml("machineFormTemplate"),
            success: function (layero) {
                form.render();
                form.val("machineForm", {
                    machineId: machine ? machine.machineId || "" : "",
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
        AppDialog.confirm(layer, "\u786e\u5b9a\u5220\u9664\u8be5\u6388\u6743\u4e3b\u673a\u5417\uff1f", async function (index) {
            try {
                await AppRequest.request("/api/tenant-machines/" + machine.id, {method: "DELETE"}, {
                    successMessage: "\u5220\u9664\u6210\u529f"
                });
                layer.close(index);
                table.reload(tableId);
            } catch (error) {
                return null;
            }
            return null;
        });
    }
});
