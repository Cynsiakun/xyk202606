/**
 * 资产详情工具：
 *  - openAssetDetail：资产页面内，按记录 id 打开单类型详情弹窗；
 *  - openHostAssetTabs：主机管理页内，按 MAC 打开「账号 / 服务 / 进程 / APP」四 Tab 资产弹窗。
 *
 * <p>两者复用同一套「信息表 + 原始JSON切换 + 客户端分页表格」渲染逻辑。</p>
 */
window.AssetUtils = (function () {

    /** 璧勪骇绫诲瀷 鈫?涓枃鏍囩銆?*/
    var TYPE_LABELS = {
        account: "账号资产",
        service: "服务资产",
        process: "进程资产",
        app: "APP资产"
    };

    /** 涓绘満璧勪骇 Tab 椤哄簭銆?*/
    var TAB_TYPES = ["account", "service", "process", "app"];

    var ANALYZABLE_TYPES = {
        account: {
            endpointType: "account",
            kicker: "ACCOUNT RISK ANALYSIS",
            title: "账号资产详情",
            itemLabel: "账号对象",
            emptyText: "当前筛选条件下暂无账号",
            nameFields: ["name", "user", "username", "account"],
            fallbackName: "账号",
            idLabel: "SID",
            idField: "sid",
            parseErrorText: "AI返回格式异常：无法解析更新后的账号数据"
        },
        service: {
            endpointType: "service",
            kicker: "SERVICE RISK ANALYSIS",
            title: "服务资产详情",
            itemLabel: "服务对象",
            emptyText: "当前筛选条件下暂无服务",
            nameFields: ["name", "serviceName", "displayName"],
            fallbackName: "服务",
            idLabel: "PID",
            idField: "pid",
            parseErrorText: "AI返回格式异常：无法解析更新后的服务数据"
        },
        process: {
            endpointType: "process",
            kicker: "PROCESS RISK ANALYSIS",
            title: "进程资产详情",
            itemLabel: "进程对象",
            emptyText: "当前筛选条件下暂无进程",
            nameFields: ["name", "processName"],
            fallbackName: "进程",
            idLabel: "PID",
            idField: "pid",
            parseErrorText: "AI返回格式异常：无法解析更新后的进程数据"
        },
        app: {
            endpointType: "app",
            kicker: "APP RISK ANALYSIS",
            title: "APP资产详情",
            itemLabel: "APP对象",
            emptyText: "当前筛选条件下暂无APP",
            nameFields: ["name", "appName"],
            fallbackName: "APP",
            idLabel: "版本",
            idField: "version",
            parseErrorText: "AI返回格式异常：无法解析更新后的APP数据"
        }
    };

    /** 璇︽儏姣忛〉灞曠ず鐨勮祫浜ф潯鏁般€?*/
    var PAGE_SIZE = 50;
    var RISK_CARD_PAGE_SIZE = 25;

    // ============ 鍗曠被鍨嬭鎯呭脊绐楋紙璧勪骇椤甸潰鐢級 ============

    /**
     * 鎵撳紑璧勪骇璇︽儏寮圭獥锛堝崟绫诲瀷锛屾寜璁板綍 id 鎷夊彇锛夈€?     *
     * @param {object}  layer       layui.layer
     * @param {string}  assetType   account / service / process / app
     * @param {object}  row         琛屾暟鎹紝鑷冲皯鍚?id
     * @param {array}   columns     棰濆鏄剧ず鐨勫熀纭€瀛楁锛屽 [{label: "涓绘満鍚?, value: row.hostName}]
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
                        renderRecordInto(layero.find("#assetDetailWrap"), record, assetData, columns, assetType, true);
                    }
                });
            })
            .catch(function () {});
    }

    // ============ 涓绘満缁村害鍥?Tab 寮圭獥锛堜富鏈虹鐞嗛〉鐢級 ============

    /**
     * 鎵撳紑涓绘満璧勪骇寮圭獥锛氬洓涓?Tab锛堣处鍙?/ 鏈嶅姟 / 杩涚▼ / APP锛夛紝鎸?MAC 鎳掑姞杞姐€?     *
     * @param {object} layer layui.layer
     * @param {object} host  涓绘満琛屾暟鎹紝鑷冲皯鍚?macAddress
     */
    function openHostAssetTabs(layer, host) {
        var mac = host ? host.macAddress : "";
        if (!mac) {
            AppRequest.showMessage("该主机缺少MAC地址，无法查看资产", 2, 2000);
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
        pane.html('<div class="host-asset-state">加载中...</div>');
        AppRequest.request("/api/assets/host-latest?type=" + type + "&mac=" + encodeURIComponent(mac), {method: "GET"})
            .then(function (result) {
                var record = result.data;
                var assetData = parseAssetJson(record, true);
                if (assetData == null) {
                    pane.html('<div class="host-asset-state">加载中...</div>');
                    return;
                }
                pane.html('<div class="asset-detail-wrap"></div>');
                renderRecordInto(pane.find(".asset-detail-wrap"), record, assetData, [
                    {label: "主机名", value: record.hostName},
                    {label: "MAC地址", value: record.macAddress},
                    {label: "任务ID", value: record.taskId}
                ], type, false);
            })
            .catch(function () {
                pane.html('<div class="host-asset-state">加载中...</div>');
            });
    }

    // ============ 鍏辩敤娓叉煋 ============

    /**
     * 鎶婁竴鏉¤祫浜ц褰曟覆鏌撹繘瀹瑰櫒锛氫俊鎭〃 + 鍘熷JSON鍒囨崲 + 鍒嗛〉琛ㄦ牸銆?     */
    function renderRecordInto($wrap, record, assetData, columns, assetType, enableAnalysis) {
        var state = {
            record: record,
            assetData: assetData,
            columns: columns || [],
            assetType: assetType,
            enableAnalysis: !!enableAnalysis,
            filter: "ALL",
            riskPage: 1,
            error: ""
        };
        renderDetailState($wrap, state);
    }

    function renderDetailState($wrap, state) {
        var record = state.record;
        var assetData = state.assetData;
        var analyzableConfig = getAnalyzableConfig(state);
        if (analyzableConfig) {
            renderAccountDetailState($wrap, state, analyzableConfig);
            return;
        }

        var columns = state.columns || [];
        var keys = buildDisplayKeys(assetData, false);
        var total = assetData.length;

        var infoRows = columns.map(function (c) {
            return '<tr><td class="detail-label">' + c.label + '</td><td>' + formatCell(c.value) + '</td></tr>';
        }).join("");
        infoRows += '<tr><td class="detail-label">资产数量</td><td>' + (record.assetCount != null ? record.assetCount : total) + '</td></tr>';
        infoRows += '<tr><td class="detail-label">更新时间</td><td>'
            + (window.AppUtils ? AppUtils.formatDateTime(record.updatedAt) : (record.updatedAt || "-")) + '</td></tr>';

        var headerHtml = '<tr>' + keys.map(function (k) { return '<th>' + escapeHtml(displayKey(k)) + '</th>'; }).join("") + '</tr>';
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

    function renderAccountDetailState($wrap, state, config) {
        var record = state.record;
        var assetData = state.assetData;
        var filtered = filterAssetData(assetData, state.filter);
        var totalPages = Math.max(1, Math.ceil(filtered.length / RISK_CARD_PAGE_SIZE));
        state.riskPage = Math.min(Math.max(1, state.riskPage || 1), totalPages);
        var pageStart = (state.riskPage - 1) * RISK_CARD_PAGE_SIZE;
        var pageEnd = Math.min(pageStart + RISK_CARD_PAGE_SIZE, filtered.length);
        var pageItems = filtered.slice(pageStart, pageEnd);
        var counts = countRiskLevels(assetData);
        var rawJson = JSON.stringify(assetData, null, 2);

        $wrap.html(''
            + '<div class="account-ai-panel">'
            +   renderAccountHero(record, assetData, counts, config)
            +   renderAccountToolbar(state, counts)
            +   (state.error ? '<div class="account-ai-error">' + escapeHtml(state.error) + '</div>' : '')
            +   '<div class="account-card-list">' + renderAccountCards(pageItems, config, pageStart) + '</div>'
            +   renderRiskCardPager(filtered.length, pageStart, pageEnd, state.riskPage, totalPages)
            + '</div>'
            + '<div class="asset-detail-json" style="display:none;"><pre class="json-block">' + escapeHtml(rawJson) + '</pre></div>');

        bindJsonToggle($wrap);
        bindAccountAnalysis($wrap, state, config);
        bindRiskTabs($wrap, state);
        bindRiskCardPager($wrap, state);
    }

    function renderAccountHero(record, assetData, counts, config) {
        var topLevel = getTopRiskLevel(counts);
        return ''
            + '<div class="account-ai-hero">'
            +   '<div class="account-ai-title-block">'
            +     '<div class="account-ai-kicker">' + escapeHtml(config.kicker) + '</div>'
            +     '<div class="account-ai-title">' + escapeHtml(config.title) + '</div>'
            +     '<div class="account-ai-meta">'
            +       '<span>主机：' + formatCell(record.hostName) + '</span>'
            +       '<span>MAC：' + formatCell(record.macAddress) + '</span>'
            +       '<span>任务：' + formatCell(record.taskId) + '</span>'
            +     '</div>'
            +   '</div>'
            +   '<div class="account-ai-status">'
            +     '<span class="asset-risk-badge ' + riskClass(topLevel) + '">' + topLevel + '</span>'
            +     '<strong>' + assetData.length + '</strong>'
            +     '<span>' + escapeHtml(config.itemLabel) + '</span>'
            +   '</div>'
            + '</div>';
    }

    function renderAccountToolbar(state, counts) {
        var tabs = [
            {value: "ALL", label: "全部", count: state.assetData.length},
            {value: "HIGH", label: "高风险", count: counts.HIGH},
            {value: "MEDIUM", label: "中风险", count: counts.MEDIUM},
            {value: "LOW", label: "低风险", count: counts.LOW},
            {value: "NONE", label: "无风险", count: counts.NONE}
        ];
        return ''
            + '<div class="account-ai-toolbar">'
            +   '<div class="account-risk-metrics">'
            +     riskMetric("HIGH", "高风险", counts.HIGH)
            +     riskMetric("MEDIUM", "中风险", counts.MEDIUM)
            +     riskMetric("LOW", "低风险", counts.LOW)
            +     riskMetric("NONE", "无风险", counts.NONE)
            +   '</div>'
            +   '<div class="account-ai-actions">'
            +     '<button type="button" class="layui-btn layui-btn-sm asset-ai-analysis">AI辅助分析</button>'
            +     '<button type="button" class="layui-btn layui-btn-primary layui-btn-sm asset-json-toggle">原始JSON</button>'
            +     '<span class="asset-analysis-state">分析中...</span>'
            +   '</div>'
            + '</div>'
            + '<div class="account-risk-tabs">' + tabs.map(function (tab) {
                var active = tab.value === state.filter ? ' is-active' : '';
                return '<button type="button" class="account-risk-tab' + active + '" data-risk="' + tab.value + '">' + tab.label + '<span>' + tab.count + '</span></button>';
            }).join("") + '</div>';
    }

    function renderAccountCards(items, config, offset) {
        if (!items.length) {
            return '<div class="account-empty">' + escapeHtml(config.emptyText) + '</div>';
        }
        offset = offset || 0;
        return items.map(function (item, index) {
            var level = normalizeRiskLevel(item.risk_level);
            var name = pickFirstText(item, config.nameFields) || (config.fallbackName + " #" + (offset + index + 1));
            var tags = Array.isArray(item.risk_tags) ? item.risk_tags : [];
            var suggestions = Array.isArray(item.suggestions) ? item.suggestions : [];
            return ''
                + '<div class="account-risk-card risk-' + level.toLowerCase() + '">'
                +   '<div class="account-card-head">'
                +     '<div>'
                +       '<div class="account-name">' + escapeHtml(name) + '</div>'
                +       '<div class="account-subline">' + escapeHtml(config.idLabel) + '：' + formatCell(item[config.idField]) + '</div>'
                +     '</div>'
                +     '<div class="account-card-score">'
                +       '<span class="asset-risk-badge ' + riskClass(level) + '">' + level + '</span>'
                +       '<strong>' + formatCell(item.risk_score) + '</strong>'
                +     '</div>'
                +   '</div>'
                +   '<div class="account-tag-row">' + (tags.length ? tags.map(function (tag) { return '<span>' + escapeHtml(tag) + '</span>'; }).join("") : '<span class="muted">暂无风险标签</span>') + '</div>'
                +   '<div class="account-result">' + formatCell(item.result || '尚未进行AI分析') + '</div>'
                +   '<div class="account-suggestions">'
                +     '<div class="account-section-title">整改建议</div>'
                +     (suggestions.length ? '<ul>' + suggestions.map(function (item) { return '<li>' + escapeHtml(item) + '</li>'; }).join("") + '</ul>' : '<p class="muted">暂无建议</p>')
                +   '</div>'
                +   '<details class="account-raw-fields">'
                +     '<summary>查看原始字段</summary>'
                +     '<div class="account-field-grid">' + renderAccountRawFields(item) + '</div>'
                +   '</details>'
                + '</div>';
        }).join("");
    }

    function renderRiskCardPager(total, start, end, currentPage, totalPages) {
        if (total <= RISK_CARD_PAGE_SIZE) {
            return "";
        }
        var prevDisabled = currentPage <= 1 ? ' layui-btn-disabled' : '';
        var nextDisabled = currentPage >= totalPages ? ' layui-btn-disabled' : '';
        return ''
            + '<div class="asset-pager account-card-pager">'
            +   '<span class="asset-pager-info">第 ' + (total === 0 ? 0 : start + 1) + ' - ' + end + ' 条 / 共 ' + total + ' 条，第 ' + currentPage + ' / ' + totalPages + ' 页</span>'
            +   '<div class="asset-pager-controls">'
            +     '<button type="button" class="layui-btn layui-btn-xs layui-btn-primary account-card-prev' + prevDisabled + '">上一页</button>'
            +     '<button type="button" class="layui-btn layui-btn-xs layui-btn-primary account-card-next' + nextDisabled + '">下一页</button>'
            +   '</div>'
            + '</div>';
    }

    function renderAccountRawFields(account) {
        var skip = {risk_level: true, risk_score: true, risk_tags: true, result: true, suggestions: true};
        return Object.keys(account || {}).filter(function (key) { return !skip[key]; }).map(function (key) {
            return '<div class="account-field-item"><span>' + escapeHtml(key) + '</span><strong>' + formatCell(account[key]) + '</strong></div>';
        }).join("");
    }
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
                    return '<td class="' + cellClass(k) + '">' + formatRiskCell(k, rowObj[k]) + '</td>';
                }).join("") + '</tr>';
            }
            tbody.html(bodyHtml || '<tr><td colspan="' + keys.length + '">暂无数据</td></tr>');
            if (scroll.length) {
                scroll[0].scrollTop = 0;
            }
            renderPager(start, end);
        }

        function renderPager(start, end) {
            var info = '<span class="asset-pager-info">第 ' + (total === 0 ? 0 : start + 1) + ' - ' + end + ' 条 / 共 ' + total + ' 条，第 ' + currentPage + ' / ' + totalPages + ' 页</span>';
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


    function renderAccountAnalysisTools(assetData, error, activeFilter) {
        var counts = countRiskLevels(assetData);
        var tabs = [
            {value: "ALL", label: "全部", count: assetData.length},
            {value: "HIGH", label: "高风险", count: counts.HIGH},
            {value: "MEDIUM", label: "中风险", count: counts.MEDIUM},
            {value: "LOW", label: "低风险", count: counts.LOW},
            {value: "NONE", label: "无风险", count: counts.NONE}
        ];
        var summary = ''
            + '<div class="asset-risk-summary">'
            + riskSummaryItem("HIGH", "高风险", counts.HIGH)
            + riskSummaryItem("MEDIUM", "中风险", counts.MEDIUM)
            + riskSummaryItem("LOW", "低风险", counts.LOW)
            + riskSummaryItem("NONE", "无风险", counts.NONE)
            + '</div>';
        var tabHtml = '<div class="asset-risk-tabs">' + tabs.map(function (tab) {
            var active = tab.value === activeFilter ? ' is-active' : '';
            return '<button type="button" class="layui-btn layui-btn-xs layui-btn-primary asset-risk-tab' + active + '" data-risk="' + tab.value + '">'
                + tab.label + ' (' + tab.count + ')</button>';
        }).join("") + '</div>';
        var errorHtml = error ? '<div class="asset-analysis-error">' + escapeHtml(error) + '</div>' : '';
        return summary + tabHtml + errorHtml;
    }

    function riskSummaryItem(level, label, count) {
        return '<span class="asset-risk-badge ' + riskClass(level) + '">' + label + ' ' + count + '</span>';
    }

    function bindAccountAnalysis($wrap, state, config) {
        $wrap.find(".asset-ai-analysis").on("click", async function () {
            var button = this;
            var stateText = $wrap.find(".asset-analysis-state");
            button.disabled = true;
            button.classList.add("layui-btn-disabled");
            stateText.show();
            try {
                var result = await AppRequest.request("/api/assets/" + config.endpointType + "/" + state.record.id + "/ai-analysis", {
                    method: "POST"
                }, {showErrorMessage: false});
                var nextRecord = result.data;
                var nextAssetData = parseAssetJson(nextRecord);
                if (nextAssetData == null) {
                    state.error = config.parseErrorText;
                    renderDetailState($wrap, state);
                    return;
                }
                state.record = nextRecord;
                state.assetData = nextAssetData;
                state.filter = "ALL";
                state.riskPage = 1;
                state.error = "";
                renderDetailState($wrap, state);
                AppRequest.showMessage("AI分析完成", 1, 1600);
            } catch (error) {
                state.error = error && error.message ? error.message : "AI分析失败";
                renderDetailState($wrap, state);
            } finally {
                stateText.hide();
            }
        });
    }

    function bindRiskTabs($wrap, state) {
        $wrap.find(".account-risk-tab, .asset-risk-tab").on("click", function () {
            state.filter = this.getAttribute("data-risk") || "ALL";
            state.riskPage = 1;
            state.error = "";
            renderDetailState($wrap, state);
        });
    }

    function bindRiskCardPager($wrap, state) {
        $wrap.find(".account-card-prev").on("click", function () {
            if (state.riskPage > 1) {
                state.riskPage -= 1;
                renderDetailState($wrap, state);
            }
        });
        $wrap.find(".account-card-next").on("click", function () {
            state.riskPage += 1;
            renderDetailState($wrap, state);
        });
    }

    function riskMetric(level, label, count) {
        return '<div class="account-risk-metric metric-' + level.toLowerCase() + '"><span>' + label + '</span><strong>' + count + '</strong></div>';
    }

    function getTopRiskLevel(counts) {
        if (counts.HIGH > 0) return "HIGH";
        if (counts.MEDIUM > 0) return "MEDIUM";
        if (counts.LOW > 0) return "LOW";
        return "NONE";
    }
    function countRiskLevels(assetData) {
        var counts = {HIGH: 0, MEDIUM: 0, LOW: 0, NONE: 0};
        assetData.forEach(function (item) {
            var level = normalizeRiskLevel(item.risk_level);
            counts[level] += 1;
        });
        return counts;
    }

    function filterAssetData(assetData, riskLevel) {
        if (!riskLevel || riskLevel === "ALL") {
            return assetData;
        }
        return assetData.filter(function (item) {
            return normalizeRiskLevel(item.risk_level) === riskLevel;
        });
    }

    function buildDisplayKeys(assetData, isAccount) {
        var seen = {};
        var keys = [];
        assetData.forEach(function (row) {
            Object.keys(row || {}).forEach(function (key) {
                if (!seen[key]) {
                    seen[key] = true;
                    keys.push(key);
                }
            });
        });
        if (!isAccount) {
            return keys;
        }
        ["risk_level", "risk_score", "risk_tags", "result", "suggestions"].forEach(function (key) {
            if (!seen[key]) {
                keys.push(key);
            }
        });
        return keys;
    }

    function displayKey(key) {
        var labels = {
            risk_level: "风险等级",
            risk_score: "风险评分",
            risk_tags: "风险标签",
            result: "分析结果",
            suggestions: "整改建议"
        };
        return labels[key] || key;
    }

    function formatRiskCell(key, val) {
        if (key === "risk_level") {
            var level = normalizeRiskLevel(val);
            return '<span class="asset-risk-badge ' + riskClass(level) + '">' + level + '</span>';
        }
        if (key === "risk_tags" || key === "suggestions") {
            if (Array.isArray(val)) {
                return val.length ? escapeHtml(val.join("；")) : "-";
            }
        }
        return formatCell(val);
    }

    function cellClass(key) {
        if (key === "result") return "asset-risk-result";
        if (key === "suggestions") return "asset-risk-suggestions";
        return "";
    }

    function normalizeRiskLevel(value) {
        var level = String(value || "NONE").toUpperCase();
        return level === "HIGH" || level === "MEDIUM" || level === "LOW" || level === "NONE" ? level : "NONE";
    }

    function riskClass(level) {
        switch (normalizeRiskLevel(level)) {
            case "HIGH": return "asset-risk-high";
            case "MEDIUM": return "asset-risk-medium";
            case "LOW": return "asset-risk-low";
            default: return "asset-risk-none";
        }
    }

    function getAnalyzableConfig(state) {
        if (!state || !state.enableAnalysis || !state.record || !state.record.id || !state.record.assetJson) {
            return null;
        }
        return ANALYZABLE_TYPES[state.assetType] || null;
    }

    function pickFirstText(obj, fields) {
        for (var i = 0; i < fields.length; i++) {
            var value = obj ? obj[fields[i]] : null;
            if (value !== null && value !== undefined && value !== "") {
                return String(value);
            }
        }
        return "";
    }
    // ============ 宸ュ叿 ============

    /**
     * 瑙ｆ瀽璁板綍閲岀殑 assetJson锛岃繑鍥炴暟缁勶紱鏃犳暟鎹椂鎸?silent 鍐冲畾鏄惁鎻愮ず骞惰繑鍥?null銆?     */
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









