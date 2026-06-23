layui.use(["table", "form", "layer"], function () {
    var table = layui.table;
    var form = layui.form;
    var layer = layui.layer;
    var tableId = "assetTable";
    var ASSET_TYPE = "port";
    var formatSource = function (source) { return source || "PLATFORM"; };

    AppTable.renderPageTable(table, {
        elem: "#" + tableId,
        url: "/api/assets/" + ASSET_TYPE + "/list",
        cols: [[
            {field: "id", title: "ID", width: 70, sort: true},
            {field: "hostName", title: "主机名", minWidth: 150, templet: function (d) { return d.hostName || "-"; }},
            {field: "macAddress", title: "MAC地址", minWidth: 160, templet: function (d) { return d.macAddress || "-"; }},
            {field: "source", title: "来源", width: 110, templet: function (d) { return formatSource(d.source); }},
            {field: "taskId", title: "任务ID", minWidth: 180, templet: function (d) { return d.taskId || "-"; }},
            {field: "portCount", title: "端口数", width: 100, templet: function (d) { return d.portCount != null ? d.portCount : (d.assetCount || 0); }},
            {field: "updatedAt", title: "更新时间", width: 170, templet: function (d) { return AppUtils.formatDateTime(d.updatedAt); }},
            {title: "操作", width: 220, fixed: "right", templet: function () {
                var buttons = '<button type="button" class="layui-btn layui-btn-primary layui-btn-xs" lay-event="detail">详情</button>';
                buttons += '<button type="button" class="layui-btn layui-btn-normal layui-btn-xs" lay-event="rematch">重新匹配</button>';
                if (AppAuth.hasPermission("asset:delete")) {
                    buttons += '<button type="button" class="layui-btn layui-btn-danger layui-btn-xs" lay-event="delete">删除</button>';
                }
                return buttons;
            }}
        ]]
    });

    form.on("submit(assetSearchSubmit)", function (data) {
        AppTable.reload(table, tableId, {keyword: data.field.keyword || ""});
        return false;
    });

    document.getElementById("resetButton").addEventListener("click", function () {
        form.val("assetSearchForm", {keyword: ""});
        AppTable.reload(table, tableId, {keyword: ""});
    });

    document.getElementById("refreshButton").addEventListener("click", function () {
        table.reloadData(tableId, {scrollPos: "fixed"});
    });

    table.on("tool(assetTable)", function (obj) {
        if (obj.event === "detail") {
            AssetUtils.openAssetDetail(layer, ASSET_TYPE, obj.data, [
                {label: "主机名", value: obj.data.hostName},
                {label: "MAC地址", value: obj.data.macAddress},
                {label: "来源", value: formatSource(obj.data.source)},
                {label: "任务ID", value: obj.data.taskId},
                {label: "端口总数", value: obj.data.portCount != null ? obj.data.portCount : (obj.data.assetCount || 0)}
            ]);
        }

        if (obj.event === "rematch") {
            var row = obj.data;
            AppDialog.confirm(layer, "确定基于最新规则重新匹配该任务的端口资产吗？", async function (index) {
                try {
                    var result = await AppRequest.request("/api/assets/" + ASSET_TYPE + "/" + row.id + "/rematch", {
                        method: "POST"
                    }, {successMessage: "重新匹配完成"});
                    layer.close(index);
                    table.reload(tableId);
                    layer.msg("重新匹配完成，本次生成 " + ((result && result.data) || 0) + " 条端口资产", {icon: 1});
                } catch (error) {
                    return;
                }
            });
        }

        if (obj.event === "delete") {
            var taskRow = obj.data;
            AppDialog.confirm(layer, "确定删除该端口资产任务记录吗？", async function (index) {
                try {
                    await AppRequest.request("/api/assets/" + ASSET_TYPE + "/" + taskRow.id, {method: "DELETE"}, {successMessage: "删除成功"});
                    layer.close(index);
                    table.reload(tableId);
                } catch (error) {
                    return;
                }
            });
        }
    });
});
