layui.use(["table", "form", "layer"], function () {
    var table = layui.table;
    var form = layui.form;
    var layer = layui.layer;
    var hostTableId = "hostTable";
    var editingHostId = null;
    var importFile = null;

    AppTable.renderPageTable(table, {
        elem: "#" + hostTableId,
        url: "/api/host/list",
        cols: [[
            {field: "id", title: "ID", width: 70, sort: true},
            {field: "hostname", title: "主机名", minWidth: 140, templet: function (d) { return d.hostname || "-"; }},
            {field: "ipv4", title: "IP", minWidth: 130, templet: function (d) { return d.ipv4 || "-"; }},
            {field: "macAddress", title: "MAC地址", minWidth: 170, templet: function (d) { return d.macAddress || "-"; }},
            {field: "osRelease", title: "操作系统", minWidth: 180, templet: function (d) {
                return d.osRelease || d.osVersion || d.osName || "-";
            }},
            {title: "内存占用", width: 150, templet: function (d) {
                var total = d.memTotal || "-";
                var usage = d.memUsage || "-";
                if (total === "-" && usage === "-") {
                    return "-";
                }
                return total + (usage === "-" ? "" : " / " + usage);
            }},
            {field: "status", title: "状态", width: 90, fixed: "right", templet: function (d) {
                return d.status === 1
                    ? '<span class="status-tag success">在线</span>'
                    : '<span class="status-tag fail">离线</span>';
            }},
            {title: "操作", width: 360, fixed: "right", templet: function () {
                var buttons = '<button type="button" class="layui-btn layui-btn-primary layui-btn-xs" lay-event="detail">详情</button>';
                if (AppAuth.canPolicy("TENANT_ASSET_VIEW")) {
                    buttons += '<button type="button" class="layui-btn layui-btn-normal layui-btn-xs" lay-event="asset">查看资产</button>';
                }
                if (AppAuth.canPolicy("TENANT_ASSET_EXPORT")) {
                    buttons += '<button type="button" class="layui-btn layui-btn-warm layui-btn-xs" lay-event="export">导出</button>';
                }
                if (AppAuth.canPolicy("TENANT_ASSET_PROBE")) {
                    buttons += '<button type="button" class="layui-btn layui-btn-normal layui-btn-xs" lay-event="probe">资产探测</button>';
                }
                if (AppAuth.canPolicy("TENANT_HOST_UPDATE")) {
                    buttons += '<button type="button" class="layui-btn layui-btn-xs" lay-event="edit">编辑</button>';
                }
                if (AppAuth.canPolicy("TENANT_HOST_DELETE")) {
                    buttons += '<button type="button" class="layui-btn layui-btn-danger layui-btn-xs" lay-event="delete">删除</button>';
                }
                return buttons;
            }}
        ]]
    });

    form.on("submit(hostSearchSubmit)", function (data) {
        AppTable.reload(table, hostTableId, {
            keyword: data.field.keyword || ""
        });
        return false;
    });

    form.on("submit(saveHost)", function (data) {
        var payload = normalizePayload(data.field);
        (async function () {
            try {
                if (editingHostId) {
                    await AppRequest.request("/api/host/" + editingHostId, {
                        method: "PUT",
                        body: payload
                    }, {
                        successMessage: "保存成功"
                    });
                } else {
                    await AppRequest.request("/api/host", {
                        method: "POST",
                        body: payload
                    }, {
                        successMessage: "保存成功"
                    });
                }
                layer.closeAll("page");
                table.reload(hostTableId);
            } catch (error) {
                return;
            }
        })();
        return false;
    });

    form.on("submit(startProbe)", function (data) {
        var payload = {
            account: data.field.account === "on",
            service: data.field.service === "on",
            process: data.field.process === "on",
            app: data.field.app === "on",
            macAddress: data.field.macAddress
        };
        var portScanOn = data.field.portScan === "on";
        var scanOpts = portScanOn ? {
            scanRange: data.field.portScanRange || "common",
            customPorts: data.field.portScanCustomPorts || "",
            grabBanner: data.field.grabBanner === "on"
        } : null;
        (async function () {
            await submitProbe(payload, false);
            if (portScanOn) {
                await submitPortScan(data.field.macAddress, scanOpts);
            }
        })();
        return false;
    });

    form.on("submit(saveProbeStrategy)", function (data) {
        var payload = {
            enabled: data.field.enabled === "on",
            periodHours: Number(data.field.periodHours),
            account: data.field.account === "on",
            service: data.field.service === "on",
            process: data.field.process === "on",
            app: data.field.app === "on",
            portScan: data.field.portScan === "on",
            fingerprint: data.field.fingerprint === "on",
            portScanRange: data.field.portScanRange || "common",
            portScanCustomPorts: data.field.portScanCustomPorts || ""
        };
        (async function () {
            try {
                await AppRequest.request("/api/host/probe-strategy", {
                    method: "PUT",
                    body: payload
                }, {
                    successMessage: "保存成功"
                });
                layer.closeAll("page");
            } catch (error) {
                return;
            }
        })();
        return false;
    });

    form.on("checkbox", function (data) {
        if (data.elem.name !== "portScan") return;
        var formElem = data.elem.closest("form");
        if (!formElem) return;
        var optionsWrap = formElem.querySelector(".probe-port-scan-options, .probe-strategy-port-scan-options");
        var customPorts = formElem.querySelector(".probe-custom-ports, .probe-strategy-custom-ports");
        var portScanRange = formElem.querySelector('select[name="portScanRange"]');
        if (optionsWrap) {
            optionsWrap.style.display = data.elem.checked ? "" : "none";
        }
        if (customPorts && portScanRange) {
            customPorts.style.display = (data.elem.checked && portScanRange.value === "custom") ? "" : "none";
        }
    });

    form.on("select", function (data) {
        if (data.elem.name !== "portScanRange") return;
        var formElem = data.elem.closest("form");
        if (!formElem) return;
        var customPorts = formElem.querySelector(".probe-custom-ports, .probe-strategy-custom-ports");
        var portScanCheckbox = formElem.querySelector('input[name="portScan"]');
        if (customPorts && portScanCheckbox) {
            customPorts.style.display = (portScanCheckbox.checked && data.value === "custom") ? "" : "none";
        }
    });

    table.on("tool(hostTable)", function (obj) {
        if (obj.event === "detail") {
            openDetailDialog(obj.data);
        }
        if (obj.event === "asset") {
            AssetUtils.openHostAssetTabs(layer, obj.data);
        }
        if (obj.event === "export") {
            openExportDialog(obj.data);
        }
        if (obj.event === "probe") {
            openProbeDialog(obj.data);
        }
        if (obj.event === "edit") {
            openHostDialog(obj.data);
        }
        if (obj.event === "delete") {
            confirmDelete(obj.data);
        }
    });

    bindToolbar();

    function bindToolbar() {
        var addHostButton = document.getElementById("addHostButton");
        if (AppAuth.canPolicy("TENANT_HOST_CREATE")) {
            addHostButton.addEventListener("click", function () {
                openHostDialog(null);
            });
        } else {
            addHostButton.style.display = "none";
        }

        var importButton = document.getElementById("importButton");
        if (AppAuth.canPolicy("TENANT_HOST_CREATE")) {
            importButton.addEventListener("click", openImportDialog);
        } else {
            importButton.style.display = "none";
        }

        var probeStrategyButton = document.getElementById("probeStrategyButton");
        if (AppAuth.canPolicy("TENANT_ASSET_PROBE")) {
            probeStrategyButton.addEventListener("click", openProbeStrategyDialog);
        } else {
            probeStrategyButton.style.display = "none";
        }

        document.getElementById("resetButton").addEventListener("click", function () {
            form.val("hostSearchForm", {keyword: ""});
            AppTable.reload(table, hostTableId, {keyword: ""});
        });

        document.getElementById("refreshButton").addEventListener("click", refreshData);
    }

    function refreshData() {
        table.reloadData(hostTableId, {scrollPos: "fixed"});
    }

    var autoRefreshTimer = null;
    form.on("select(autoRefreshSelect)", function (data) {
        if (autoRefreshTimer) {
            clearInterval(autoRefreshTimer);
            autoRefreshTimer = null;
        }
        var seconds = Number(data.value);
        if (seconds > 0) {
            autoRefreshTimer = setInterval(refreshData, seconds * 1000);
        }
    });

    async function submitProbe(payload, force) {
        var requestPayload = Object.assign({}, payload, {force: !!force});
        try {
            await AppRequest.request("/api/host/probe", {
                method: "POST",
                body: requestPayload
            }, {
                successMessage: "探测任务已下发"
            });
            layer.closeAll("page");
            table.reload(hostTableId);
        } catch (error) {
            if (error && error.code === 409 && !force) {
                layer.confirm(error.message || "该主机资产数据仍在有效期内，是否继续探测？", {
                    icon: 3,
                    title: "确认探测"
                }, function (index) {
                    layer.close(index);
                    submitProbe(payload, true);
                });
            }
        }
    }

    async function submitPortScan(macAddress, opts) {
        try {
            await AppRequest.request("/api/port-scan/trigger", {
                method: "POST",
                body: {
                    macAddress: macAddress,
                    scanRange: opts.scanRange || "common",
                    customPorts: opts.customPorts || "",
                    grabBanner: !!opts.grabBanner
                }
            }, {
                successMessage: "端口扫描任务已下发"
            });
        } catch (error) {
            return;
        }
    }

    function openHostDialog(host) {
        editingHostId = host ? host.id : null;
        var viewportHeight = window.innerHeight || 640;
        var viewportWidth = window.innerWidth || 600;
        var dialogHeight = Math.min(640, viewportHeight - 30);
        var dialogWidth = Math.min(600, viewportWidth - 30);
        var index = layer.open({
            type: 1,
            title: editingHostId ? "编辑主机" : "新增主机",
            area: [dialogWidth + "px", dialogHeight + "px"],
            content: AppUtils.getTemplateHtml("hostFormTemplate"),
            success: function (layero) {
                form.val("hostForm", {
                    hostname: host ? host.hostname || "" : "",
                    ipv4: host ? host.ipv4 || "" : "",
                    macAddress: host ? host.macAddress || "" : "",
                    osName: host ? host.osName || "" : "",
                    osVersion: host ? host.osVersion || "" : "",
                    osArch: host ? host.osArch || "" : "",
                    osRelease: host ? host.osRelease || "" : "",
                    cpuModel: host ? host.cpuModel || "" : "",
                    cpuPhysicalCores: host && host.cpuPhysicalCores != null ? host.cpuPhysicalCores : "",
                    cpuLogicalCores: host && host.cpuLogicalCores != null ? host.cpuLogicalCores : "",
                    memTotal: host ? host.memTotal || "" : "",
                    memUsed: host ? host.memUsed || "" : "",
                    memAvailable: host ? host.memAvailable || "" : "",
                    memUsage: host ? host.memUsage || "" : "",
                    status: host && host.status === 0 ? "0" : "1"
                });
                form.render(null, "hostForm");
                layero.find('[data-action="close"]').on("click", function () {
                    layer.close(index);
                });
            },
            end: function () {
                editingHostId = null;
            }
        });
    }

    function openImportDialog() {
        importFile = null;
        var viewportWidth = window.innerWidth || 560;
        var dialogWidth = Math.min(560, viewportWidth - 30);
        var index = layer.open({
            type: 1,
            title: "导入主机 CSV",
            area: [dialogWidth + "px", "380px"],
            content: AppUtils.getTemplateHtml("hostImportTemplate"),
            success: function (layero) {
                var fileInput = layero[0].querySelector("#hostCsvFile");
                var fileName = layero[0].querySelector("#hostCsvFileName");
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
            var result = await AppRequest.request("/api/host/import", {
                method: "POST",
                body: formData
            }, {
                successMessage: "导入完成"
            });
            layer.close(dialogIndex);
            table.reload(hostTableId);
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

    function openProbeStrategyDialog() {
        var viewportHeight = window.innerHeight || 640;
        var viewportWidth = window.innerWidth || 500;
        var dialogHeight = Math.min(580, viewportHeight - 30);
        var dialogWidth = Math.min(500, viewportWidth - 30);
        layer.open({
            type: 1,
            title: "探测策略配置",
            area: [dialogWidth + "px", dialogHeight + "px"],
            content: AppUtils.getTemplateHtml("probeStrategyTemplate"),
            success: function (layero, index) {
                layero.find('[data-action="close"]').on("click", function () {
                    layer.close(index);
                });

                (async function () {
                    var strategy = {
                        enabled: false,
                        periodHours: 8,
                        account: true, service: true, process: true, app: true,
                        portScan: false, fingerprint: false,
                        portScanRange: "common", portScanCustomPorts: ""
                    };
                    try {
                        var result = await AppRequest.request("/api/host/probe-strategy", {
                            method: "GET"
                        }, {
                            showErrorMessage: false
                        });
                        if (result && result.data) {
                            strategy = Object.assign(strategy, result.data);
                        }
                    } catch (error) {
                        return;
                    }
                    form.val("probeStrategyForm", {
                        enabled: !!strategy.enabled,
                        periodHours: String(strategy.periodHours || 8),
                        account: !!strategy.account,
                        service: !!strategy.service,
                        process: !!strategy.process,
                        app: !!strategy.app,
                        portScan: !!strategy.portScan,
                        fingerprint: !!strategy.fingerprint,
                        portScanRange: strategy.portScanRange || "common",
                        portScanCustomPorts: strategy.portScanCustomPorts || ""
                    });
                    form.render(null, "probeStrategyForm");
                    var optionsWrap = layero[0].querySelector('.probe-strategy-port-scan-options');
                    var customPorts = layero[0].querySelector('.probe-strategy-custom-ports');
                    if (optionsWrap) {
                        optionsWrap.style.display = strategy.portScan ? '' : 'none';
                    }
                    if (customPorts) {
                        var showCustom = strategy.portScanRange === 'custom' && !!strategy.portScan;
                        customPorts.style.display = showCustom ? '' : 'none';
                    }
                })();
            }
        });
    }

    function openProbeDialog(host) {
        var viewportHeight = window.innerHeight || 640;
        var viewportWidth = window.innerWidth || 460;
        var dialogHeight = Math.min(560, viewportHeight - 30);
        var dialogWidth = Math.min(460, viewportWidth - 30);
        var index = layer.open({
            type: 1,
            title: "资产探测",
            area: [dialogWidth + "px", dialogHeight + "px"],
            content: AppUtils.getTemplateHtml("assetProbeTemplate"),
            success: function (layero) {
                form.val("assetProbeForm", {
                    macAddress: host ? host.macAddress || "" : "",
                    account: true,
                    service: false,
                    process: false,
                    app: false,
                    portScan: false,
                    portScanRange: "common",
                    portScanCustomPorts: "",
                    grabBanner: false
                });
                form.render(null, "assetProbeForm");

                layero.find('[data-action="close"]').on("click", function () {
                    layer.close(index);
                });
            }
        });
    }

    function openDetailDialog(host) {
        var viewportHeight = window.innerHeight || 640;
        var viewportWidth = window.innerWidth || 640;
        var dialogHeight = Math.min(640, viewportHeight - 30);
        var dialogWidth = Math.min(680, viewportWidth - 30);
        layer.open({
            type: 1,
            title: "主机详情",
            area: [dialogWidth + "px", dialogHeight + "px"],
            content: buildDetailHtml(host)
        });
    }

    function openExportDialog(host) {
        layer.open({
            type: 1,
            title: "导出资产清单",
            area: ["320px", "190px"],
            content: '<div class="popup-form">'
                + '<div class="layui-btn-container" style="padding-top: 12px;">'
                + '<button type="button" class="layui-btn layui-btn-fluid" data-export-format="json">JSON</button>'
                + '<button type="button" class="layui-btn layui-btn-normal layui-btn-fluid" data-export-format="excel">Excel</button>'
                + "</div>"
                + "</div>",
            success: function (layero, index) {
                layero.find("[data-export-format]").on("click", function () {
                    var format = this.getAttribute("data-export-format");
                    layer.close(index);
                    downloadAssetExport(host, format);
                });
            }
        });
    }

    async function downloadAssetExport(host, format) {
        var token = AppAuth.getToken();
        var url = "/api/asset/export/" + encodeURIComponent(host.id) + "?format=" + encodeURIComponent(format);
        try {
            var response = await fetch(url, {
                method: "GET",
                headers: token ? {Authorization: "Bearer " + token} : {}
            });
            if (!response.ok) {
                await handleDownloadError(response);
                return;
            }
            var blob;
            var fileName;
            if (format === "excel") {
                blob = await response.blob();
                fileName = getDownloadFileName(response) || buildExportFileName(host.id, "xlsx");
            } else {
                var result = await response.json();
                if (!result || result.code !== 200) {
                    layer.msg(result && result.message ? result.message : "导出失败", {icon: 2});
                    return;
                }
                blob = new Blob([JSON.stringify(result.data, null, 2)], {type: "application/json;charset=utf-8"});
                fileName = buildExportFileName(host.id, "json");
            }
            triggerDownload(blob, fileName);
            layer.msg("导出成功", {icon: 1});
        } catch (error) {
            layer.msg(error.message || "导出失败", {icon: 2});
        }
    }

    async function handleDownloadError(response) {
        var message = "导出失败";
        try {
            var result = await response.json();
            if (result && result.message) {
                message = result.message;
            }
        } catch (error) {
            message = response.statusText || message;
        }
        if (response.status === 401 || response.status === 403) {
            AppAuth.clearLogin();
            AppAuth.redirectToLogin();
        } else {
            layer.msg(message, {icon: 2});
        }
    }

    function getDownloadFileName(response) {
        var disposition = response.headers.get("content-disposition") || "";
        var utf8Match = disposition.match(/filename\*=UTF-8''([^;]+)/i);
        if (utf8Match) {
            return decodeURIComponent(utf8Match[1]);
        }
        var nameMatch = disposition.match(/filename="?([^";]+)"?/i);
        return nameMatch ? nameMatch[1] : "";
    }

    function buildExportFileName(hostId, suffix) {
        var now = new Date();
        var timestamp = [
            now.getFullYear(),
            pad2(now.getMonth() + 1),
            pad2(now.getDate()),
            pad2(now.getHours()),
            pad2(now.getMinutes()),
            pad2(now.getSeconds())
        ].join("");
        return "asset_export_" + hostId + "_" + timestamp + "." + suffix;
    }

    function pad2(value) {
        return value < 10 ? "0" + value : String(value);
    }

    function triggerDownload(blob, fileName) {
        var url = URL.createObjectURL(blob);
        var link = document.createElement("a");
        link.href = url;
        link.download = fileName;
        document.body.appendChild(link);
        link.click();
        document.body.removeChild(link);
        URL.revokeObjectURL(url);
    }

    function buildDetailHtml(host) {
        var h = host || {};
        var statusTag = h.status === 1
            ? '<span class="status-tag success">在线</span>'
            : '<span class="status-tag fail">离线</span>';
        var cores = "";
        if (h.cpuPhysicalCores != null || h.cpuLogicalCores != null) {
            cores = (h.cpuPhysicalCores != null ? h.cpuPhysicalCores : "?") + " 物理核 / "
                + (h.cpuLogicalCores != null ? h.cpuLogicalCores : "?") + " 逻辑核";
        }
        var sections = [
            {title: "基本信息", items: [
                {label: "ID", value: h.id},
                {label: "主机名", value: h.hostname},
                {label: "IP", value: h.ipv4},
                {label: "MAC地址", value: h.macAddress},
                {label: "状态", value: statusTag, raw: true, full: true}
            ]},
            {title: "操作系统", items: [
                {label: "系统名称", value: h.osName},
                {label: "系统版本", value: h.osVersion},
                {label: "系统架构", value: h.osArch},
                {label: "具体版本", value: h.osRelease}
            ]},
            {title: "CPU", items: [
                {label: "CPU型号", value: h.cpuModel, full: true},
                {label: "核心数", value: cores, full: true}
            ]},
            {title: "内存", items: [
                {label: "总内存", value: h.memTotal},
                {label: "使用率", value: h.memUsage},
                {label: "已使用", value: h.memUsed},
                {label: "可用内存", value: h.memAvailable}
            ]},
            {title: "时间", items: [
                {label: "创建时间", value: AppUtils.formatDateTime(h.createdAt)},
                {label: "更新时间", value: AppUtils.formatDateTime(h.updatedAt)},
                {label: "最近探测时间", value: h.lastScanTime ? AppUtils.formatDateTime(h.lastScanTime) : "从未探测", full: true}
            ]}
        ];

        var html = '<div class="host-detail">';
        sections.forEach(function (section) {
            html += '<div class="host-detail-section">';
            html += '<div class="host-detail-section-title">' + section.title + "</div>";
            html += '<div class="host-detail-grid">';
            section.items.forEach(function (item) {
                var value = item.raw ? item.value : formatValue(item.value);
                html += '<div class="host-detail-item' + (item.full ? " full" : "") + '">';
                html += '<span class="host-detail-label">' + item.label + "</span>";
                html += '<span class="host-detail-value">' + value + "</span>";
                html += "</div>";
            });
            html += "</div></div>";
        });
        html += "</div>";
        return html;
    }

    function formatValue(value) {
        if (value == null || value === "" || value === "-") {
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

    function confirmDelete(host) {
        var label = host.hostname || host.macAddress;
        AppDialog.confirm(layer, '确定删除主机 "' + label + '" 吗？', async function (index) {
            try {
                await AppRequest.request("/api/host/" + host.id, {
                    method: "DELETE"
                }, {
                    successMessage: "删除成功"
                });
                layer.close(index);
                table.reload(hostTableId);
            } catch (error) {
                return;
            }
        });
    }

    function normalizePayload(field) {
        return {
            hostname: field.hostname || "",
            ipv4: field.ipv4 || "",
            macAddress: field.macAddress,
            osName: field.osName || "",
            osVersion: field.osVersion || "",
            osArch: field.osArch || "",
            osRelease: field.osRelease || "",
            cpuModel: field.cpuModel || "",
            cpuPhysicalCores: field.cpuPhysicalCores === "" ? null : Number(field.cpuPhysicalCores),
            cpuLogicalCores: field.cpuLogicalCores === "" ? null : Number(field.cpuLogicalCores),
            memTotal: field.memTotal || "",
            memUsed: field.memUsed || "",
            memAvailable: field.memAvailable || "",
            memUsage: field.memUsage || "",
            status: Number(field.status)
        };
    }
});
