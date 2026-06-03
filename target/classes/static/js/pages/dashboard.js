layui.use(["layer"], function () {
    var layer = layui.layer;

    loadStatistics();

    async function loadStatistics() {
        try {
            var result = await AppRequest.request("/api/dashboard/statistics", {method: "GET"});
            var data = result.data || {};
            setText("totalUsers", data.totalUsers);
            setText("todayLoginCount", data.todayLoginCount);
            setText("todayNewUsers", data.todayNewUsers);
            setText("weekActiveUsers", data.weekActiveUsers);
            setText("totalLogs", data.totalLogs);
        } catch (error) {
            return null;
        }
    }

    function setText(id, value) {
        document.getElementById(id).textContent = value == null ? 0 : value;
    }
});
