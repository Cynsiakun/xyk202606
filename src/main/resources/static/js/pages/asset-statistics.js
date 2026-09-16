layui.use(["layer"], function () {
    var layer = layui.layer;
    var charts = {};
    var loadingIndex = null;

    init();

    function init() {
        bindEvents();
        initCharts();
        loadOverview();
        window.addEventListener("resize", resizeCharts);
    }

    function bindEvents() {
        document.getElementById("refreshButton").addEventListener("click", function () {
            loadOverview();
        });
    }

    function initCharts() {
        charts.topIp = echarts.init(document.getElementById("topIpChart"));
        charts.topPort = echarts.init(document.getElementById("topPortChart"));
        charts.category = echarts.init(document.getElementById("categoryPieChart"));
        charts.topProduct = echarts.init(document.getElementById("topProductChart"));
        charts.trend = echarts.init(document.getElementById("trendChart"));
    }

    async function loadOverview() {
        setLoading(true);
        try {
            var result = await AppRequest.request("/api/asset-statistics/overview", {method: "GET"});
            render(result.data || {});
        } catch (error) {
            renderEmpty();
            layer.msg(error.message || "资产统计概览加载失败", {icon: 2});
        } finally {
            setLoading(false);
        }
    }

    function render(data) {
        renderGeneratedAt(data.generatedAt);
        renderKpis(data);
        renderTopIpChart(data.topHosts || []);
        renderTopPortChart(data.topPorts || []);
        renderCategoryChart(data.assetTypes || []);
        renderTopProductChart(data.topProducts || []);
        renderTrendChart(data.discoveryTrend || {});
    }

    function renderEmpty() {
        renderGeneratedAt(null);
        renderKpis({});
        renderTopIpChart([]);
        renderTopPortChart([]);
        renderCategoryChart([]);
        renderTopProductChart([]);
        renderTrendChart({});
    }

    function renderGeneratedAt(value) {
        var text = value ? "生成时间：" + (AppUtils ? AppUtils.formatDateTime(value) : value) : "生成时间：-";
        document.getElementById("generatedAt").textContent = text;
    }

    function renderKpis(data) {
        var cards = [
            decorateCard(data.totalAssets, "#1f5eff"),
            decorateCard(data.totalHosts, "#139c6d"),
            decorateCard(data.portKinds, "#d99112"),
            decorateCard(data.identifiedRate, "#d9485f")
        ];
        document.getElementById("kpiGrid").innerHTML = cards.map(function (card) {
            return '<article class="stats-kpi-card" style="--card-accent:' + card.color + ';">'
                + '<div class="stats-kpi-label">' + escapeHtml(card.label) + '</div>'
                + '<div class="stats-kpi-value">' + escapeHtml(card.value) + '</div>'
                + '<div class="stats-kpi-sub">' + escapeHtml(card.subText) + '</div>'
                + '</article>';
        }).join("");
    }

    function decorateCard(card, color) {
        return {
            label: card && card.label ? card.label : "-",
            value: card && card.value ? card.value : "0",
            subText: card && card.subText ? card.subText : "-",
            color: color
        };
    }

    function renderTopIpChart(items) {
        var top = items.slice(0, 10);
        var names = top.map(function (item) { return item.name || "-"; });
        var values = top.map(function (item) { return item.count || 0; });
        charts.topIp.setOption({
            color: ["#1f5eff"],
            tooltip: {
                trigger: "axis",
                axisPointer: {type: "shadow"}
            },
            grid: {left: 110, right: 24, top: 12, bottom: 18, containLabel: false},
            xAxis: {
                type: "value",
                splitLine: {lineStyle: {color: "#e7edf4"}}
            },
            yAxis: {
                type: "category",
                data: names,
                inverse: true,
                axisTick: {show: false},
                axisLine: {show: false}
            },
            series: [{
                type: "bar",
                data: values,
                barWidth: 16,
                itemStyle: {borderRadius: [0, 8, 8, 0]}
            }],
            graphic: emptyGraphic(top.length, "暂无主机资产排行数据")
        }, true);
    }

    function renderTopPortChart(items) {
        var top = items.slice(0, 10);
        var pieData = top.map(function (item) {
            var portName = item.name || "-";
            var protocol = item.secondary || "tcp";
            return {
                name: portName,
                value: item.count || 0,
                rawLabel: portName + "/" + protocol,
                itemStyle: {
                    borderRadius: 6,
                    borderColor: "#ffffff",
                    borderWidth: 2
                }
            };
        });
        charts.topPort.setOption({
            color: ["#139c6d", "#1f5eff", "#d99112", "#d9485f", "#6b7cff", "#00a6a6", "#7a9e1f", "#ff8a3d", "#9b59b6", "#4f6d7a"],
            tooltip: {
                trigger: "item",
                formatter: function (params) {
                    return escapeHtml(params.data.rawLabel || params.name)
                        + "<br>覆盖主机数：" + params.value
                        + "<br>占比：" + params.percent + "%";
                }
            },
            series: [{
                type: "pie",
                radius: ["42%", "72%"],
                center: ["50%", "50%"],
                minAngle: pieData.length ? 8 : 0,
                avoidLabelOverlap: true,
                label: {
                    show: true,
                    formatter: function (params) {
                        var label = params.data.rawLabel || params.name;
                        return label + "\n" + params.value + " 台";
                    },
                    fontSize: 11,
                    lineHeight: 16
                },
                labelLine: {
                    length: 12,
                    length2: 10
                },
                emphasis: {
                    scale: true,
                    scaleSize: 8
                },
                data: pieData
            }],
            graphic: emptyGraphic(top.length, "暂无最新开放端口覆盖数据")
        }, true);
    }

    function renderCategoryChart(items) {
        var palette = {
            Web: "#1f5eff",
            DB: "#d99112",
            Middleware: "#139c6d",
            Unknown: "#a0aec0"
        };
        var data = items.map(function (item) {
            return {
                name: item.name || "Unknown",
                value: item.count || 0,
                itemStyle: {color: palette[item.name] || "#a0aec0"}
            };
        });
        charts.category.setOption({
            tooltip: {
                trigger: "item",
                formatter: function (params) {
                    return escapeHtml(params.name) + "<br>数量：" + params.value + "<br>占比：" + params.percent + "%";
                }
            },
            legend: {
                bottom: 0,
                icon: "circle"
            },
            series: [{
                type: "pie",
                radius: ["42%", "68%"],
                center: ["50%", "44%"],
                label: {
                    formatter: "{b}\n{d}%"
                },
                data: data
            }],
            graphic: emptyGraphic(data.length, "暂无资产类型分布")
        }, true);
    }

    function renderTopProductChart(items) {
        var top = items.slice(0, 10);
        charts.topProduct.setOption({
            color: ["#d9485f"],
            tooltip: {
                trigger: "axis",
                axisPointer: {type: "shadow"}
            },
            grid: {left: 110, right: 24, top: 12, bottom: 18, containLabel: false},
            xAxis: {
                type: "value",
                splitLine: {lineStyle: {color: "#e7edf4"}}
            },
            yAxis: {
                type: "category",
                data: top.map(function (item) { return item.name || "-"; }),
                inverse: true,
                axisTick: {show: false},
                axisLine: {show: false}
            },
            series: [{
                type: "bar",
                data: top.map(function (item) { return item.count || 0; }),
                barWidth: 16,
                itemStyle: {borderRadius: [0, 8, 8, 0]}
            }],
            graphic: emptyGraphic(top.length, "暂无软件分布数据")
        }, true);
    }

    function renderTrendChart(trend) {
        var points = Array.isArray(trend.points) ? trend.points : [];
        document.getElementById("trendSubtitle").textContent = trend.subtitle || "-";
        charts.trend.setOption({
            color: ["#1f5eff"],
            tooltip: {
                trigger: "axis"
            },
            grid: {left: 48, right: 24, top: 28, bottom: 48},
            xAxis: {
                type: "category",
                data: points.map(function (item) { return item.label || "-"; }),
                axisLabel: {
                    rotate: points.length > 10 ? 30 : 0
                }
            },
            yAxis: {
                type: "value",
                splitLine: {lineStyle: {color: "#e7edf4"}}
            },
            series: [{
                type: "line",
                smooth: true,
                symbol: "circle",
                symbolSize: 9,
                areaStyle: {
                    color: "rgba(31,94,255,0.14)"
                },
                lineStyle: {width: 3},
                data: points.map(function (item) { return item.count || 0; })
            }],
            graphic: emptyGraphic(points.length, "暂无资产发现趋势")
        }, true);
    }

    function emptyGraphic(hasData, text) {
        if (hasData) {
            return [];
        }
        return [{
            type: "text",
            left: "center",
            top: "middle",
            style: {
                text: text,
                fill: "#94a3b8",
                fontSize: 14
            }
        }];
    }

    function resizeCharts() {
        Object.keys(charts).forEach(function (key) {
            if (charts[key]) {
                charts[key].resize();
            }
        });
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

    function escapeHtml(value) {
        return String(value == null ? "" : value)
            .replace(/&/g, "&amp;")
            .replace(/</g, "&lt;")
            .replace(/>/g, "&gt;")
            .replace(/"/g, "&quot;")
            .replace(/'/g, "&#39;");
    }
});
