layui.use(["table", "form", "layer", "laydate"], function () {
    var table = layui.table;
    var form = layui.form;
    var layer = layui.layer;
    var laydate = layui.laydate;
    var tenantTableId = "tenantTable";
    var currentTenant = null;
    var offlinePayloadText = "";

    var API = {
        tenantList: "/api/platform/tenant/list",
        tenantCreate: "/api/platform/tenant",
        tenantStatus: "/api/platform/tenant/{id}/status",
        tenantLicenses: "/api/platform/tenant/{id}/licenses",
        generateLicense: "/api/platform/tenant/{id}/licenses",
        offlineLicense: "/api/platform/license/offline"
    };

    AppTable.renderPageTable(table, {
        elem: "#" + tenantTableId,
        url: API.tenantList,
        cols: [[
            {field: "id", title: "ID", width: 80, sort: true},
            {field: "name", title: "\u79df\u6237\u540d", minWidth: 160},
            {field: "contact", title: "\u8054\u7cfb\u4eba", minWidth: 160, templet: function (d) { return d.contact || "-"; }},
            {field: "status", title: "\u72b6\u6001", width: 100, templet: function (d) {
                return d.status === 1
                    ? '<span class="status-tag success">\u542f\u7528</span>'
                    : '<span class="status-tag fail">\u7981\u7528</span>';
            }},
            {field: "createdAt", title: "\u521b\u5efa\u65f6\u95f4", minWidth: 180, templet: function (d) {
                return AppUtils.formatDateTime(d.createdAt);
            }},
            {title: "\u64cd\u4f5c", width: 280, fixed: "right", templet: function (d) {
                var toggleText = d.status === 1 ? "\u7981\u7528" : "\u542f\u7528";
                var toggleClass = d.status === 1 ? "layui-btn-danger" : "layui-btn-normal";
                return '<div class="tenant-action-group">'
                    + '<button type="button" class="layui-btn layui-btn-xs" lay-event="license">\u6388\u6743</button>'
                    + '<button type="button" class="layui-btn layui-btn-primary layui-btn-xs" lay-event="detail">\u8be6\u60c5</button>'
                    + '<button type="button" class="layui-btn ' + toggleClass + ' layui-btn-xs" lay-event="toggle">' + toggleText + "</button>"
                    + "</div>";
            }}
        ]]
    });

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
                        status: Number(data.field.status)
                    }
                }, {
                    successMessage: "\u79df\u6237\u521b\u5efa\u6210\u529f",
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
        var payload = normalizeLicensePayload(data.field);
        (async function () {
            try {
                await AppRequest.request(apiFor(API.generateLicense, currentTenant.id), {
                    method: "POST",
                    body: payload
                }, {
                    successMessage: "License \u751f\u6210\u6210\u529f",
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
                    successMessage: "\u79bb\u7ebf License \u5df2\u751f\u6210",
                    showErrorMessage: false
                });
                renderOfflineResult(result.data, data.field.licenseKey);
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

    bindToolbar();

    function bindToolbar() {
        document.getElementById("addTenantButton").addEventListener("click", openTenantDialog);
        document.getElementById("resetButton").addEventListener("click", function () {
            form.val("tenantSearchForm", {
                keyword: "",
                status: ""
            });
            AppTable.reload(table, tenantTableId, {
                keyword: "",
                status: ""
            });
        });
    }

    function openTenantDialog() {
        var index = layer.open({
            type: 1,
            title: "\u65b0\u5efa\u79df\u6237",
            area: ["520px", "360px"],
            content: AppUtils.getTemplateHtml("tenantFormTemplate"),
            success: function (layero) {
                form.render();
                form.val("tenantForm", {
                    name: "",
                    contact: "",
                    status: "1"
                });
                layero.find('[data-action="close"]').on("click", function () {
                    layer.close(index);
                });
            }
        });
    }

    function openTenantDetail(tenant) {
        var html = '<div class="tenant-detail">'
            + detailRow("ID", tenant.id)
            + detailRow("\u79df\u6237\u540d", escapeHtml(tenant.name))
            + detailRow("\u8054\u7cfb\u4eba", escapeHtml(tenant.contact || "-"))
            + detailRow("\u72b6\u6001", tenant.status === 1 ? "\u542f\u7528" : "\u7981\u7528")
            + detailRow("\u521b\u5efa\u65f6\u95f4", AppUtils.formatDateTime(tenant.createdAt))
            + "</div>";
        layer.open({
            type: 1,
            title: "\u79df\u6237\u8be6\u60c5",
            area: ["480px", "360px"],
            content: html
        });
    }

    function openLicensePanel(tenant) {
        currentTenant = tenant;
        var index = layer.open({
            type: 1,
            title: "License",
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
        return index;
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
                    {field: "edition", title: "\u7248\u672c", width: 130},
                    {field: "hostLimit", title: "\u4e3b\u673a", width: 90, templet: function (d) { return formatLimit(d.hostLimit); }},
                    {field: "userLimit", title: "\u7528\u6237", width: 90, templet: function (d) { return formatLimit(d.userLimit); }},
                    {field: "expireTime", title: "\u5230\u671f\u65f6\u95f4", minWidth: 170, templet: function (d) { return AppUtils.formatDateTime(d.expireTime); }},
                    {field: "machineId", title: "MachineId", minWidth: 150, templet: function (d) { return d.machineId || "-"; }},
                    {field: "status", title: "\u72b6\u6001", width: 90, templet: function (d) {
                        return d.status === 1
                            ? '<span class="status-tag success">\u542f\u7528</span>'
                            : '<span class="status-tag fail">\u7981\u7528</span>';
                    }},
                    {title: "\u64cd\u4f5c", width: 120, templet: function () {
                        return '<button type="button" class="layui-btn layui-btn-primary layui-btn-xs" lay-event="offline">\u79bb\u7ebf</button>';
                    }}
                ]]
            });
        } catch (error) {
            return null;
        }
        return null;
    }

    function openLicenseDialog(tenant) {
        var index = layer.open({
            type: 1,
            title: "\u751f\u6210 License",
            area: ["560px", "560px"],
            content: AppUtils.getTemplateHtml("licenseFormTemplate"),
            success: function (layero) {
                form.render();
                form.val("licenseForm", {
                    edition: "STANDARD",
                    hostLimit: "",
                    userLimit: "",
                    expireTime: "",
                    machineId: "",
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
        currentTenant = tenant;
    }

    function openOfflineDialog(licenseKey) {
        offlinePayloadText = "";
        var index = layer.open({
            type: 1,
            title: "\u751f\u6210\u79bb\u7ebf\u6388\u6743\u6587\u4ef6",
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
            AppRequest.showMessage("\u5e73\u53f0\u79df\u6237\u4e0d\u80fd\u7981\u7528", 2);
            return;
        }
        var nextStatus = tenant.status === 1 ? 0 : 1;
        var text = nextStatus === 1 ? "\u542f\u7528" : "\u7981\u7528";
        AppDialog.confirm(layer, "\u786e\u5b9a" + text + "\u79df\u6237 " + tenant.name + " \u5417\uff1f", async function (index) {
            try {
                await AppRequest.request(apiFor(API.tenantStatus, tenant.id), {
                    method: "PUT",
                    body: {status: nextStatus}
                }, {
                    successMessage: text + "\u6210\u529f"
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
            edition: field.edition,
            hostLimit: field.hostLimit === "" ? null : Number(field.hostLimit),
            userLimit: field.userLimit === "" ? null : Number(field.userLimit),
            expireTime: formatLocalDateTime(field.expireTime),
            machineId: field.machineId || "",
            status: Number(field.status)
        };
    }

    function formatLocalDateTime(value) {
        return value ? value.replace(" ", "T") : value;
    }

    function renderOfflineResult(data, licenseKey) {
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
            AppRequest.showMessage("\u5df2\u590d\u5236", 1);
        } catch (error) {
            AppRequest.showMessage("\u590d\u5236\u5931\u8d25", 2);
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

    function apiFor(pattern, id) {
        return pattern.replace("{id}", encodeURIComponent(id));
    }

    function formatLimit(value) {
        return Number(value || 0) === 0 ? "\u4e0d\u9650" : value;
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
