/**
 * 资产详情工具：
 *  - openAssetDetail：资产页面内，按记录 id 打开单类型详情弹窗；
 *  - openHostAssetTabs：主机管理页内，按 MAC 打开「账号 / 服务 / 进程 / APP」四 Tab 资产弹窗。
 *
 * <p>两者复用同一套「信息表 + 原始JSON切换 + 客户端分页表格」渲染逻辑。</p>
 */
window.AssetUtils = (function () {

    /** 资产类型 → 中文标签。 */
    var TYPE_LABELS = {
        account: "账号资产",
        service: "服务资产",
        process: "进程资产",
        app: "APP资产"
    };

    /** 主机资产 Tab 顺序。 */
    var TAB_TYPES = ["account", "service", "process", "app"];

    /** 详情每页展示的资产条数。 */
    var PAGE_SIZE = 50;

    // ============ 单类型详情弹窗（资产页面用） ============

    /**
     * 打开资产详情弹窗（单类型，按记录 id 拉取）。
     *
     * @param {object}  layer       layui.layer
     * @param {string}  assetType   account / service / process / app
     * @param {object}  row         行数据，至少含 id
     * @param {array}   columns     额外显示的基础字段，如 [{label: "主机名", value: row.hostName}]
     */
    function openAssetDetail(layer, assetType, row, columns) {
        var typeLabel = TYPE_LABELS[assetType] || assetType;

        AppRequest.request("/api/assets/" + assetType + "/" + row.id, {method: "GET"})
            .then(function (result) {
                var record = result.data;
                var assetData = parseAssetJson(record);
                if (assetData == null) {
                    return;
                }

                var dialog = computeDialogSize(820, 600);
                layer.open({
                    type: 1,
                    title: typeLabel + " 详情（共 " + assetData.length + " 条）",
                    area: [dialog.width + "px", dialog.height + "px"],
                    content: '<div class="asset-detail-wrap" id="assetDetailWrap"></div>',
                    success: function (layero) {
                        renderRecordInto(layero.find("#assetDetailWrap"), record, assetData, columns);
                    }
                });
            })
            .catch(function () {});
    }

    // ============ 主机维度四 Tab 弹窗（主机管理页用） ============

    /**
     * 打开主机资产弹窗：四个 Tab（账号 / 服务 / 进程 / APP），按 MAC 懒加载。
     *
     * @param {object} layer layui.layer
     * @param {object} host  主机行数据，至少含 macAddress
     */
    function openHostAssetTabs(layer, host) {
        var mac = host ? host.macAddress : "";
        if (!mac) {
            AppRequest.showMessage("该主机缺少 MAC 地址，无法查看资产", 2, 2000);
            return;
        }
        var title = "主机资产 · " + (host.hostname || mac);

        var headHtml = TAB_TYPES.map(function (type, i) {
            return '<li class="host-asset-tab' + (i === 0 ? " is-active" : "") + '" data-type="' + type + '">'
                + TYPE_LABELS[type] + '</li>';
        }).join("");
        var bodyHtml = TAB_TYPES.map(function (type, i) {
            return '<div class="host-asset-pane' + (i === 0 ? " is-active" : "") + '" data-type="' + type + '"></div>';
        }).join("");

        var contentHtml = ''
            + '<div class="host-asset-tabs">'
            +   '<ul class="host-asset-tab-head">' + headHtml + '</ul>'
            +   '<div class="host-asset-tab-body">' + bodyHtml + '</div>'
            + '</div>';

        var dialog = computeDialogSize(860, 620);
        layer.open({
            type: 1,
            title: title,
            area: [dialog.width + "px", dialog.height + "px"],
            content: contentHtml,
            success: function (layero) {
                var loaded = {};

                function activate(type) {
                    layero.find(".host-asset-tab").removeClass("is-active");
                    layero.find('.host-asset-tab[data-type="' + type + '"]').addClass("is-active");
                    layero.find(".host-asset-pane").removeClass("is-active");
                    var pane = layero.find('.host-asset-pane[data-type="' + type + '"]');
                    pane.addClass("is-active");
                    if (!loaded[type]) {
                        loaded[type] = true;
                        loadHostAssetPane(pane, type, mac);
                    }
                }

                layero.find(".host-asset-tab").on("click", function () {
                    activate(this.getAttribute("data-type"));
                });

                activate(TAB_TYPES[0]);
            }
        });
    }

    function loadHostAssetPane(pane, type, mac) {
        pane.html('<div class="host-asset-state">加载中…</div>');
        AppRequest.request("/api/assets/host-latest?type=" + type + "&mac=" + encodeURIComponent(mac), {method: "GET"})
            .then(function (result) {
                var record = result.data;
                var assetData = parseAssetJson(record, true);
                if (assetData == null) {
                    pane.html('<div class="host-asset-state">暂无' + (TYPE_LABELS[type] || "") + '数据</div>');
                    return;
                }
                pane.html('<div class="asset-detail-wrap"></div>');
                renderRecordInto(pane.find(".asset-detail-wrap"), record, assetData, [
                    {label: "主机名", value: record.hostName},
                    {label: "MAC地址", value: record.macAddress},
                    {label: "任务ID", value: record.taskId}
                ]);
            })
            .catch(function () {
                pane.html('<div class="host-asset-state">加载失败</div>');
            });
    }

    // ============ 共用渲染 ============

    /**
     * 把一条资产记录渲染进容器：信息表 + 原始JSON切换 + 分页表格。
     */
    function renderRecordInto($wrap, record, assetData, columns) {
        var keys = Object.keys(assetData[0]);
        var total = assetData.length;

        var infoRows = (columns || []).map(function (c) {
            return '<tr><td class="detail-label">' + c.label + '</td><td>' + formatCell(c.value) + '</td></tr>';
        }).join("");
        infoRows += '<tr><td class="detail-label">资产数量</td><td>' + (record.assetCount != null ? record.assetCount : total) + '</td></tr>';
        infoRows += '<tr><td class="detail-label">更新时间</td><td>'
            + (window.AppUtils ? AppUtils.formatDateTime(record.updatedAt) : (record.updatedAt || "-")) + '</td></tr>';

        var headerHtml = '<tr>' + keys.map(function (k) { return '<th>' + escapeHtml(k) + '</th>'; }).join("") + '</tr>';
        var rawJson = JSON.stringify(assetData, null, 2);

        $wrap.html(''
            + '<div class="asset-detail-info"><table class="layui-table asset-info-table"><tbody>' + infoRows + '</tbody></table></div>'
            + '<div class="asset-detail-actions"><button type="button" class="layui-btn layui-btn-xs layui-btn-primary asset-json-toggle">展开原始JSON</button></div>'
            + '<div class="asset-detail-array">'
            +   '<div class="asset-table-scroll"><table class="layui-table asset-data-table"><thead>' + headerHtml + '</thead><tbody class="asset-tbody"></tbody></table></div>'
            +   '<div class="asset-pager"></div>'
            + '</div>'
            + '<div class="asset-detail-json" style="display:none;"><pre class="json-block">' + escapeHtml(rawJson) + '</pre></div>');

        bindPagedTable($wrap, assetData, keys);
        bindJsonToggle($wrap);
    }

    /**
     * 客户端分页：每页 PAGE_SIZE 条，切页仅重绘表体，避免一次性渲染海量 DOM。
     * 所有选择器都限定在 $wrap 内，支持同页多个实例（如四 Tab）。
     */
    function bindPagedTable($wrap, assetData, keys) {
        var total = assetData.length;
        var totalPages = Math.max(1, Math.ceil(total / PAGE_SIZE));
        var currentPage = 1;

        var tbody = $wrap.find(".asset-tbody");
        var pager = $wrap.find(".asset-pager");
        var scroll = $wrap.find(".asset-table-scroll");

        function renderPage(page) {
            currentPage = Math.min(Math.max(1, page), totalPages);
            var start = (currentPage - 1) * PAGE_SIZE;
            var end = Math.min(start + PAGE_SIZE, total);

            var bodyHtml = "";
            for (var i = start; i < end; i++) {
                var rowObj = assetData[i];
                bodyHtml += '<tr>' + keys.map(function (k) {
                    return '<td>' + formatCell(rowObj[k]) + '</td>';
                }).join("") + '</tr>';
            }
            tbody.html(bodyHtml);
            if (scroll.length) {
                scroll[0].scrollTop = 0;
            }
            renderPager(start, end);
        }

        function renderPager(start, end) {
            var info = '<span class="asset-pager-info">第 ' + (start + 1) + ' - ' + end + ' 条 / 共 ' + total + ' 条，第 ' + currentPage + ' / ' + totalPages + ' 页</span>';
            var prevDisabled = currentPage <= 1 ? ' layui-btn-disabled' : '';
            var nextDisabled = currentPage >= totalPages ? ' layui-btn-disabled' : '';
            var controls = ''
                + '<div class="asset-pager-controls">'
                +   '<button type="button" class="layui-btn layui-btn-xs layui-btn-primary asset-pager-prev' + prevDisabled + '">上一页</button>'
                +   '<button type="button" class="layui-btn layui-btn-xs layui-btn-primary asset-pager-next' + nextDisabled + '">下一页</button>'
                + '</div>';
            pager.html(info + controls);

            pager.find(".asset-pager-prev").off("click").on("click", function () {
                if (currentPage > 1) renderPage(currentPage - 1);
            });
            pager.find(".asset-pager-next").off("click").on("click", function () {
                if (currentPage < totalPages) renderPage(currentPage + 1);
            });
        }

        renderPage(1);
    }

    function bindJsonToggle($wrap) {
        var toggled = false;
        $wrap.find(".asset-json-toggle").on("click", function () {
            toggled = !toggled;
            if (toggled) {
                $wrap.find(".asset-detail-json").show();
                $wrap.find(".asset-detail-array").hide();
                $wrap.find(".asset-json-toggle").text("收起原始JSON");
            } else {
                $wrap.find(".asset-detail-json").hide();
                $wrap.find(".asset-detail-array").show();
                $wrap.find(".asset-json-toggle").text("展开原始JSON");
            }
        });
    }

    // ============ 工具 ============

    /**
     * 解析记录里的 assetJson，返回数组；无数据时按 silent 决定是否提示并返回 null。
     */
    function parseAssetJson(record, silent) {
        if (!record || !record.assetJson) {
            if (!silent) AppRequest.showMessage("无资产数据", 2, 2000);
            return null;
        }
        var data;
        try {
            data = JSON.parse(record.assetJson);
        } catch (e) {
            if (!silent) AppRequest.showMessage("资产JSON解析失败", 2, 2000);
            return null;
        }
        if (!Array.isArray(data) || data.length === 0) {
            if (!silent) AppRequest.showMessage("资产数组为空", 2, 2000);
            return null;
        }
        return data;
    }

    function computeDialogSize(maxWidth, maxHeight) {
        var vh = window.innerHeight || 600;
        var vw = window.innerWidth || 800;
        return {
            width: Math.min(maxWidth, vw - 40),
            height: Math.min(maxHeight, vh - 40)
        };
    }

    function formatCell(val) {
        if (val === null || val === undefined || val === "") return "-";
        if (typeof val === "object") return escapeHtml(JSON.stringify(val));
        return escapeHtml(String(val));
    }

    function escapeHtml(text) {
        return String(text)
            .replace(/&/g, "&amp;")
            .replace(/</g, "&lt;")
            .replace(/>/g, "&gt;")
            .replace(/"/g, "&quot;")
            .replace(/'/g, "&#39;");
    }

    return {
        openAssetDetail: openAssetDetail,
        openHostAssetTabs: openHostAssetTabs,
        TYPE_LABELS: TYPE_LABELS
    };
})();
