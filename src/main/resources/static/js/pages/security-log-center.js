layui.use(["table", "form", "layer", "element", "laydate"], function () {
    var table = layui.table;
    var form = layui.form;
    var layer = layui.layer;
    var element = layui.element;
    var laydate = layui.laydate;

    var TABLE_ID = "logTable";
    var API = {
        list: "/api/security-log-center/list",
        stats: "/api/security-log-center/stats",
        detail: "/api/security-log-center/detail/",
        hosts: "/api/security-log-center/hosts",
        export: "/api/security-log-center/export"
    };

    // 查询状态：全部筛选在服务端完成
    var state = {
        logType: "Security",
        keyword: "",
        adv: {eventId: "", hostId: "", username: "", level: "", startTime: "", endTime: ""},
        sortField: "",
        sortOrder: ""
    };
    var hostOptions = [];
    var autoRefreshTimer = null;
    var detailLayerIndex = null;

    init();

    function init() {
        renderTable();
        loadStats();
        loadHostOptions();
        bindEvents();
        form.render(); // 渲染自动刷新开关皮肤
    }

    function escapeHtml(value) {
        if (value == null) {
            return "";
        }
        return String(value)
            .replace(/&/g, "&amp;")
            .replace(/</g, "&lt;")
            .replace(/>/g, "&gt;")
            .replace(/"/g, "&quot;")
            .replace(/'/g, "&#39;");
    }

    // 仅把有值的条件放入 where，避免空串污染后端类型绑定
    function buildWhere() {
        var where = {logType: state.logType};
        if (state.keyword) {
            where.keyword = state.keyword;
        }
        if (state.adv.eventId) {
            where.eventId = state.adv.eventId;
        }
        if (state.adv.hostId) {
            where.hostId = state.adv.hostId;
        }
        if (state.adv.username) {
            where.username = state.adv.username;
        }
        if (state.adv.level) {
            where.level = state.adv.level;
        }
        if (state.adv.startTime) {
            where.startTime = state.adv.startTime;
        }
        if (state.adv.endTime) {
            where.endTime = state.adv.endTime;
        }
        if (state.sortField) {
            where.sortField = state.sortField;
            where.sortOrder = state.sortOrder;
        }
        return where;
    }

    function renderTable() {
        table.render({
            elem: "#" + TABLE_ID,
            url: API.list,
            method: "GET",
            headers: {Authorization: "Bearer " + AppAuth.getToken()},
            page: true,
            limit: 20,
            limits: [20, 50, 100],
            autoSort: false,
            request: {pageName: "page", limitName: "size"},
            where: buildWhere(),
            parseData: function (res) {
                if (res.code !== 200) {
                    AppRequest.showMessage(res.message || "请求失败", 2, 2200);
                    if ((res.code === 401 || res.code === 403) && window.AppAuth) {
                        AppAuth.clearLogin();
                        AppAuth.redirectToLogin();
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
            text: {none: "暂无符合条件的日志"},
            cols: [[
                {field: "id", title: "ID", width: 100, sort: true},
                {field: "logType", title: "类型", width: 120, templet: "#logTypeTpl"},
                {field: "eventId", title: "EventID", width: 110, sort: true},
                {field: "level", title: "级别", width: 120, templet: "#levelTpl"},
                {field: "eventTime", title: "事件时间", width: 180, sort: true, templet: function (d) {
                    return AppUtils.formatDateTime(d.eventTime);
                }},
                {field: "hostname", title: "主机", minWidth: 150, templet: "#hostTpl"},
                {field: "username", title: "用户名", minWidth: 150, templet: function (d) {
                    return escapeHtml(d.username) || "-";
                }},
                {field: "message", title: "摘要", minWidth: 280, templet: function (d) {
                    return '<span class="msg-cell" title="' + escapeHtml(d.message) + '">' + escapeHtml(d.message) + "</span>";
                }},
                {title: "操作", width: 110, fixed: "right", align: "center", templet: "#opTpl"}
            ]]
        });
    }

    function reloadTable(resetPage) {
        table.reload(TABLE_ID, {
            where: buildWhere(),
            page: resetPage === false ? undefined : {curr: 1}
        });
    }

    function loadStats() {
        AppRequest.request(API.stats, {method: "GET"}, {showErrorMessage: false})
            .then(function (res) {
                var d = res.data || {};
                document.getElementById("statSecurity").textContent = d.todaySecurity || 0;
                document.getElementById("statSystem").textContent = d.todaySystem || 0;
                document.getElementById("statApplication").textContent = d.todayApplication || 0;
                document.getElementById("statError").textContent = d.todayError || 0;
            })
            .catch(function () { /* 忽略统计失败，不打断主流程 */ });
    }

    function loadHostOptions() {
        AppRequest.request(API.hosts, {method: "GET"}, {showErrorMessage: false})
            .then(function (res) {
                hostOptions = res.data || [];
            })
            .catch(function () { hostOptions = []; });
    }

    function bindEvents() {
        // 日志类型 Tab 切换
        document.getElementById("logTypeTabs").addEventListener("click", function (e) {
            var btn = e.target.closest(".log-type-tab");
            if (!btn) {
                return;
            }
            var type = btn.dataset.type;
            if (type === state.logType) {
                return;
            }
            state.logType = type;
            this.querySelectorAll(".log-type-tab").forEach(function (el) {
                el.classList.toggle("is-active", el === btn);
            });
            reloadTable(true);
        });

        // 关键字搜索
        document.getElementById("searchButton").addEventListener("click", function () {
            state.keyword = document.getElementById("keywordInput").value.trim();
            reloadTable(true);
        });
        document.getElementById("keywordInput").addEventListener("keydown", function (e) {
            if (e.key === "Enter") {
                state.keyword = this.value.trim();
                reloadTable(true);
            }
        });

        // 刷新
        document.getElementById("refreshButton").addEventListener("click", function () {
            reloadTable(false);
            loadStats();
        });

        // 高级搜索
        document.getElementById("advSearchButton").addEventListener("click", openAdvSearch);

        // 导出
        document.getElementById("exportButton").addEventListener("click", exportLogs);

        // 自动刷新开关
        form.on("switch(autoRefreshSwitch)", function (data) {
            if (data.elem.checked) {
                startAutoRefresh();
            } else {
                stopAutoRefresh();
            }
        });

        // 服务端排序
        table.on("sort(logTable)", function (obj) {
            if (obj.type) {
                state.sortField = obj.field;
                state.sortOrder = obj.type;
            } else {
                state.sortField = "";
                state.sortOrder = "";
            }
            reloadTable(true);
        });

        // 行点击 / 详情按钮
        table.on("row(logTable)", function (obj) {
            openDetail(obj.data.id);
        });
        table.on("tool(logTable)", function (obj) {
            if (obj.event === "detail") {
                openDetail(obj.data.id);
            }
        });
    }

    function startAutoRefresh() {
        stopAutoRefresh();
        autoRefreshTimer = setInterval(function () {
            reloadTable(false);
            loadStats();
        }, 10000);
        layer.msg("已开启自动刷新（每 10 秒）", {icon: 1, time: 1500});
    }

    function stopAutoRefresh() {
        if (autoRefreshTimer) {
            clearInterval(autoRefreshTimer);
            autoRefreshTimer = null;
        }
    }

    function openAdvSearch() {
        layer.open({
            type: 1,
            title: "高级搜索",
            area: ["520px", "auto"],
            shadeClose: false,
            content: AppUtils.getTemplateHtml("advSearchTemplate"),
            success: function (layero) {
                // 填充主机下拉
                var select = layero[0].querySelector("#advHostSelect");
                hostOptions.forEach(function (h) {
                    var opt = document.createElement("option");
                    opt.value = h.id;
                    opt.textContent = h.hostname ? (h.hostname + (h.ipv4 ? " / " + h.ipv4 : "")) : ("主机#" + h.id);
                    select.appendChild(opt);
                });

                // 回填已有条件
                form.val("advSearchForm", {
                    eventId: state.adv.eventId,
                    hostId: state.adv.hostId,
                    username: state.adv.username,
                    level: state.adv.level
                });
                form.render(null, "advSearchForm");

                // 时间范围选择器
                var rangeInput = layero[0].querySelector("#advTimeRange");
                if (state.adv.startTime && state.adv.endTime) {
                    rangeInput.value = state.adv.startTime + " - " + state.adv.endTime;
                }
                laydate.render({
                    elem: rangeInput,
                    type: "datetime",
                    range: " - ",
                    trigger: "click"
                });

                layero[0].querySelector("#advQueryButton").addEventListener("click", function () {
                    var field = form.val("advSearchForm");
                    state.adv.eventId = (field.eventId || "").trim();
                    state.adv.hostId = field.hostId || "";
                    state.adv.username = (field.username || "").trim();
                    state.adv.level = field.level || "";
                    var range = (rangeInput.value || "").trim();
                    if (range && range.indexOf(" - ") > -1) {
                        var parts = range.split(" - ");
                        state.adv.startTime = parts[0].trim();
                        state.adv.endTime = parts[1].trim();
                    } else {
                        state.adv.startTime = "";
                        state.adv.endTime = "";
                    }
                    renderActiveFilter();
                    reloadTable(true);
                    layer.closeAll();
                });

                layero[0].querySelector("#advResetButton").addEventListener("click", function () {
                    state.adv = {eventId: "", hostId: "", username: "", level: "", startTime: "", endTime: ""};
                    form.val("advSearchForm", {eventId: "", hostId: "", username: "", level: ""});
                    rangeInput.value = "";
                    form.render(null, "advSearchForm");
                    renderActiveFilter();
                    reloadTable(true);
                });
            }
        });
    }

    function renderActiveFilter() {
        var bar = document.getElementById("activeFilterBar");
        var chips = [];
        if (state.adv.eventId) {
            chips.push("EventID: " + state.adv.eventId);
        }
        if (state.adv.hostId) {
            var host = hostOptions.filter(function (h) { return String(h.id) === String(state.adv.hostId); })[0];
            chips.push("主机: " + (host && host.hostname ? host.hostname : "#" + state.adv.hostId));
        }
        if (state.adv.username) {
            chips.push("用户名: " + state.adv.username);
        }
        if (state.adv.level) {
            chips.push("级别: " + state.adv.level);
        }
        if (state.adv.startTime && state.adv.endTime) {
            chips.push("时间: " + state.adv.startTime + " ~ " + state.adv.endTime);
        }
        if (chips.length === 0) {
            bar.style.display = "none";
            bar.innerHTML = "";
            return;
        }
        bar.style.display = "flex";
        bar.innerHTML = '<span class="active-filter-label">高级筛选：</span>'
            + chips.map(function (c) { return '<span class="filter-chip">' + escapeHtml(c) + "</span>"; }).join("")
            + '<button type="button" class="layui-btn layui-btn-primary layui-btn-xs" id="clearAdvFilter">清除</button>';
        var clearBtn = document.getElementById("clearAdvFilter");
        if (clearBtn) {
            clearBtn.addEventListener("click", function () {
                state.adv = {eventId: "", hostId: "", username: "", level: "", startTime: "", endTime: ""};
                renderActiveFilter();
                reloadTable(true);
            });
        }
    }

    function openDetail(id) {
        if (detailLayerIndex !== null) {
            layer.close(detailLayerIndex);
            detailLayerIndex = null;
        }
        detailLayerIndex = layer.open({
            type: 1,
            title: "日志详情",
            offset: "r",
            anim: -1,
            shadeClose: true,
            area: ["640px", "100%"],
            content: AppUtils.getTemplateHtml("logDetailTemplate"),
            success: function (layero) {
                element.render("tab", "logDetailTab");
                AppRequest.request(API.detail + encodeURIComponent(id), {method: "GET"})
                    .then(function (res) {
                        fillDetail(layero[0], res.data || {});
                    })
                    .catch(function () {
                        layero[0].querySelector("#detailKvGrid").innerHTML = "<div class='detail-empty'>加载详情失败</div>";
                    });
            },
            end: function () {
                detailLayerIndex = null;
            }
        });
    }

    function fillDetail(root, d) {
        var rows = [
            ["ID", d.id],
            ["日志类型", d.logType],
            ["EventID", d.eventId],
            ["级别", d.level],
            ["事件时间", AppUtils.formatDateTime(d.eventTime)],
            ["主机", d.hostname ? d.hostname : ("#" + (d.hostId == null ? "-" : d.hostId))],
            ["主机IP", d.ipv4 || "-"],
            ["用户名", d.username || "-"],
            ["记录号", d.recordNumber],
            ["入库时间", AppUtils.formatDateTime(d.createTime)]
        ];
        root.querySelector("#detailKvGrid").innerHTML = rows.map(function (r) {
            return '<div class="detail-kv-item">'
                + '<span class="detail-kv-label">' + escapeHtml(r[0]) + "</span>"
                + '<span class="detail-kv-value">' + escapeHtml(r[1] == null || r[1] === "" ? "-" : r[1]) + "</span>"
                + "</div>";
        }).join("");

        root.querySelector("#detailMessage").textContent = d.message || "-";

        var rawXml = d.rawXml || "";
        root.querySelector("#detailRawXml").textContent = rawXml || "（无原始 XML）";

        var copyBtn = root.querySelector("#copyXmlButton");
        if (copyBtn) {
            copyBtn.addEventListener("click", function () {
                copyToClipboard(rawXml);
            });
        }
    }

    function copyToClipboard(text) {
        if (!text) {
            layer.msg("无可复制内容", {icon: 2});
            return;
        }
        if (navigator.clipboard && navigator.clipboard.writeText) {
            navigator.clipboard.writeText(text).then(function () {
                layer.msg("已复制", {icon: 1});
            }).catch(function () {
                fallbackCopy(text);
            });
        } else {
            fallbackCopy(text);
        }
    }

    function fallbackCopy(text) {
        var ta = document.createElement("textarea");
        ta.value = text;
        document.body.appendChild(ta);
        ta.select();
        try {
            document.execCommand("copy");
            layer.msg("已复制", {icon: 1});
        } catch (e) {
            layer.msg("复制失败", {icon: 2});
        }
        document.body.removeChild(ta);
    }

    function buildExportQuery() {
        var where = buildWhere();
        var params = Object.keys(where).map(function (key) {
            return encodeURIComponent(key) + "=" + encodeURIComponent(where[key]);
        });
        return params.length ? "?" + params.join("&") : "";
    }

    async function exportLogs() {
        var token = AppAuth.getToken();
        var url = API.export + buildExportQuery();
        var loading = layer.load(2);
        try {
            var response = await fetch(url, {
                method: "GET",
                headers: token ? {Authorization: "Bearer " + token} : {}
            });
            if (!response.ok) {
                if (response.status === 401 || response.status === 403) {
                    AppAuth.clearLogin();
                    AppAuth.redirectToLogin();
                    return;
                }
                layer.msg("导出失败", {icon: 2});
                return;
            }
            var blob = await response.blob();
            triggerDownload(blob, "event_logs_" + timestamp() + ".csv");
            layer.msg("导出成功", {icon: 1});
        } catch (e) {
            layer.msg(e.message || "导出失败", {icon: 2});
        } finally {
            layer.close(loading);
        }
    }

    function timestamp() {
        var now = new Date();
        function p(n) { return n < 10 ? "0" + n : String(n); }
        return now.getFullYear() + p(now.getMonth() + 1) + p(now.getDate())
            + p(now.getHours()) + p(now.getMinutes()) + p(now.getSeconds());
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
});
