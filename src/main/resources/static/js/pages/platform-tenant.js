layui.use(["table", "form", "layer", "laydate"], function () {
    var table = layui.table;
    var form = layui.form;
    var layer = layui.layer;
    var laydate = layui.laydate;
    var tenantTableId = "tenantTable";
    var currentTenant = null;
    var licensePlans = [];
    var offlinePayloadText = "";

    var API = {
        tenantList: "/api/platform/tenant/list",
        tenantCreate: "/api/platform/tenant",
        tenantStatus: "/api/platform/tenant/{id}/status",
        tenantLicenses: "/api/platform/tenant/{id}/licenses",
        generateLicense: "/api/platform/tenant/{id}/licenses",
        licensePlans: "/api/platform/license/plans",
        offlineLicense: "/api/platform/license/offline"
    };

    init();

    async function init() {
        await loadLicensePlans();
        renderTenantTable();
        bindEvents();
    }

    function renderTenantTable() {
        AppTable.renderPageTable(table, {
            elem: "#" + tenantTableId,
            url: API.tenantList,
            cols: [[
                {field: "id", title: "ID", width: 80, sort: true},
                {field: "name", title: "租户名", minWidth: 160},
                {field: "licenseEdition", title: "套餐", width: 130, templet: function (d) { return editionTpl(d.licenseEdition); }},
                {field: "contact", title: "联系人", minWidth: 150, templet: function (d) { return d.contact || "-"; }},
                {field: "status", title: "状态", width: 90, templet: statusTpl},
                {field: "createdAt", title: "创建时间", minWidth: 170, templet: function (d) { return AppUtils.formatDateTime(d.createdAt); }},
                {title: "操作", width: 280, fixed: "right", templet: function (d) {
                    var toggleText = d.status === 1 ? "禁用" : "启用";
                    var toggleClass = d.status === 1 ? "layui-btn-danger" : "layui-btn-normal";
                    return '<div class="tenant-action-group">'
                        + '<button type="button" class="layui-btn layui-btn-xs" lay-event="license">授权</button>'
                        + '<button type="button" class="layui-btn layui-btn-primary layui-btn-xs" lay-event="detail">详情</button>'
                        + '<button type="button" class="layui-btn ' + toggleClass + ' layui-btn-xs" lay-event="toggle">' + toggleText + "</button>"
                        + "</div>";
                }}
            ]]
        });
    }

    function bindEvents() {
        form.on("submit(tenantSearchSubmit)", function (data) {
            AppTable.reload(table, tenantTableId, {
                keyword: data.field.keyword || "",
                status: data.field.status || ""
            });
            return false;
        });

        form.on("submit(saveTenant)", function (data) {
            var formEl = document.querySelector('form[lay-filter="tenantForm"]');
            AppUtils.clearFormError(formEl);
            (async function () {
                try {
                    await AppRequest.request(API.tenantCreate, {
                        method: "POST",
                        body: {
                            name: data.field.name,
                            contact: data.field.contact || "",
                            status: Number(data.field.status),
                            adminUserName: data.field.adminUserName,
                            adminPassword: data.field.adminPassword,
                            adminPhone: data.field.adminPhone || "",
                            adminEmail: data.field.adminEmail || ""
                        }
                    }, {
                        successMessage: "租户开通成功",
                        showErrorMessage: false
                    });
                    layer.closeAll("page");
                    table.reload(tenantTableId);
                } catch (error) {
                    AppUtils.showFormError(formEl, error.message);
                }
            })();
            return false;
        });

        form.on("submit(saveLicense)", function (data) {
            var formEl = document.querySelector('form[lay-filter="licenseForm"]');
            AppUtils.clearFormError(formEl);
            (async function () {
                try {
                    await AppRequest.request(apiFor(API.generateLicense, currentTenant.id), {
                        method: "POST",
                        body: normalizeLicensePayload(data.field)
                    }, {
                        successMessage: "套餐授权已生成",
                        showErrorMessage: false
                    });
                    layer.closeAll("page");
                    openLicensePanel(currentTenant);
                    table.reload(tenantTableId);
                } catch (error) {
                    AppUtils.showFormError(formEl, error.message);
                }
            })();
            return false;
        });

        form.on("submit(generateOffline)", function (data) {
            var formEl = document.querySelector('form[lay-filter="offlineForm"]');
            AppUtils.clearFormError(formEl);
            (async function () {
                try {
                    var result = await AppRequest.request(API.offlineLicense, {
                        method: "POST",
                        body: {
                            licenseKey: data.field.licenseKey,
                            machineId: data.field.machineId
                        }
                    }, {
                        successMessage: "离线 License 已生成",
                        showErrorMessage: false
                    });
                    renderOfflineResult(result.data);
                    loadLicenses(currentTenant);
                } catch (error) {
                    AppUtils.showFormError(formEl, error.message);
                }
            })();
            return false;
        });

        table.on("tool(tenantTable)", function (obj) {
            if (obj.event === "detail") {
                openTenantDetail(obj.data);
            }
            if (obj.event === "license") {
                openLicensePanel(obj.data);
            }
            if (obj.event === "toggle") {
                toggleTenantStatus(obj.data);
            }
        });

        table.on("tool(licenseTable)", function (obj) {
            if (obj.event === "offline") {
                openOfflineDialog(obj.data.licenseKey || "");
            }
        });

        document.getElementById("addTenantButton").addEventListener("click", openTenantDialog);
        document.getElementById("resetButton").addEventListener("click", function () {
            form.val("tenantSearchForm", {keyword: "", status: ""});
            AppTable.reload(table, tenantTableId, {keyword: "", status: ""});
        });
    }

    async function loadLicensePlans() {
        try {
            var result = await AppRequest.request(API.licensePlans, {method: "GET"}, {showErrorMessage: false});
            licensePlans = result.data || [];
        } catch (error) {
            licensePlans = [];
        }
    }

    function openTenantDialog() {
        var index = layer.open({
            type: 1,
            title: "开通租户",
            area: ["560px", "620px"],
            content: AppUtils.getTemplateHtml("tenantFormTemplate"),
            success: function (layero) {
                form.render();
                form.val("tenantForm", {
                    name: "",
                    contact: "",
                    status: "1",
                    adminUserName: "",
                    adminPassword: "",
                    adminPhone: "",
                    adminEmail: ""
                });
                layero.find('[data-action="close"]').on("click", function () {
                    layer.close(index);
                });
            }
        });
    }

    function openTenantDetail(tenant) {
        var html = '<div class="tenant-detail profile-grid">'
            + detailRow("ID", tenant.id)
            + detailRow("租户名", escapeHtml(tenant.name))
            + detailRow("套餐", editionTpl(tenant.licenseEdition))
            + detailRow("联系人", escapeHtml(tenant.contact || "-"))
            + detailRow("状态", tenant.status === 1 ? "启用" : "禁用")
            + detailRow("创建时间", AppUtils.formatDateTime(tenant.createdAt))
            + "</div>";
        layer.open({
            type: 1,
            title: "租户详情",
            area: ["560px", "420px"],
            content: html
        });
    }

    function openLicensePanel(tenant) {
        currentTenant = tenant;
        layer.open({
            type: 1,
            title: "租户授权",
            area: ["980px", "620px"],
            content: AppUtils.getTemplateHtml("licensePanelTemplate"),
            success: function (layero) {
                layero.find("#licenseTenantName").text(tenant.name || "-");
                layero.find("#licenseTenantMeta").text("Tenant ID: " + tenant.id + " / " + (tenant.contact || "-"));
                layero.find("#generateLicenseButton").on("click", function () {
                    openLicenseDialog(tenant);
                });
                layero.find("#offlineLicenseButton").on("click", function () {
                    openOfflineDialog("");
                });
                loadLicenses(tenant);
            },
            end: function () {
                if (currentTenant && currentTenant.id === tenant.id) {
                    currentTenant = null;
                }
            }
        });
    }

    async function loadLicenses(tenant) {
        try {
            var result = await AppRequest.request(apiFor(API.tenantLicenses, tenant.id), {method: "GET"});
            table.render({
                elem: "#licenseTable",
                data: result.data || [],
                page: false,
                cols: [[
                    {field: "licenseKey", title: "LicenseKey", minWidth: 230},
                    {field: "edition", title: "套餐", width: 130},
                    {field: "hostLimit", title: "主机上限", width: 100, templet: function (d) { return formatLimit(d.hostLimit); }},
                    {field: "userLimit", title: "用户上限", width: 100, templet: function (d) { return formatLimit(d.userLimit); }},
                    {field: "expireTime", title: "到期时间", minWidth: 170, templet: function (d) { return AppUtils.formatDateTime(d.expireTime); }},
                    {field: "machineId", title: "MachineId", minWidth: 150, templet: function (d) { return d.machineId || "-"; }},
                    {field: "status", title: "状态", width: 90, templet: statusTpl},
                    {title: "操作", width: 120, templet: function () {
                        return '<button type="button" class="layui-btn layui-btn-primary layui-btn-xs" lay-event="offline">离线文件</button>';
                    }}
                ]],
                text: {none: "当前租户尚未开通套餐授权"}
            });
        } catch (error) {
            return null;
        }
        return null;
    }

    function openLicenseDialog(tenant) {
        currentTenant = tenant;
        var index = layer.open({
            type: 1,
            title: "开通 / 调整套餐",
            area: ["560px", "500px"],
            content: AppUtils.getTemplateHtml("licenseFormTemplate"),
            success: function (layero) {
                renderPlanOptions(layero[0]);
                form.render();
                form.val("licenseForm", {
                    planCode: licensePlans[0] ? licensePlans[0].code : "",
                    hostLimit: "",
                    userLimit: "",
                    expireTime: "",
                    status: "1"
                });
                laydate.render({
                    elem: "#licenseExpireTime",
                    type: "datetime",
                    format: "yyyy-MM-dd HH:mm:ss"
                });
                layero.find('[data-action="close"]').on("click", function () {
                    layer.close(index);
                });
            }
        });
    }

    function renderPlanOptions(container) {
        var select = container.querySelector("#licensePlanSelect");
        var html = ['<option value="">请选择套餐</option>'];
        licensePlans.forEach(function (plan) {
            html.push('<option value="' + escapeAttr(plan.code) + '">' + escapeHtml(plan.name || plan.code)
                + " (" + plan.userLimit + "U/" + plan.hostLimit + "H)</option>");
        });
        select.innerHTML = html.join("");
    }

    function openOfflineDialog(licenseKey) {
        offlinePayloadText = "";
        var index = layer.open({
            type: 1,
            title: "生成离线授权文件",
            area: ["620px", "600px"],
            content: AppUtils.getTemplateHtml("offlineFormTemplate"),
            success: function (layero) {
                form.render();
                form.val("offlineForm", {
                    licenseKey: licenseKey || "",
                    machineId: ""
                });
                layero.find('[data-action="close"]').on("click", function () {
                    layer.close(index);
                });
                layero.find("#copyOfflineButton").on("click", copyOfflineJson);
                layero.find("#downloadOfflineButton").on("click", downloadOfflineJson);
            }
        });
    }

    async function toggleTenantStatus(tenant) {
        if (tenant.id === 0 && tenant.status === 1) {
            AppRequest.showMessage("平台租户不能禁用", 2);
            return;
        }
        var nextStatus = tenant.status === 1 ? 0 : 1;
        var text = nextStatus === 1 ? "启用" : "禁用";
        AppDialog.confirm(layer, "确定" + text + "租户 " + tenant.name + " 吗？", async function (index) {
            try {
                await AppRequest.request(apiFor(API.tenantStatus, tenant.id), {
                    method: "PUT",
                    body: {status: nextStatus}
                }, {
                    successMessage: text + "成功"
                });
                layer.close(index);
                table.reload(tenantTableId);
            } catch (error) {
                return null;
            }
            return null;
        });
    }

    function normalizeLicensePayload(field) {
        return {
            planCode: field.planCode,
            hostLimit: field.hostLimit === "" ? null : Number(field.hostLimit),
            userLimit: field.userLimit === "" ? null : Number(field.userLimit),
            expireTime: field.expireTime ? field.expireTime.replace(" ", "T") : field.expireTime,
            status: Number(field.status)
        };
    }

    function renderOfflineResult(data) {
        offlinePayloadText = JSON.stringify(data || {}, null, 2);
        var resultEl = document.getElementById("offlineResult");
        var textarea = document.getElementById("offlineJson");
        if (resultEl && textarea) {
            resultEl.hidden = false;
            textarea.value = offlinePayloadText;
        }
    }

    async function copyOfflineJson() {
        if (!offlinePayloadText) {
            return;
        }
        try {
            await navigator.clipboard.writeText(offlinePayloadText);
            AppRequest.showMessage("已复制", 1);
        } catch (error) {
            AppRequest.showMessage("复制失败", 2);
        }
    }

    function downloadOfflineJson() {
        if (!offlinePayloadText) {
            return;
        }
        var blob = new Blob([offlinePayloadText], {type: "application/json;charset=utf-8"});
        var url = URL.createObjectURL(blob);
        var link = document.createElement("a");
        link.href = url;
        link.download = "license.dat";
        document.body.appendChild(link);
        link.click();
        link.remove();
        URL.revokeObjectURL(url);
    }

    function statusTpl(d) {
        return d.status === 1
            ? '<span class="status-tag success">启用</span>'
            : '<span class="status-tag fail">禁用</span>';
    }

    function editionTpl(edition) {
        var labels = {
            TRIAL: "Trial",
            STANDARD: "Standard",
            PROFESSIONAL: "Professional",
            NONE: "未授权"
        };
        return labels[edition] || edition || "-";
    }

    function apiFor(pattern, id) {
        return pattern.replace("{id}", encodeURIComponent(id));
    }

    function formatLimit(value) {
        return Number(value || 0) === 0 ? "不限" : value;
    }

    function detailRow(label, value) {
        return '<div class="profile-item"><span class="profile-item-label">'
            + label + '</span><div class="profile-item-value">' + value + "</div></div>";
    }

    function escapeHtml(text) {
        return String(text == null ? "" : text)
            .replace(/&/g, "&amp;")
            .replace(/</g, "&lt;")
            .replace(/>/g, "&gt;")
            .replace(/"/g, "&quot;")
            .replace(/'/g, "&#39;");
    }

    function escapeAttr(text) {
        return escapeHtml(text).replace(/`/g, "&#96;");
    }
});
