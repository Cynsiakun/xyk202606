layui.use(["table", "form", "layer"], function () {
    var table = layui.table;
    var form = layui.form;
    var layer = layui.layer;
    var permissionTableId = "permissionTable";
    var editingPermissionId = null;

    var api = {
        page: "/api/rbac/permission/list",
        create: "/api/rbac/permission",
        update: "/api/rbac/permission/{id}",
        delete: "/api/rbac/permission/{id}"
    };

    AppTable.renderPageTable(table, {
        elem: "#" + permissionTableId,
        url: api.page,
        cols: [[
            {field: "id", title: "ID", width: 80, sort: true},
            {field: "permissionCode", title: "权限编码", minWidth: 180},
            {field: "permissionName", title: "权限名称", minWidth: 180},
            {field: "permissionType", title: "权限类型", width: 110},
            {field: "path", title: "路径", minWidth: 220, templet: function (d) { return d.path || "-"; }},
            {field: "status", title: "状态", width: 90, templet: function (d) {
                return d.status === 1
                    ? '<span class="status-tag success">启用</span>'
                    : '<span class="status-tag fail">禁用</span>';
            }},
            {title: "操作", width: 150, fixed: "right", templet: function () {
                var buttons = "";
                if (AppAuth.hasPermission("permission:update")) {
                    buttons += '<button type="button" class="layui-btn layui-btn-xs" lay-event="edit">编辑</button>';
                }
                if (AppAuth.hasPermission("permission:delete")) {
                    buttons += '<button type="button" class="layui-btn layui-btn-danger layui-btn-xs" lay-event="delete">删除</button>';
                }
                return buttons || '<span class="empty-text">-</span>';
            }}
        ]]
    });

    form.on("submit(permissionSearchSubmit)", function (data) {
        AppTable.reload(table, permissionTableId, {
            keyword: data.field.keyword || ""
        });
        return false;
    });

    form.on("submit(savePermission)", function (data) {
        var payload = normalizePayload(data.field);
        (async function () {
            try {
                if (editingPermissionId) {
                    await AppRequest.request(api.update.replace("{id}", editingPermissionId), {
                        method: "PUT",
                        body: payload
                    }, {
                        successMessage: "保存成功"
                    });
                } else {
                    await AppRequest.request(api.create, {
                        method: "POST",
                        body: payload
                    }, {
                        successMessage: "保存成功"
                    });
                }
                layer.closeAll("page");
                table.reload(permissionTableId);
            } catch (error) {
                return;
            }
        })();
        return false;
    });

    table.on("tool(permissionTable)", function (obj) {
        if (obj.event === "edit") {
            openPermissionDialog(obj.data);
        }
        if (obj.event === "delete") {
            confirmDelete(obj.data);
        }
    });

    var addPermissionButton = document.getElementById("addPermissionButton");
    if (AppAuth.hasPermission("permission:create")) {
        addPermissionButton.addEventListener("click", function () {
            openPermissionDialog(null);
        });
    } else {
        addPermissionButton.style.display = "none";
    }

    document.getElementById("resetButton").addEventListener("click", function () {
        form.val("permissionSearchForm", {keyword: ""});
        AppTable.reload(table, permissionTableId, {keyword: ""});
    });

    function openPermissionDialog(permission) {
        editingPermissionId = permission ? permission.id : null;
        var index = layer.open({
            type: 1,
            title: editingPermissionId ? "编辑权限" : "新增权限",
            area: ["620px", "520px"],
            content: AppUtils.getTemplateHtml("permissionFormTemplate"),
            success: function (layero) {
                form.render();
                form.val("permissionForm", {
                    permissionCode: permission ? permission.permissionCode : "",
                    permissionName: permission ? permission.permissionName : "",
                    permissionType: permission ? permission.permissionType : "API",
                    path: permission ? permission.path || "" : "",
                    status: permission && permission.status === 0 ? "0" : "1"
                });
                layero.find('[data-action="close"]').on("click", function () {
                    layer.close(index);
                });
            },
            end: function () {
                editingPermissionId = null;
            }
        });
    }

    function confirmDelete(permission) {
        AppDialog.confirm(layer, "确定删除权限“" + permission.permissionName + "”吗？", async function (index) {
            try {
                await AppRequest.request(api.delete.replace("{id}", permission.id), {
                    method: "DELETE"
                }, {
                    successMessage: "删除成功"
                });
                layer.close(index);
                table.reload(permissionTableId);
            } catch (error) {
                return;
            }
        });
    }

    function normalizePayload(field) {
        return {
            permissionCode: field.permissionCode,
            permissionName: field.permissionName,
            permissionType: field.permissionType,
            path: field.path || "",
            status: Number(field.status)
        };
    }
});
