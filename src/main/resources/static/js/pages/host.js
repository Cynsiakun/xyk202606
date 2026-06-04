layui.use(["table", "form", "layer"], function () {
    var table = layui.table;
    var form = layui.form;
    var layer = layui.layer;
    var editingHostId = null;
    var hostTableId = "hostTable";

    AppTable.renderPageTable(table, {
        elem: "#" + hostTableId,
        url: "/api/host/list",
        cols: [[
            {field: "id", title: "ID", width: 70, sort: true},
            {field: "hostname", title: "主机名", minWidth: 140, templet: function (d) { return d.hostname || "-"; }},
            {field: "ipv4", title: "主IP", minWidth: 130, templet: function (d) { return d.ipv4 || "-"; }},
            {field: "macAddress", title: "MAC地址", minWidth: 160},
            {field: "osName", title: "操作系统", minWidth: 160, templet: function (d) {
                var parts = [d.osName, d.osRelease].filter(Boolean).join(" ");
                return parts || "-";
            }},
            {field: "memUsage", title: "内存占用", width: 110, templet: function (d) { return d.memUsage || "-"; }},
            {field: "status", title: "状态", width: 90, templet: function (d) {
                return d.status === 1
                        ? '<span class="status-tag success">在线</span>'
                        : '<span class="status-tag fail">离线</span>';
            }},
            {title: "操作", width: 220, fixed: "right", templet: function () {
                var buttons = '<button type="button" class="layui-btn layui-btn-primary layui-btn-xs" lay-event="detail">详情</button>';
                if (AppAuth.hasPermission("host:update")) {
                    buttons += '<button type="button" class="layui-btn layui-btn-xs" lay-event="edit">编辑</button>';
                }
                if (AppAuth.hasPermission("host:delete")) {
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

    table.on("tool(hostTable)", function (obj) {
        if (obj.event === "detail") {
            openDetailDialog(obj.data);
        }
        if (obj.event === "edit") {
            openHostDialog(obj.data);
        }
        if (obj.event === "delete") {
            confirmDelete(obj.data);
        }
    });

    var addHostButton = document.getElementById("addHostButton");
    if (AppAuth.hasPermission("host:create")) {
        addHostButton.addEventListener("click", function () {
            openHostDialog(null);
        });
    } else {
        addHostButton.style.display = "none";
    }

    document.getElementById("resetButton").addEventListener("click", function () {
        form.val("hostSearchForm", {keyword: ""});
        AppTable.reload(table, hostTableId, {keyword: ""});
    });

    // 仅刷新数据、保留当前页码与滚动位置，避免整表重渲染导致的页面跳动。
    function refreshData() {
        table.reloadData(hostTableId, {scrollPos: "fixed"});
    }

    document.getElementById("refreshButton").addEventListener("click", refreshData);

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
                form.render();
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
                layero.find('[data-action="close"]').on("click", function () {
                    layer.close(index);
                });
            },
            end: function () {
                editingHostId = null;
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
                {label: "ID", value: h.id, full: false},
                {label: "主机名", value: h.hostname, full: false},
                {label: "主IP", value: h.ipv4, full: false},
                {label: "MAC地址", value: h.macAddress, full: false},
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
                {label: "更新时间", value: AppUtils.formatDateTime(h.updatedAt)}
            ]}
        ];

        var html = '<div class="host-detail">';
        sections.forEach(function (section) {
            html += '<div class="host-detail-section">';
            html += '<div class="host-detail-section-title">' + section.title + '</div>';
            html += '<div class="host-detail-grid">';
            section.items.forEach(function (item) {
                var value = item.raw ? item.value : formatValue(item.value);
                html += '<div class="host-detail-item' + (item.full ? ' full' : '') + '">';
                html += '<span class="host-detail-label">' + item.label + '</span>';
                html += '<span class="host-detail-value">' + value + '</span>';
                html += '</div>';
            });
            html += '</div></div>';
        });
        html += '</div>';
        return html;
    }

    function formatValue(value) {
        if (value == null || value === "" || value === "-") {
            return "-";
        }
        return escapeHtml(String(value));
    }

    function escapeHtml(text) {
        return text
                .replace(/&/g, "&amp;")
                .replace(/</g, "&lt;")
                .replace(/>/g, "&gt;")
                .replace(/"/g, "&quot;")
                .replace(/'/g, "&#39;");
    }

    function confirmDelete(host) {
        var label = host.hostname || host.macAddress;
        AppDialog.confirm(layer, "确定删除主机“" + label + "”吗？", async function (index) {
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
