layui.use(["table", "layer", "element"], function () {
    var table = layui.table;
    var layer = layui.layer;
    var element = layui.element;

    var TABLE_ID = "logTable";
    var API = {
        hosts: "/api/host-log/hosts",
        list: "/api/host-log/list",
        detail: "/api/host-log/detail/"
    };

    var state = {
        hostId: null,
        hostName: "",
        hostIp: "",
        logType: "Security",
        keyword: "",
        sortField: "",
        sortOrder: ""
    };
    var hostOptions = [];
    var tableRendered = false;
    var detailLayerIndex = null;

    init();

    function init() {
        bindEvents();
        loadHosts();
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

    function loadHosts() {
        AppRequest.request(API.hosts, {method: "GET"}, {showErrorMessage: false})
            .then(function (res) {
                hostOptions = res.data || [];
                document.getElementById("hostCount").textContent = hostOptions.length + " 台";
                renderHostList(hostOptions);
                if (hostOptions.length > 0) {
                    selectHost(hostOptions[0]);
                }
            })
            .catch(function () {
                hostOptions = [];
                document.getElementById("hostList").innerHTML = '<div class="host-empty">主机加载失败</div>';
            });
    }

    function renderHostList(list) {
        var container = document.getElementById("hostList");
        if (!list || list.length === 0) {
            container.innerHTML = '<div class="host-empty">无匹配主机</div>';
            return;
        }
        container.innerHTML = list.map(function (h) {
            var name = h.hostname || ("主机#" + h.id);
            var initial = (name.charAt(0) || "#").toUpperCase();
            var active = String(h.id) === String(state.hostId) ? " is-active" : "";
            return '<div class="host-item' + active + '" data-id="' + h.id + '">'
                + '<div class="host-item-avatar">' + escapeHtml(initial) + "</div>"
                + '<div class="host-item-info">'
                + '<span class="host-item-name" title="' + escapeHtml(name) + '">' + escapeHtml(name) + "</span>"
                + '<span class="host-item-ip">' + escapeHtml(h.ipv4 || "-") + "</span>"
                + "</div>"
                + "</div>";
        }).join("");

        container.querySelectorAll(".host-item").forEach(function (el) {
            el.addEventListener("click", function () {
                var id = this.dataset.id;
                var host = hostOptions.filter(function (h) { return String(h.id) === String(id); })[0];
                if (host) {
                    selectHost(host);
                }
            });
        });
    }

    function selectHost(host) {
        if (String(host.id) === String(state.hostId)) {
            return;
        }
        state.hostId = host.id;
        state.hostName = host.hostname || ("主机#" + host.id);
        state.hostIp = host.ipv4 || "";

        document.querySelectorAll("#hostList .host-item").forEach(function (el) {
            el.classList.toggle("is-active", String(el.dataset.id) === String(host.id));
        });

        var tag = document.getElementById("currentHostTag");
        tag.innerHTML = '<i class="layui-icon layui-icon-component"></i>'
            + "<strong>" + escapeHtml(state.hostName) + "</strong>"
            + (state.hostIp ? '<span class="muted">' + escapeHtml(state.hostIp) + "</span>" : "");

        if (!tableRendered) {
            renderTable();
            tableRendered = true;
        } else {
            reloadTable(true);
        }
    }

    function buildWhere() {
        var where = {hostId: state.hostId, logType: state.logType};
        if (state.keyword) {
            where.keyword = state.keyword;
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
            text: {none: "该主机暂无此类型日志"},
            cols: [[
                {field: "id", title: "ID", width: 100, sort: true},
                {field: "logType", title: "类型", width: 110, templet: "#logTypeTpl"},
                {field: "eventId", title: "EventID", width: 110, sort: true},
                {field: "level", title: "级别", width: 120, templet: "#levelTpl"},
                {field: "eventTime", title: "事件时间", width: 180, sort: true, templet: function (d) {
                    return AppUtils.formatDateTime(d.eventTime);
                }},
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
        if (!tableRendered) {
            return;
        }
        table.reload(TABLE_ID, {
            where: buildWhere(),
            page: resetPage === false ? undefined : {curr: 1}
        });
    }

    function bindEvents() {
        // 日志类型切换 → 自动刷新
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

        // 主机搜索（仅过滤左侧已加载的主机列表，非日志数据）
        document.getElementById("hostSearchInput").addEventListener("input", function () {
            var kw = this.value.trim().toLowerCase();
            var filtered = !kw ? hostOptions : hostOptions.filter(function (h) {
                return (h.hostname || "").toLowerCase().indexOf(kw) > -1
                    || (h.ipv4 || "").toLowerCase().indexOf(kw) > -1;
            });
            renderHostList(filtered);
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

        // 行 / 详情
        table.on("row(logTable)", function (obj) {
            openDetail(obj.data.id);
        });
        table.on("tool(logTable)", function (obj) {
            if (obj.event === "detail") {
                openDetail(obj.data.id);
            }
        });
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
});
