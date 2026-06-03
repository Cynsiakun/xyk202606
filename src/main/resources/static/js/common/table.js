(function (window) {
    function renderPageTable(table, config) {
        return table.render({
            elem: config.elem,
            url: config.url,
            method: config.method || "GET",
            headers: {
                Authorization: "Bearer " + window.AppAuth.getToken()
            },
            page: true,
            limit: config.limit || 10,
            limits: config.limits || [10, 20, 50],
            request: {
                pageName: "page",
                limitName: "size"
            },
            where: config.where || {},
            parseData: function (res) {
                if (res.code !== 200) {
                    if (window.AppRequest && typeof window.AppRequest.showMessage === "function") {
                        window.AppRequest.showMessage(res.message || "请求失败", 2, 2200);
                    }
                    if ((res.code === 401 || res.code === 403) && window.AppAuth) {
                        window.AppAuth.clearLogin();
                        window.AppAuth.redirectToLogin();
                    }
                }

                var pageData = res.data || {};
                return {
                    code: res.code === 200 ? 0 : res.code,
                    msg: res.message,
                    count: pageData.total || 0,
                    data: pageData.list || []
                };
            },
            cols: config.cols
        });
    }

    function reload(table, tableId, where) {
        table.reload(tableId, {
            page: {curr: 1},
            where: where || {}
        });
    }

    window.AppTable = {
        renderPageTable: renderPageTable,
        reload: reload
    };
})(window);
