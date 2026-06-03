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
            {field: "ipv4", title: "IPv4", minWidth: 130, templet: function (d) { return d.ipv4 || "-"; }},
            {field: "macAddress", title: "MAC地址", minWidth: 160},
            {field: "osName", title: "操作系统", minWidth: 160, templet: function (d) {
                var parts = [d.osName, d.osRelease].filter(Boolean).join(" ");
                return parts || "-";
            }},
            {field: "cpuModel", title: "CPU", minWidth: 200, templet: function (d) {
                if (!d.cpuModel) { return "-"; }
                var cores = "";
                if (d.cpuPhysicalCores != null || d.cpuLogicalCores != null) {
                    cores = " (" + (d.cpuPhysicalCores != null ? d.cpuPhysicalCores : "?") + "核/"
                            + (d.cpuLogicalCores != null ? d.cpuLogicalCores : "?") + "线程)";
                }
                return d.cpuModel + cores;
            }},
            {field: "memUsage", title: "内存", minWidth: 150, templet: function (d) {
                if (!d.memTotal && !d.memUsage) { return "-"; }
                return (d.memTotal || "-") + (d.memUsage ? " / " + d.memUsage : "");
            }},
            {field: "status", title: "状态", width: 90, templet: function (d) {
                return d.status === 1
                        ? '<span class="status-tag success">在线</span>'
                        : '<span class="status-tag fail">离线</span>';
            }},
            {field: "updatedAt", title: "更新时间", minWidth: 170, templet: function (d) { return AppUtils.formatDateTime(d.updatedAt); }},
            {title: "操作", width: 160, fixed: "right", templet: function () {
                return '<button type="button" class="layui-btn layui-btn-xs" lay-event="edit">编辑</button>'
                        + '<button type="button" class="layui-btn layui-btn-danger layui-btn-xs" lay-event="delete">删除</button>';
            }}
        ]]
    });

    form.on("submit(hostSearchSubmit)", function (data) {
        AppTable.reload(table, hostTableId, {
            keyword: data.field.keyword || ""
        });
        return false;
    });

    form.on("submit(saveHost)", async function (data) {
        var payload = normalizePayload(data.field);
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
            return false;
        }
        return false;
    });

    table.on("tool(hostTable)", function (obj) {
        if (obj.event === "edit") {
            openHostDialog(obj.data);
        }
        if (obj.event === "delete") {
            confirmDelete(obj.data);
        }
    });

    document.getElementById("addHostButton").addEventListener("click", function () {
        openHostDialog(null);
    });

    document.getElementById("resetButton").addEventListener("click", function () {
        form.val("hostSearchForm", {keyword: ""});
        AppTable.reload(table, hostTableId, {keyword: ""});
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
