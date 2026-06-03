layui.use(["table", "form"], function () {
    var table = layui.table;
    var form = layui.form;
    var logTableId = "logTable";

    AppTable.renderPageTable(table, {
        elem: "#" + logTableId,
        url: "/api/login-log/list",
        cols: [[
            {field: "id", title: "ID", width: 80, sort: true},
            {field: "userName", title: "用户名", minWidth: 140},
            {field: "loginTime", title: "登录时间", minWidth: 180, templet: function (d) { return AppUtils.formatDateTime(d.loginTime); }},
            {field: "ipAddress", title: "IP地址", minWidth: 140},
            {field: "status", title: "状态", width: 100, templet: function (d) {
                return d.status === 1
                        ? '<span class="status-tag success">成功</span>'
                        : '<span class="status-tag fail">失败</span>';
            }},
            {field: "message", title: "消息", minWidth: 180}
        ]]
    });

    form.on("submit(searchSubmit)", function (data) {
        AppTable.reload(table, logTableId, {
            userName: data.field.userName || "",
            status: data.field.status === "" ? null : data.field.status
        });
        return false;
    });

    document.getElementById("resetButton").addEventListener("click", function () {
        form.val("searchForm", {
            userName: "",
            status: ""
        });
        AppTable.reload(table, logTableId, {
            userName: "",
            status: null
        });
    });
});
