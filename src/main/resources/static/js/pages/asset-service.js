layui.use(["table", "form", "layer"], function () {
    var table = layui.table;
    var form = layui.form;
    var layer = layui.layer;
    var tableId = "assetTable";
    var ASSET_TYPE = "service";
    var formatSource = function (source) { return source || "PLATFORM"; };

    AppTable.renderPageTable(table, {
        elem: "#" + tableId,
        url: "/api/assets/" + ASSET_TYPE + "/list",
        cols: [[
            {field: "id", title: "ID", width: 70, sort: true},
            {field: "hostName", title: "主机名", minWidth: 140, templet: function (d) { return d.hostName || "-"; }},
            {field: "macAddress", title: "MAC地址", minWidth: 150},
            {field: "source", title: "来源", width: 110, templet: function (d) { return formatSource(d.source); }},
            {field: "assetCount", title: "服务数", width: 90},
            {field: "updatedAt", title: "更新时间", width: 170, templet: function (d) { return AppUtils.formatDateTime(d.updatedAt); }},
            {title: "操作", width: 160, fixed: "right", templet: function () {
                var buttons = '<button type="button" class="layui-btn layui-btn-primary layui-btn-xs" lay-event="detail">详情</button>';
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
                {label: "任务ID", value: obj.data.taskId}
            ]);
        }
        if (obj.event === "delete") {
            var row = obj.data;
            AppDialog.confirm(layer, "确定删除该服务资产记录吗？", async function (index) {
                try {
                    await AppRequest.request("/api/assets/" + ASSET_TYPE + "/" + row.id, {method: "DELETE"}, {successMessage: "删除成功"});
                    layer.close(index);
                    table.reload(tableId);
                } catch (error) { return; }
            });
        }
    });
});
