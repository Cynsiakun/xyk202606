layui.use(["layer"], function () {
    var layer = layui.layer;
    var currentRange = "24h";
    var refreshMode = "1m";
    var refreshTimer = null;
    var hiddenTrendSeries = {};
    var loadingIndex = null;
    var currentData = null;
    var animationState = {};

    init();

    function init() {
        bindEvents();
        loadDashboard();
        applyRefreshMode(refreshMode);
    }

    function bindEvents() {
        document.getElementById("rangeTabs").addEventListener("click", function (event) {
            var tab = event.target.closest(".range-tab");
            if (!tab) return;
            document.querySelectorAll(".range-tab").forEach(function (item) {
                item.classList.toggle("is-active", item === tab);
            });
            currentRange = tab.dataset.range || "24h";
            loadDashboard();
        });

        document.getElementById("refreshTabs").addEventListener("click", function (event) {
            var tab = event.target.closest(".refresh-tab");
            if (!tab) return;
            document.querySelectorAll(".refresh-tab").forEach(function (item) {
                item.classList.toggle("is-active", item === tab);
            });
            refreshMode = tab.dataset.refresh || "off";
            applyRefreshMode(refreshMode);
        });

        document.getElementById("customStartDate").addEventListener("change", handleCustomRange);
        document.getElementById("customEndDate").addEventListener("change", handleCustomRange);

        document.getElementById("exportReportButton").addEventListener("click", function () {
            layer.msg("导出接口已预留，当前先展示真实运营数据。");
        });
    }

    function handleCustomRange() {
        var start = getCustomStart();
        var end = getCustomEnd();
        if (!start || !end) return;
        currentRange = "custom";
        document.querySelectorAll(".range-tab").forEach(function (item) {
            item.classList.remove("is-active");
        });
        loadDashboard();
    }

    function applyRefreshMode(mode) {
        if (refreshTimer) {
            window.clearInterval(refreshTimer);
            refreshTimer = null;
        }
        var msMap = {
            "1m": 60000,
            "5m": 300000,
            "15m": 900000
        };
        if (msMap[mode]) {
            refreshTimer = window.setInterval(loadDashboard, msMap[mode]);
        }
    }

    async function loadDashboard() {
        setLoading(true);
        try {
            var query = buildQuery();
            var result = await AppRequest.request("/api/vuln-ops-dashboard/overview" + query, {
                method: "GET"
            });
            currentData = normalizeDashboardData(result.data || {});
            renderDashboard(currentData);
        } catch (error) {
            if (!currentData) {
                renderEmptyState();
            }
        } finally {
            setLoading(false);
        }
    }

    function setLoading(loading) {
        if (loading) {
            if (loadingIndex == null) {
                loadingIndex = layer.load(1, {shade: [0.08, "#fff"]});
            }
            return;
        }
        if (loadingIndex != null) {
            layer.close(loadingIndex);
            loadingIndex = null;
        }
    }

    function buildQuery() {
        var params = ["range=" + encodeURIComponent(currentRange)];
        if (currentRange === "custom") {
            var start = getCustomStart();
            var end = getCustomEnd();
            if (start) params.push("startDate=" + encodeURIComponent(start));
            if (end) params.push("endDate=" + encodeURIComponent(end));
        }
        return "?" + params.join("&");
    }

    function normalizeDashboardData(data) {
        data.kpis = data.kpis || {};
        data.trend = data.trend || {points: [], meta: "-"};
        data.highRisk = Array.isArray(data.highRisk) ? data.highRisk : [];
        data.sla = data.sla || {averageDays: 0, targetDays: 2, maxDays: 16, buckets: []};
        data.verifyTrend = data.verifyTrend || {points: []};
        data.clientTrend = data.clientTrend || {points: [], offlineHosts: []};
        return data;
    }

    function renderDashboard(data) {
        renderKpis(data.kpis);
        renderTrendLegend();
        renderTrendChart(data.trend);
        renderHighRisk(data.highRisk);
        renderSlaDistribution(data.sla);
        renderVerifyChart(data.verifyTrend);
        renderClientChart(data.clientTrend);
    }

    function renderEmptyState() {
        renderDashboard({
            kpis: {
                total: {count: "0", trendText: "-", trendUp: true},
                toFix: {count: "0", highRiskRatio: 0},
                avgFixDays: {count: "0.0天", progress: 0, gapText: "暂无数据"},
                verifyRate: {count: "0", progress: 0, gapText: "暂无数据"},
                clientOnline: {rate: 0, online: 0, offline: 0, total: 0}
            },
            trend: {points: [], meta: "-"},
            highRisk: [],
            sla: {averageDays: 0, targetDays: 2, maxDays: 16, buckets: []},
            verifyTrend: {points: []},
            clientTrend: {points: [], offlineHosts: []}
        });
    }

    function renderKpis(kpis) {
        var total = kpis.total || {};
        var toFix = kpis.toFix || {};
        var avgFixDays = kpis.avgFixDays || {};
        var verifyRate = kpis.verifyRate || {};
        var clientOnline = kpis.clientOnline || {};

        var html = [
            kpiCard("漏洞总量", total.count || "0", total.trendText || "-", "#3B82F6", null, total.trendUp),
            kpiCard("待修复漏洞", toFix.count || "0", "高危占比 " + (toFix.highRiskRatio || 0) + "%", "#EF4444", null),
            kpiCard("平均修复时效", avgFixDays.count || "0.0天", avgFixDays.gapText || "-", "#F59E0B", {
                progress: avgFixDays.progress || 0,
                color: "#F59E0B",
                display: avgFixDays.count || "0.0天"
            }),
            kpiCard("验证成功率", (verifyRate.count || "0") + "%", verifyRate.gapText || "-", "#10B981", {
                progress: verifyRate.progress || 0,
                color: "#10B981",
                display: (verifyRate.count || "0") + "%"
            }),
            clientKpiCard(clientOnline, "#3B82F6")
        ];
        document.getElementById("kpiGrid").innerHTML = html.join("");
        animateNumbers();
    }

    function kpiCard(label, value, caption, color, ring, trendUp) {
        var ringHtml = "";
        if (ring) {
            ringHtml = '<div class="kpi-ring" style="--ring-progress:' + escapeAttr(String(ring.progress || 0))
                + ';--ring-color:' + escapeAttr(ring.color) + ';">'
                + escapeHtml(String(ring.display || value))
                + '</div>';
        }
        var trendClass = trendUp === true ? "trend-up" : trendUp === false ? "trend-down" : "";
        return '<article class="kpi-card draw-in">'
            + '<span class="kpi-stripe" style="background:' + color + ';"></span>'
            + '<div class="kpi-main">'
            + '<div class="kpi-label">' + escapeHtml(label) + '</div>'
            + '<div class="kpi-inline">'
            + '<div class="kpi-copy">'
            + '<div class="kpi-value count-up" data-key="' + escapeAttr(label) + '" data-target="' + escapeAttr(String(toNumericValue(value))) + '" data-format="' + escapeAttr(detectFormat(value)) + '">' + escapeHtml(String(value)) + '</div>'
            + '<div class="kpi-caption ' + trendClass + '">' + escapeHtml(caption) + '</div>'
            + '</div>'
            + ringHtml
            + '</div>'
            + '</div>'
            + '</article>';
    }

    function clientKpiCard(data, color) {
        data = data || {};
        var total = data.total || 0;
        var online = data.online || 0;
        var offline = data.offline || 0;
        var rate = data.rate || 0;
        var onlineWidth = total > 0 ? Math.round(online * 100 / total) : 0;
        var offlineWidth = total > 0 ? Math.round(offline * 100 / total) : 0;
        return '<article class="kpi-card draw-in">'
            + '<span class="kpi-stripe" style="background:' + color + ';"></span>'
            + '<div class="kpi-main">'
            + '<div class="kpi-label">Client 在线率</div>'
            + '<div class="kpi-inline client-inline">'
            + '<div class="kpi-copy">'
            + '<div class="kpi-value count-up" data-key="clientOnline" data-target="' + rate + '" data-format="percent">' + rate + '%</div>'
            + '<div class="kpi-caption">在线 ' + online + ' / 离线 ' + offline + '</div>'
            + '</div>'
            + '<div class="mini-bars">'
            + miniBar("在线", onlineWidth, "#3B82F6")
            + miniBar("离线", offlineWidth, "#CBD5E1")
            + '</div>'
            + '</div>'
            + '</div>'
            + '</article>';
    }

    function miniBar(label, width, color) {
        return '<div class="mini-bar-row">'
            + '<span>' + escapeHtml(label) + '</span>'
            + '<div class="mini-bar-track"><span class="mini-bar-fill" style="width:' + width + '%;background:' + color + ';"></span></div>'
            + '</div>';
    }

    function renderTrendLegend() {
        var legend = [
            {key: "pending", label: "待验证", color: "#F59E0B"},
            {key: "verifying", label: "验证中", color: "#3B82F6"},
            {key: "repair", label: "待修复", color: "#EF4444"},
            {key: "fixed", label: "已修复", color: "#10B981"},
            {key: "new", label: "新增漏洞", color: "#1D4ED8"}
        ];
        document.getElementById("trendLegend").innerHTML = legend.map(function (item) {
            return '<button type="button" class="legend-chip' + (hiddenTrendSeries[item.key] ? ' is-muted' : '') + '" data-key="' + item.key + '">'
                + '<span class="legend-dot" style="background:' + item.color + ';"></span>' + escapeHtml(item.label)
                + '</button>';
        }).join("");
        document.getElementById("trendLegend").onclick = function (event) {
            var button = event.target.closest(".legend-chip");
            if (!button || !currentData) return;
            var key = button.dataset.key;
            hiddenTrendSeries[key] = !hiddenTrendSeries[key];
            renderDashboard(currentData);
        };
    }

    function renderTrendChart(data) {
        var svg = document.getElementById("trendChart");
        var tooltip = document.getElementById("trendTooltip");
        var points = Array.isArray(data.points) ? data.points : [];
        var width = 900;
        var height = 250;
        var left = 46;
        var bottom = 26;
        var top = 16;
        var chartWidth = width - left - 14;
        var chartHeight = height - top - bottom;

        if (!points.length) {
            svg.innerHTML = emptySvg(width, height, "当前时间窗口暂无趋势数据");
            document.getElementById("trendMeta").textContent = data.meta || "-";
            return;
        }

        var maxValue = Math.max.apply(null, points.map(function (item) {
            return item.pending + item.verifying + item.repair + item.fixed;
        }).concat([1]));
        var xAt = createXAxis(points.length, left, chartWidth);
        var yAt = function (value) {
            return top + chartHeight - (value / maxValue) * chartHeight;
        };

        var cumulative = points.map(function (item) {
            return {
                label: item.label,
                pendingBase: 0,
                pendingTop: item.pending,
                verifyingBase: item.pending,
                verifyingTop: item.pending + item.verifying,
                repairBase: item.pending + item.verifying,
                repairTop: item.pending + item.verifying + item.repair,
                fixedBase: item.pending + item.verifying + item.repair,
                fixedTop: item.pending + item.verifying + item.repair + item.fixed,
                newCount: item.newCount
            };
        });

        var layers = [];
        if (!hiddenTrendSeries.pending) {
            layers.push(areaPath(cumulative, xAt, yAt, "pendingBase", "pendingTop", "#F59E0B44"));
        }
        if (!hiddenTrendSeries.verifying) {
            layers.push(areaPath(cumulative, xAt, yAt, "verifyingBase", "verifyingTop", "#3B82F633"));
        }
        if (!hiddenTrendSeries.repair) {
            layers.push(areaPath(cumulative, xAt, yAt, "repairBase", "repairTop", "#EF444433"));
        }
        if (!hiddenTrendSeries.fixed) {
            layers.push(areaPath(cumulative, xAt, yAt, "fixedBase", "fixedTop", "#10B98130"));
        }

        var newLine = hiddenTrendSeries.new ? "" : linePath(points, xAt, function (item) {
            return yAt(item.newCount);
        }, "#1D4ED8", 2.5);

        var markers = hiddenTrendSeries.new ? "" : points.map(function (item, index) {
            return '<circle cx="' + xAt(index) + '" cy="' + yAt(item.newCount) + '" r="3.5" fill="#1D4ED8"></circle>';
        }).join("");

        svg.innerHTML = gridLines(maxValue, left, top, chartWidth, chartHeight)
            + axisLabels(points, xAt, height)
            + layers.join("")
            + newLine
            + markers;

        document.getElementById("trendMeta").textContent = data.meta || "-";
        bindSeriesTooltip(svg, tooltip, points, left, chartWidth, function (item) {
            return '<strong>' + escapeHtml(item.label) + '</strong>'
                + '<br>待验证 ' + item.pending
                + '<br>验证中 ' + item.verifying
                + '<br>待修复 ' + item.repair
                + '<br>已修复 ' + item.fixed
                + '<br>新增 ' + item.newCount;
        });
    }

    function renderHighRisk(data) {
        var donut = document.getElementById("highRiskDonut");
        var totalElement = document.getElementById("highRiskTotal");
        var listElement = document.getElementById("highRiskList");
        var items = Array.isArray(data) ? data : [];
        var total = items.reduce(function (sum, item) { return sum + (item.count || 0); }, 0);
        totalElement.textContent = total;

        if (!items.length || total <= 0) {
            donut.style.background = "radial-gradient(closest-side, white 62%, transparent 63% 100%), conic-gradient(#E5E7EB 0 100%)";
            listElement.innerHTML = '<div class="empty-sheet">当前没有高危未修复漏洞。</div>';
            return;
        }

        var cursor = 0;
        var segments = items.map(function (item) {
            var start = cursor;
            cursor += (item.count || 0) / total * 100;
            return (item.color || "#3B82F6") + " " + start.toFixed(2) + "% " + cursor.toFixed(2) + "%";
        }).join(", ");
        donut.style.background = "radial-gradient(closest-side, white 62%, transparent 63% 100%), conic-gradient(" + segments + ")";

        listElement.innerHTML = items.map(function (item) {
            return '<div class="donut-row">'
                + '<span class="donut-color" style="background:' + escapeAttr(item.color || "#3B82F6") + ';"></span>'
                + '<strong>' + escapeHtml(item.name || "未分类") + '</strong>'
                + '<span>' + (item.hosts || 0) + ' 台主机</span>'
                + '<span>' + (item.ratio || 0) + '%</span>'
                + '</div>';
        }).join("");
    }

    function renderSlaDistribution(data) {
        var container = document.getElementById("slaDistribution");
        var hint = document.getElementById("slaDrillHint");
        var buckets = Array.isArray(data.buckets) ? data.buckets : [];
        if (!buckets.length) {
            container.innerHTML = '<div class="empty-sheet">当前窗口暂无已修复漏洞，无法计算修复时效。</div>';
            hint.textContent = "等待修复闭环后，这里会展示真实时效分布。";
            return;
        }

        var maxCount = Math.max.apply(null, buckets.map(function (item) { return item.count || 0; }).concat([1]));
        var avgPosition = Math.min(95, Math.round((data.averageDays || 0) / Math.max(data.maxDays || 16, 1) * 100));
        var targetPosition = Math.min(95, Math.round((data.targetDays || 2) / Math.max(data.maxDays || 16, 1) * 100));

        container.innerHTML = buckets.map(function (item) {
            var width = Math.round((item.count || 0) / maxCount * 100);
            return '<div class="dist-row">'
                + '<span>' + escapeHtml(item.label || "-") + '</span>'
                + '<div class="dist-track">'
                + '<span class="dist-fill" style="width:' + width + '%;background:' + (item.overTarget ? "#EF4444" : "#3B82F6") + ';"></span>'
                + '<span class="dist-line" style="left:' + avgPosition + '%;"></span>'
                + '<span class="dist-target" style="left:' + targetPosition + '%;"></span>'
                + '</div>'
                + '<strong>' + (item.count || 0) + '</strong>'
                + '</div>';
        }).join("");
        hint.textContent = "平均 " + (data.averageDays || 0) + " 天，目标 " + (data.targetDays || 2) + " 天";
    }

    function renderVerifyChart(data) {
        var svg = document.getElementById("verifyChart");
        var tooltip = document.getElementById("verifyTooltip");
        var points = Array.isArray(data.points) ? data.points : [];
        var width = 760;
        var height = 250;
        var left = 44;
        var top = 16;
        var bottom = 24;
        var chartWidth = width - left - 18;
        var chartHeight = height - top - bottom;

        if (!points.length) {
            svg.innerHTML = emptySvg(width, height, "当前窗口暂无验证回传");
            return;
        }

        var maxTotal = Math.max.apply(null, points.map(function (item) { return item.total || 0; }).concat([1]));
        var xAt = createXAxis(points.length, left, chartWidth);
        var yBar = function (value) {
            return top + chartHeight - (value / maxTotal) * chartHeight;
        };
        var yRate = function (value) {
            return top + chartHeight - (value / 100) * chartHeight;
        };
        var barWidth = Math.max(18, chartWidth / Math.max(points.length * 2.6, 8));

        var bars = points.map(function (item, index) {
            var x = xAt(index) - barWidth / 2;
            var y = yBar(item.total || 0);
            var heightValue = top + chartHeight - y;
            return '<rect x="' + x + '" y="' + y + '" width="' + barWidth + '" height="' + heightValue + '" rx="7" fill="#BFDBFE"></rect>';
        }).join("");

        var rateLine = linePath(points, xAt, function (item) {
            return yRate(item.rate || 0);
        }, "#1D4ED8", 2.5);

        var ratePoints = points.map(function (item, index) {
            var color = (item.rate || 0) < 80 ? "#EF4444" : "#1D4ED8";
            return '<circle cx="' + xAt(index) + '" cy="' + yRate(item.rate || 0) + '" r="4" fill="' + color + '"></circle>';
        }).join("");

        svg.innerHTML = percentageGrid(left, top, chartWidth, chartHeight, height, points, xAt)
            + bars
            + rateLine
            + ratePoints;

        bindSeriesTooltip(svg, tooltip, points, left, chartWidth, function (item) {
            return '<strong>' + escapeHtml(item.label) + '</strong>'
                + '<br>验证次数 ' + (item.total || 0)
                + '<br>命中 ' + (item.success || 0)
                + '<br>不影响 ' + (item.fail || 0)
                + '<br>成功率 ' + (item.rate || 0) + '%';
        });
    }

    function renderClientChart(data) {
        var svg = document.getElementById("clientChart");
        var tooltip = document.getElementById("clientTooltip");
        var points = Array.isArray(data.points) ? data.points : [];
        var width = 760;
        var height = 250;
        var left = 44;
        var top = 16;
        var bottom = 24;
        var chartWidth = width - left - 18;
        var chartHeight = height - top - bottom;

        if (!points.length) {
            svg.innerHTML = emptySvg(width, height, "暂无在线率趋势数据");
            renderOfflineSheet(data.offlineHosts || []);
            return;
        }

        var maxOnline = Math.max.apply(null, points.map(function (item) { return item.online || 0; }).concat([1]));
        var xAt = createXAxis(points.length, left, chartWidth);
        var yOnline = function (value) {
            return top + chartHeight - (value / maxOnline) * chartHeight;
        };
        var yRate = function (value) {
            return top + chartHeight - (value / 100) * chartHeight;
        };

        var areaPathValue = areaPath(points, xAt, yOnline, null, "online", "#DBEAFE");
        var rateLine = linePath(points, xAt, function (item) {
            return yRate(item.rate || 0);
        }, "#1D4ED8", 2.5);
        var thresholdY = yRate(95);

        svg.innerHTML = percentageGrid(left, top, chartWidth, chartHeight, height, points, xAt)
            + '<rect x="' + left + '" y="' + thresholdY + '" width="' + chartWidth + '" height="' + (top + chartHeight - thresholdY) + '" fill="#FEE2E2" opacity="0.26"></rect>'
            + areaPathValue
            + rateLine
            + '<line x1="' + left + '" y1="' + thresholdY + '" x2="' + (left + chartWidth) + '" y2="' + thresholdY + '" stroke="#EF4444" stroke-width="2" stroke-dasharray="6 6"></line>';

        bindSeriesTooltip(svg, tooltip, points, left, chartWidth, function (item) {
            return '<strong>' + escapeHtml(item.label) + '</strong>'
                + '<br>在线主机 ' + (item.online || 0)
                + '<br>在线率 ' + (item.rate || 0) + '%';
        });

        renderOfflineSheet(data.offlineHosts || []);
    }

    function renderOfflineSheet(rows) {
        var container = document.getElementById("offlineSheet");
        if (!rows.length) {
            container.innerHTML = '<div class="empty-sheet">当前没有离线主机。</div>';
            return;
        }
        container.innerHTML = rows.map(function (item) {
            return '<div class="offline-row">'
                + '<div><strong>' + escapeHtml(item.hostname || "-") + '</strong><span>' + escapeHtml(item.ip || "-") + '</span></div>'
                + '<span>' + escapeHtml(item.offlineFor || "-") + '</span>'
                + '</div>';
        }).join("");
    }

    function bindSeriesTooltip(svg, tooltip, points, left, chartWidth, formatter) {
        svg.onmousemove = function (event) {
            var rect = svg.getBoundingClientRect();
            var relativeX = event.clientX - rect.left - left;
            var index = Math.max(0, Math.min(points.length - 1, Math.round(relativeX / Math.max(chartWidth, 1) * Math.max(points.length - 1, 1))));
            var item = points[index];
            showTooltip(tooltip, event.clientX - rect.left + 16, event.clientY - rect.top + 16, formatter(item));
        };
        svg.onmouseleave = function () {
            hideTooltip(tooltip);
        };
    }

    function areaPath(points, xAt, yGetter, bottomKey, topKey, fill) {
        if (!points.length) return "";
        var topPath = points.map(function (item, index) {
            var topValue = topKey ? item[topKey] : item.online;
            return (index === 0 ? "M" : "L") + xAt(index) + " " + yGetter(topValue || 0);
        }).join(" ");

        var bottomPath = points.slice().reverse().map(function (item, reverseIndex) {
            var originalIndex = points.length - 1 - reverseIndex;
            var bottomValue = bottomKey ? item[bottomKey] : 0;
            return "L" + xAt(originalIndex) + " " + yGetter(bottomValue || 0);
        }).join(" ");

        return '<path d="' + topPath + " " + bottomPath + ' Z" fill="' + fill + '"></path>';
    }

    function linePath(points, xAt, yGetter, stroke, strokeWidth) {
        if (!points.length) return "";
        var d = points.map(function (item, index) {
            return (index === 0 ? "M" : "L") + xAt(index) + " " + yGetter(item);
        }).join(" ");
        return '<path d="' + d + '" fill="none" stroke="' + stroke + '" stroke-width="' + strokeWidth + '" stroke-linecap="round"></path>';
    }

    function createXAxis(pointCount, left, chartWidth) {
        return function (index) {
            if (pointCount <= 1) {
                return left + chartWidth / 2;
            }
            return left + (chartWidth * index / (pointCount - 1));
        };
    }

    function gridLines(maxValue, left, top, chartWidth, chartHeight) {
        var lines = [];
        for (var i = 0; i <= 4; i++) {
            var y = top + chartHeight * i / 4;
            var label = Math.round(maxValue * (1 - i / 4));
            lines.push('<line x1="' + left + '" y1="' + y + '" x2="' + (left + chartWidth) + '" y2="' + y + '" stroke="#E5ECF4"></line>');
            lines.push('<text x="8" y="' + (y + 4) + '" fill="#7B8798" font-size="11">' + label + '</text>');
        }
        return lines.join("");
    }

    function axisLabels(points, xAt, height) {
        return points.map(function (item, index) {
            return '<text x="' + xAt(index) + '" y="' + (height - 6) + '" text-anchor="middle" fill="#7B8798" font-size="11">' + escapeHtml(item.label || "") + '</text>';
        }).join("");
    }

    function percentageGrid(left, top, chartWidth, chartHeight, height, points, xAt) {
        var labels = [100, 80, 60, 40, 20, 0];
        var output = [];
        labels.forEach(function (label) {
            var y = top + chartHeight - label / 100 * chartHeight;
            output.push('<line x1="' + left + '" y1="' + y + '" x2="' + (left + chartWidth) + '" y2="' + y + '" stroke="#E5ECF4"></line>');
            output.push('<text x="6" y="' + (y + 4) + '" fill="#7B8798" font-size="11">' + label + '%</text>');
        });
        output.push(axisLabels(points, xAt, height));
        return output.join("");
    }

    function emptySvg(width, height, text) {
        return '<rect x="0" y="0" width="' + width + '" height="' + height + '" fill="#F8FAFC"></rect>'
            + '<text x="' + (width / 2) + '" y="' + (height / 2) + '" text-anchor="middle" fill="#94A3B8" font-size="14">' + escapeHtml(text) + '</text>';
    }

    function showTooltip(tooltip, x, y, html) {
        tooltip.style.left = x + "px";
        tooltip.style.top = y + "px";
        tooltip.innerHTML = html;
        tooltip.classList.add("is-visible");
    }

    function hideTooltip(tooltip) {
        tooltip.classList.remove("is-visible");
    }

    function animateNumbers() {
        document.querySelectorAll(".count-up").forEach(function (element) {
            var key = element.dataset.key || element.textContent;
            var target = Number(element.dataset.target || "0");
            var format = element.dataset.format || "int";
            var startValue = Number(animationState[key] || 0);
            animationState[key] = target;
            var startTime = performance.now();
            var duration = 520;

            function tick(now) {
                var progress = Math.min(1, (now - startTime) / duration);
                var current = startValue + (target - startValue) * progress;
                element.textContent = formatAnimatedValue(current, format);
                if (progress < 1) {
                    requestAnimationFrame(tick);
                } else {
                    element.textContent = formatAnimatedValue(target, format);
                }
            }

            requestAnimationFrame(tick);
        });
    }

    function formatAnimatedValue(value, format) {
        if (format === "percent") {
            return Math.round(value) + "%";
        }
        if (format === "day") {
            return (Math.round(value * 10) / 10).toFixed(1) + "天";
        }
        return String(Math.round(value));
    }

    function toNumericValue(value) {
        if (typeof value === "number") return value;
        var match = String(value == null ? "" : value).match(/-?\d+(\.\d+)?/);
        return match ? Number(match[0]) : 0;
    }

    function detectFormat(value) {
        var text = String(value == null ? "" : value);
        if (text.indexOf("%") >= 0) return "percent";
        if (text.indexOf("天") >= 0) return "day";
        return "int";
    }

    function getCustomStart() {
        return document.getElementById("customStartDate").value;
    }

    function getCustomEnd() {
        return document.getElementById("customEndDate").value;
    }

    function escapeHtml(value) {
        return String(value == null ? "" : value)
            .replace(/&/g, "&amp;")
            .replace(/</g, "&lt;")
            .replace(/>/g, "&gt;")
            .replace(/"/g, "&quot;")
            .replace(/'/g, "&#39;");
    }

    function escapeAttr(value) {
        return escapeHtml(value);
    }
});
