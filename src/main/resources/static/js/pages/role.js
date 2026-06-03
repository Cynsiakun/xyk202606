layui.use(["table", "form", "layer", "tree"], function () {
    var table = layui.table;
    var form = layui.form;
    var layer = layui.layer;
    var tree = layui.tree;
    var roleTableId = "roleTable";
    var editingRoleId = null;
    var permissionAssignRoleId = null;
    var permissionTreeData = [];

    var api = {
        page: "/api/rbac/role/list",
        create: "/api/rbac/role",
        update: "/api/rbac/role/{id}",
        delete: "/api/rbac/role/{id}",
        permissions: "/api/rbac/permission/all",
        assignPermissions: "/api/rbac/role/{id}/permissions"
    };

    AppTable.renderPageTable(table, {
        elem: "#" + roleTableId,
        url: api.page,
        cols: [[
            {field: "id", title: "ID", width: 80, sort: true},
            {field: "roleCode", title: "角色编码", minWidth: 160},
            {field: "roleName", title: "角色名称", minWidth: 160},
            {field: "status", title: "状态", width: 90, templet: function (d) {
                return d.status === 1
                    ? '<span class="status-tag success">启用</span>'
                    : '<span class="status-tag fail">禁用</span>';
            }},
            {field: "createAt", title: "创建时间", minWidth: 180, templet: function (d) { return AppUtils.formatDateTime(d.createAt); }},
            {title: "操作", width: 250, fixed: "right", templet: function () {
                var buttons = "";
                if (AppAuth.hasPermission("role:update")) {
                    buttons += '<button type="button" class="layui-btn layui-btn-xs" lay-event="edit">编辑</button>';
                }
                if (AppAuth.hasPermission("role:permission:assign")) {
                    buttons += '<button type="button" class="layui-btn layui-btn-normal layui-btn-xs" lay-event="assignPermission">分配权限</button>';
                }
                if (AppAuth.hasPermission("role:delete")) {
                    buttons += '<button type="button" class="layui-btn layui-btn-danger layui-btn-xs" lay-event="delete">删除</button>';
                }
                return buttons || '<span class="empty-text">-</span>';
            }}
        ]]
    });

    form.on("submit(roleSearchSubmit)", function (data) {
        AppTable.reload(table, roleTableId, {
            keyword: data.field.keyword || ""
        });
        return false;
    });

    form.on("submit(saveRole)", function (data) {
        var payload = normalizeRolePayload(data.field);
        (async function () {
            try {
                if (editingRoleId) {
                    await AppRequest.request(api.update.replace("{id}", editingRoleId), {
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
                table.reload(roleTableId);
            } catch (error) {
                return;
            }
        })();
        return false;
    });

    table.on("tool(roleTable)", function (obj) {
        if (obj.event === "edit") {
            openRoleDialog(obj.data);
        }
        if (obj.event === "assignPermission") {
            openPermissionDialog(obj.data);
        }
        if (obj.event === "delete") {
            confirmDelete(obj.data);
        }
    });

    var addRoleButton = document.getElementById("addRoleButton");
    if (AppAuth.hasPermission("role:create")) {
        addRoleButton.addEventListener("click", function () {
            openRoleDialog(null);
        });
    } else {
        addRoleButton.style.display = "none";
    }

    document.getElementById("resetButton").addEventListener("click", function () {
        form.val("roleSearchForm", {keyword: ""});
        AppTable.reload(table, roleTableId, {keyword: ""});
    });

    function openRoleDialog(role) {
        editingRoleId = role ? role.id : null;
        var dialogHeight = Math.min(420, (window.innerHeight || 420) - 30);
        var dialogWidth = Math.min(560, (window.innerWidth || 560) - 30);
        var index = layer.open({
            type: 1,
            title: editingRoleId ? "编辑角色" : "新增角色",
            area: [dialogWidth + "px", dialogHeight + "px"],
            content: AppUtils.getTemplateHtml("roleFormTemplate"),
            success: function (layero) {
                form.render();
                form.val("roleForm", {
                    roleCode: role ? role.roleCode : "",
                    roleName: role ? role.roleName : "",
                    status: role && role.status === 0 ? "0" : "1"
                });
                layero.find('[data-action="close"]').on("click", function () {
                    layer.close(index);
                });
            },
            end: function () {
                editingRoleId = null;
            }
        });
    }

    async function openPermissionDialog(role) {
        permissionAssignRoleId = role.id;
        try {
            var result = await AppRequest.request(api.permissions, {method: "GET"});
            var permissions = result.data || [];
            var checkedIds = role.permissionIds || [];
            permissionTreeData = buildPermissionTreeData(permissions, checkedIds);

            var dialogHeight = Math.min(560, (window.innerHeight || 560) - 30);
            var dialogWidth = Math.min(680, (window.innerWidth || 680) - 30);
            var index = layer.open({
                type: 1,
                title: "分配权限 - " + role.roleName,
                area: [dialogWidth + "px", dialogHeight + "px"],
                content: AppUtils.getTemplateHtml("rolePermissionTemplate"),
                success: function (layero, layerIndex) {
                    renderPermissionTree(permissionTreeData);
                    bindPermissionToolbar(layero, layerIndex);
                },
                end: function () {
                    permissionAssignRoleId = null;
                    permissionTreeData = [];
                }
            });
        } catch (error) {
            permissionAssignRoleId = null;
        }
    }

    function bindPermissionToolbar(layero, index) {
        var container = layero[0];
        var checkAllButton = container.querySelector("#checkAllPermissions");
        var uncheckAllButton = container.querySelector("#uncheckAllPermissions");
        var saveButton = container.querySelector("#saveRolePermissionButton");
        var closeButtons = container.querySelectorAll('[data-action="close"]');

        checkAllButton.addEventListener("click", function () {
            setTreeChecked(permissionTreeData, true);
            renderPermissionTree(permissionTreeData);
        });

        uncheckAllButton.addEventListener("click", function () {
            setTreeChecked(permissionTreeData, false);
            renderPermissionTree(permissionTreeData);
        });

        saveButton.addEventListener("click", async function () {
            try {
                await AppRequest.request(api.assignPermissions.replace("{id}", permissionAssignRoleId), {
                    method: "POST",
                    body: {
                        permissionIds: collectCheckedPermissionIds()
                    }
                }, {
                    successMessage: "权限分配成功"
                });
                layer.close(index);
                table.reload(roleTableId);
            } catch (error) {
                return;
            }
        });

        closeButtons.forEach(function (button) {
            button.addEventListener("click", function () {
                layer.close(index);
            });
        });
    }

    function renderPermissionTree(treeData) {
        tree.render({
            elem: "#permissionTree",
            id: "permissionTreeId",
            data: treeData,
            showCheckbox: true,
            onlyIconControl: false
        });
    }

    function confirmDelete(role) {
        AppDialog.confirm(layer, "确定删除角色“" + role.roleName + "”吗？", async function (index) {
            try {
                await AppRequest.request(api.delete.replace("{id}", role.id), {
                    method: "DELETE"
                }, {
                    successMessage: "删除成功"
                });
                layer.close(index);
                table.reload(roleTableId);
            } catch (error) {
                return;
            }
        });
    }

    function normalizeRolePayload(field) {
        return {
            roleCode: field.roleCode,
            roleName: field.roleName,
            status: Number(field.status)
        };
    }

    function buildPermissionTreeData(permissionList, checkedIds) {
        var checkedMap = {};
        checkedIds.forEach(function (id) {
            checkedMap[id] = true;
        });

        var grouped = {};
        permissionList.forEach(function (item) {
            var type = item.permissionType || "OTHER";
            if (!grouped[type]) {
                grouped[type] = [];
            }
            grouped[type].push({
                id: item.id,
                title: item.permissionName + " (" + item.permissionCode + ")",
                checked: !!checkedMap[item.id]
            });
        });

        var typeNameMap = {
            API: "接口权限",
            MENU: "菜单权限",
            BUTTON: "按钮权限",
            OTHER: "其他权限"
        };

        return Object.keys(grouped).map(function (type, index) {
            return {
                id: "group_" + type + "_" + index,
                title: typeNameMap[type] || type,
                spread: true,
                children: grouped[type]
            };
        });
    }

    function setTreeChecked(treeData, checked) {
        treeData.forEach(function (node) {
            node.checked = checked;
            if (node.children && node.children.length) {
                node.children.forEach(function (child) {
                    child.checked = checked;
                });
            }
        });
    }

    function collectCheckedPermissionIds() {
        var ids = [];
        var checkedNodes = tree.getChecked("permissionTreeId") || [];
        (function walk(nodes) {
            nodes.forEach(function (node) {
                if (node.children && node.children.length) {
                    walk(node.children);
                    return;
                }
                var numericId = Number(node.id);
                if (!Number.isNaN(numericId)) {
                    ids.push(numericId);
                }
            });
        })(checkedNodes);
        return ids;
    }
});
