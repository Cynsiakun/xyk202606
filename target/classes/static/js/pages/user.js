layui.use(["table", "form", "layer"], function () {
    var table = layui.table;
    var form = layui.form;
    var layer = layui.layer;
    var editingUserId = null;
    var roleAssignUserId = null;
    var userTableId = "userTable";
    var importFile = null;
    var roleApi = {
        allRoles: "/api/rbac/role/all",
        userRoles: "/api/rbac/user/{userId}/roles",
        assignUserRoles: "/api/rbac/user/{userId}/roles"
    };

    AppTable.renderPageTable(table, {
        elem: "#" + userTableId,
        url: "/api/user/list",
        cols: [[
            {field: "id", title: "ID", width: 80, sort: true},
            {field: "userName", title: "用户名", minWidth: 140},
            {field: "userAvatar", title: "头像", width: 90, templet: function (d) {
                return d.userAvatar
                    ? '<img class="table-avatar" src="' + d.userAvatar + '" alt="头像">'
                    : '<span class="empty-text">-</span>';
            }},
            {field: "userPhone", title: "手机号", minWidth: 140, templet: function (d) { return d.userPhone || "-"; }},
            {field: "userEmail", title: "邮箱", minWidth: 180, templet: function (d) { return d.userEmail || "-"; }},
            {field: "roles", title: "角色", minWidth: 160, templet: function (d) {
                if (!d.roles || !d.roles.length) {
                    return '<span class="empty-text">-</span>';
                }
                return d.roles.map(function (roleName) {
                    return '<span class="status-tag info">' + roleName + "</span>";
                }).join(" ");
            }},
            {field: "status", title: "状态", width: 90, templet: function (d) {
                return d.status === 1
                    ? '<span class="status-tag success">启用</span>'
                    : '<span class="status-tag fail">禁用</span>';
            }},
            {field: "lastLoginTime", title: "最后登录时间", minWidth: 180, templet: function (d) { return AppUtils.formatDateTime(d.lastLoginTime); }},
            {title: "操作", width: 230, fixed: "right", templet: function () {
                var buttons = "";
                if (AppAuth.hasPermission("user:update")) {
                    buttons += '<button type="button" class="layui-btn layui-btn-xs" lay-event="edit">编辑</button>';
                }
                if (AppAuth.hasPermission("user:role:assign")) {
                    buttons += '<button type="button" class="layui-btn layui-btn-normal layui-btn-xs" lay-event="assignRole">分配角色</button>';
                }
                if (AppAuth.hasPermission("user:delete")) {
                    buttons += '<button type="button" class="layui-btn layui-btn-danger layui-btn-xs" lay-event="delete">删除</button>';
                }
                return buttons || '<span class="empty-text">-</span>';
            }}
        ]]
    });

    form.on("submit(userSearchSubmit)", function (data) {
        AppTable.reload(table, userTableId, {
            userName: data.field.userName || ""
        });
        return false;
    });

    form.on("submit(saveUser)", function (data) {
        var formEl = document.querySelector('form[lay-filter="userForm"]');
        AppUtils.clearFormError(formEl);
        var payload = normalizePayload(data.field);
        if (!editingUserId && !payload.userPwd) {
            AppUtils.showFormError(formEl, "新增用户时密码必填");
            return false;
        }
        (async function () {
            try {
                if (editingUserId) {
                    delete payload.userPwd;
                    await AppRequest.request("/api/user/" + editingUserId, {
                        method: "PUT",
                        body: payload
                    }, {
                        successMessage: "保存成功",
                        showErrorMessage: false
                    });
                } else {
                    await AppRequest.request("/api/user", {
                        method: "POST",
                        body: payload
                    }, {
                        successMessage: "保存成功",
                        showErrorMessage: false
                    });
                }
                layer.closeAll("page");
                table.reload(userTableId);
            } catch (error) {
                AppUtils.showFormError(formEl, error.message);
            }
        })();
        return false;
    });

    form.on("submit(saveUserRoles)", function () {
        var roleIds = [];
        document.querySelectorAll('#userRoleCheckboxGroup input[type="checkbox"]').forEach(function (checkbox) {
            if (checkbox.checked) {
                roleIds.push(Number(checkbox.value));
            }
        });

        (async function () {
            try {
                await AppRequest.request(roleApi.assignUserRoles.replace("{userId}", roleAssignUserId), {
                    method: "POST",
                    body: {
                        roleIds: roleIds
                    }
                }, {
                    successMessage: "角色分配成功"
                });
                layer.closeAll("page");
                table.reload(userTableId);
            } catch (error) {
                return;
            }
        })();
        return false;
    });

    table.on("tool(userTable)", function (obj) {
        if (obj.event === "edit") {
            openUserDialog(obj.data);
        }
        if (obj.event === "assignRole") {
            openRoleAssignDialog(obj.data);
        }
        if (obj.event === "delete") {
            confirmDelete(obj.data);
        }
    });

    bindToolbar();

    function bindToolbar() {
        var addUserButton = document.getElementById("addUserButton");
        if (AppAuth.hasPermission("user:create")) {
            addUserButton.addEventListener("click", function () {
                openUserDialog(null);
            });
        } else {
            addUserButton.style.display = "none";
        }

        var importButton = document.getElementById("importButton");
        if (AppAuth.hasPermission("user:create")) {
            importButton.addEventListener("click", openImportDialog);
        } else {
            importButton.style.display = "none";
        }

        document.getElementById("resetButton").addEventListener("click", function () {
            form.val("userSearchForm", {userName: ""});
            AppTable.reload(table, userTableId, {userName: ""});
        });
    }

    function openUserDialog(user) {
        editingUserId = user ? user.id : null;
        var index = layer.open({
            type: 1,
            title: editingUserId ? "编辑用户" : "新增用户",
            area: ["560px", "520px"],
            content: AppUtils.getTemplateHtml("userFormTemplate"),
            success: function (layero) {
                var passwordItem = layero.find("#passwordItem");
                if (editingUserId) {
                    passwordItem.addClass("user-form-password-hidden");
                } else {
                    layero.find('input[name="userPwd"]').attr("required", true).attr("lay-verify", "required");
                }
                form.render();
                form.val("userForm", {
                    userName: user ? user.userName : "",
                    userPwd: "",
                    userPhone: user ? user.userPhone || "" : "",
                    userEmail: user ? user.userEmail || "" : "",
                    userAvatar: user ? user.userAvatar || "" : "",
                    status: user && user.status === 0 ? "0" : "1"
                });
                AppUtils.bindLiveValidation(layero[0], {
                    saveButton: 'button[lay-filter="saveUser"]',
                    fields: [
                        {selector: 'input[name="userPhone"]', type: "phone", message: "手机号格式不正确"},
                        {selector: 'input[name="userEmail"]', type: "email", message: "邮箱格式不正确"}
                    ]
                });
                layero.find('[data-action="close"]').on("click", function () {
                    layer.close(index);
                });
            },
            end: function () {
                editingUserId = null;
            }
        });
    }

    function openImportDialog() {
        importFile = null;
        var index = layer.open({
            type: 1,
            title: "导入用户 CSV",
            area: ["560px", "380px"],
            content: AppUtils.getTemplateHtml("userImportTemplate"),
            success: function (layero) {
                var fileInput = layero[0].querySelector("#userCsvFile");
                var fileName = layero[0].querySelector("#userCsvFileName");
                var submitButton = layero[0].querySelector("#submitImportButton");
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
            var result = await AppRequest.request("/api/user/import", {
                method: "POST",
                body: formData
            }, {
                successMessage: "导入完成"
            });
            layer.close(dialogIndex);
            table.reload(userTableId);
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

    function confirmDelete(user) {
        AppDialog.confirm(layer, '确定删除用户 "' + user.userName + '" 吗？', async function (index) {
            try {
                await AppRequest.request("/api/user/" + user.id, {
                    method: "DELETE"
                }, {
                    successMessage: "删除成功"
                });
                layer.close(index);
                table.reload(userTableId);
            } catch (error) {
                return;
            }
        });
    }

    async function openRoleAssignDialog(user) {
        roleAssignUserId = user.id;
        try {
            var roleResult = await AppRequest.request(roleApi.allRoles, {method: "GET"}, {showErrorMessage: true});
            var selectedRoleResult = await AppRequest.request(
                roleApi.userRoles.replace("{userId}", user.id),
                {method: "GET"},
                {showErrorMessage: true}
            );

            var roleList = roleResult.data || [];
            var selectedRoleIds = selectedRoleResult.data || [];
            var index = layer.open({
                type: 1,
                title: "分配角色",
                area: ["560px", "480px"],
                content: AppUtils.getTemplateHtml("userRoleTemplate"),
                success: function (layero) {
                    var checkboxGroup = layero.find("#userRoleCheckboxGroup");
                    checkboxGroup.html(buildRoleCheckboxHtml(roleList, selectedRoleIds));
                    form.render("checkbox");
                    form.val("userRoleForm", {
                        userName: user.userName
                    });
                    layero.find('[data-action="close"]').on("click", function () {
                        layer.close(index);
                    });
                },
                end: function () {
                    roleAssignUserId = null;
                }
            });
        } catch (error) {
            roleAssignUserId = null;
        }
    }

    function normalizePayload(field) {
        return {
            userName: field.userName,
            userPwd: field.userPwd,
            userPhone: field.userPhone || "",
            userEmail: field.userEmail || "",
            userAvatar: field.userAvatar || "",
            status: Number(field.status)
        };
    }

    function buildRoleCheckboxHtml(roleList, selectedRoleIds) {
        var selectedMap = {};
        selectedRoleIds.forEach(function (roleId) {
            selectedMap[roleId] = true;
        });

        return roleList.map(function (role) {
            var checked = selectedMap[role.id] ? "checked" : "";
            return '<input type="checkbox" name="role_' + role.id + '" title="' + role.roleName + " (" + role.roleCode + ')" value="' + role.id + '" ' + checked + ">";
        }).join("");
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
