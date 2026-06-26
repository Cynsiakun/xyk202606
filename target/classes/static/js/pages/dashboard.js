layui.use(["layer"], function () {
    var layer = layui.layer;
    var charts = {};
    var loadingIndex = null;
    var trendCards = [];
    var currentTrendKey = "";

    init();

    function init() {
        bindEvents();
        initCharts();
        loadOverview();
        window.addEventListener("resize", resizeCharts);
    }

    function bindEvents() {
        document.getElementById("refreshButton").addEventListener("click", loadOverview);
        var openThreatScreenButton = document.getElementById("openThreatScreenButton");
        if (openThreatScreenButton) {
            openThreatScreenButton.addEventListener("click", function () {
                window.top.location.href = "/pages/threat.html";
            });
        }
    }

    function initCharts() {
        charts.starRing = echarts.init(document.getElementById("starRingChart"));
        charts.assetCategory = echarts.init(document.getElementById("assetCategoryChart"));
        charts.topProduct = echarts.init(document.getElementById("topProductChart"));
        charts.baselineStatus = echarts.init(document.getElementById("baselineStatusChart"));
        charts.severity = echarts.init(document.getElementById("severityChart"));
        charts.funnel = echarts.init(document.getElementById("funnelChart"));
        charts.trend = echarts.init(document.getElementById("trendChart"));
    }

    async function loadOverview() {
        setLoading(true);
        try {
            var result = await AppRequest.request("/api/dashboard/overview", {method: "GET"});
            render(result.data || {});
        } catch (error) {
            renderEmpty();
            layer.msg(error.message || "首页态势数据加载失败", {icon: 2});
        } finally {
            setLoading(false);
        }
    }

    function render(data) {
        renderHero(data);
        renderMetrics(data.metrics || []);
        renderStarRing(data.starRing || {});
        renderAssetCategory(data.assetCategories || []);
        renderTopProducts(data.topProducts || []);
        renderBaselineStatus(data.baselineStatus || {});
        renderSeverity(data.vulnerabilitySeverity || {});
        renderFunnel(data.vulnerabilityFunnel || []);
        renderTrendTabs(data.trends || []);
    }

    function renderEmpty() {
        renderHero({});
        renderMetrics([]);
        renderStarRing({});
        renderAssetCategory([]);
        renderTopProducts([]);
        renderBaselineStatus({});
        renderSeverity({});
        renderFunnel([]);
        renderTrendTabs([]);
    }

    function renderHero(data) {
        document.getElementById("lastUpdatedLabel").textContent = data.lastUpdatedLabel ? "最新活动 " + data.lastUpdatedLabel : "-";
    }

    function renderMetrics(items) {
        var html = items.map(function (item) {
            return '<article class="metric-card tone-' + escapeHtml(item.tone || "cyan") + '">'
                + '<div class="metric-label">' + escapeHtml(item.label || "-") + '</div>'
                + '<div class="metric-value">' + escapeHtml(item.value || "0") + '</div>'
                + '<div class="metric-sub">' + escapeHtml(item.subText || "-") + '</div>'
                + '</article>';
        }).join("");
        document.getElementById("metricStrip").innerHTML = html;
    }

    function renderStarRing(starRing) {
        var nodes = Array.isArray(starRing.nodes) ? starRing.nodes : [];
        var categories = [
            {name: "Safe", itemStyle: {color: "#22d3ee"}},
            {name: "Medium", itemStyle: {color: "#facc15"}},
            {name: "High", itemStyle: {color: "#fb7185"}},
            {name: "Critical", itemStyle: {color: "#f97316"}}
        ];
        var data = nodes.map(function (node, index) {
            return {
                id: "host-" + index,
                name: node.hostName || ("Host#" + (node.hostId || "")),
                value: Math.max(16, Math.min(82, 18 + (node.assetCount || 0) * 0.16 + (node.riskScore || 0) * 0.45)),
                category: riskCategoryIndex(node.riskLevel),
                host: node
            };
        });

        document.getElementById("starRingMeta").textContent = "平台指数 " + safeText(starRing.platformScore, "0")
            + " / 风险主机 " + safeText(starRing.riskyHostCount, "0")
            + " / 最近扫描 " + safeText(starRing.latestScanTime, "-");

        charts.starRing.setOption({
            backgroundColor: "transparent",
            tooltip: {
                trigger: "item",
                formatter: function (params) {
                    var host = params.data && params.data.host ? params.data.host : {};
                    return [
                        '<strong>' + escapeHtml(host.hostName || params.name || "-") + '</strong>',
                        'IP: ' + escapeHtml(host.ipv4 || "-"),
                        '资产数: ' + safeText(host.assetCount, "0"),
                        '基线通过率: ' + safeText(formatPercent(host.complianceRate), "0%"),
                        '漏洞: C ' + safeText(host.criticalVulnCount, "0") + ' / H ' + safeText(host.highVulnCount, "0"),
                        '补丁风险: ' + safeText(host.patchRiskCount, "0"),
                        '告警: ' + safeText(host.alertCount, "0")
                    ].join("<br>");
                }
            },
            legend: {
                bottom: 0,
                icon: "circle",
                itemWidth: 10,
                textStyle: {color: "rgba(224,236,255,0.72)"},
                data: categories.map(function (item) { return item.name; })
            },
            graphic: buildStarCenterGraphic(starRing),
            series: [{
                type: "graph",
                layout: "circular",
                circular: {rotateLabel: false},
                roam: false,
                data: data,
                categories: categories,
                symbol: "circle",
                label: {
                    show: true,
                    color: "#e8f2ff",
                    fontSize: 11
                },
                lineStyle: {
                    color: "rgba(61, 225, 255, 0.18)",
                    width: 1.2,
                    curveness: 0.18
                },
                edgeSymbol: ["none", "none"],
                links: buildRingLinks(nodes),
                itemStyle: {
                    borderColor: "rgba(255,255,255,0.32)",
                    borderWidth: 1.4,
                    shadowBlur: 18,
                    shadowColor: "rgba(34,211,238,0.24)"
                },
                emphasis: {
                    scale: 1.14,
                    focus: "adjacency"
                }
            }],
            animationDuration: 900
        }, true);
    }

    function renderAssetCategory(items) {
        var data = items.map(function (item) {
            return {name: item.name || "-", value: item.count || 0};
        });
        charts.assetCategory.setOption({
            color: ["#22d3ee", "#60a5fa", "#34d399", "#facc15", "#fb7185", "#818cf8"],
            tooltip: {
                trigger: "item",
                formatter: function (params) {
                    return escapeHtml(params.name) + "<br>数量: " + params.value + "<br>占比: " + params.percent + "%";
                }
            },
            series: [{
                type: "pie",
                radius: ["26%", "72%"],
                roseType: "radius",
                center: ["50%", "46%"],
                itemStyle: {
                    borderColor: "rgba(7,18,31,0.95)",
                    borderWidth: 2
                },
                label: {
                    color: "#e9f3ff",
                    formatter: "{b}\n{d}%"
                },
                data: data
            }],
            graphic: emptyGraphic(data.length, "暂无资产分类数据")
        }, true);
    }

    function renderTopProducts(items) {
        var top = items.slice(0, 8);
        charts.topProduct.setOption({
            color: ["#60a5fa"],
            tooltip: {
                trigger: "axis",
                axisPointer: {type: "shadow"}
            },
            grid: {left: 118, right: 16, top: 16, bottom: 16, containLabel: false},
            xAxis: {
                type: "value",
                splitLine: {lineStyle: {color: "rgba(154,180,214,0.14)"}},
                axisLabel: {color: "rgba(224,236,255,0.68)"}
            },
            yAxis: {
                type: "category",
                inverse: true,
                data: top.map(function (item) { return item.name || "-"; }),
                axisTick: {show: false},
                axisLine: {show: false},
                axisLabel: {
                    color: "rgba(232,242,255,0.86)",
                    formatter: function (value) {
                        return value.length > 14 ? value.slice(0, 14) + "..." : value;
                    }
                }
            },
            series: [{
                type: "bar",
                barWidth: 14,
                data: top.map(function (item) { return item.count || 0; }),
                itemStyle: {
                    borderRadius: [0, 8, 8, 0],
                    color: new echarts.graphic.LinearGradient(1, 0, 0, 0, [
                        {offset: 0, color: "#22d3ee"},
                        {offset: 1, color: "#2563eb"}
                    ])
                }
            }],
            graphic: emptyGraphic(top.length, "暂无产品排行")
        }, true);
    }

    function renderBaselineStatus(status) {
        var passCount = numeric(status.passCount);
        var failCount = numeric(status.failCount);
        var errorCount = numeric(status.errorCount);
        var complianceRate = formatPercent(status.complianceRate);
        charts.baselineStatus.setOption({
            color: ["#22c55e", "#fb7185", "#f59e0b"],
            tooltip: {trigger: "item"},
            legend: {
                bottom: 0,
                icon: "circle",
                itemWidth: 10,
                textStyle: {color: "rgba(224,236,255,0.72)"}
            },
            graphic: [{
                type: "text",
                left: "center",
                top: "42%",
                style: {
                    text: complianceRate + "\n合规率",
                    fill: "#f8fbff",
                    fontSize: 18,
                    fontWeight: 700,
                    textAlign: "center"
                }
            }],
            series: [{
                type: "pie",
                radius: ["52%", "76%"],
                center: ["50%", "44%"],
                label: {show: false},
                data: [
                    {name: "PASS", value: passCount},
                    {name: "FAIL", value: failCount},
                    {name: "ERROR", value: errorCount}
                ]
            }]
        }, true);
    }

    function renderSeverity(severity) {
        var labels = ["LOW", "MEDIUM", "HIGH", "CRITICAL"];
        var values = [
            numeric(severity.lowCount),
            numeric(severity.mediumCount),
            numeric(severity.highCount),
            numeric(severity.criticalCount)
        ];
        charts.severity.setOption({
            color: ["#60a5fa", "#facc15", "#fb7185", "#f97316"],
            tooltip: {
                trigger: "axis",
                axisPointer: {type: "shadow"}
            },
            grid: {left: 36, right: 20, top: 18, bottom: 18},
            xAxis: {
                type: "value",
                splitLine: {lineStyle: {color: "rgba(154,180,214,0.14)"}},
                axisLabel: {color: "rgba(224,236,255,0.68)"}
            },
            yAxis: {
                type: "category",
                data: labels,
                axisTick: {show: false},
                axisLine: {show: false},
                axisLabel: {color: "#e8f2ff"}
            },
            series: [{
                type: "bar",
                data: values.map(function (value, index) {
                    return {
                        value: value,
                        itemStyle: {
                            borderRadius: [0, 12, 12, 0],
                            color: ["#60a5fa", "#facc15", "#fb7185", "#f97316"][index]
                        }
                    };
                }),
                barWidth: 18
            }],
            graphic: emptyGraphic(values.some(function (value) { return value > 0; }), "暂无漏洞分级数据")
        }, true);
    }

    function renderFunnel(stages) {
        var data = (stages || []).map(function (item) {
            return {name: item.name || "-", value: item.count || 0};
        });
        charts.funnel.setOption({
            color: ["#22d3ee", "#60a5fa", "#facc15", "#fb7185", "#f97316"],
            tooltip: {trigger: "item"},
            series: [{
                type: "funnel",
                left: "10%",
                top: 10,
                bottom: 10,
                width: "80%",
                minSize: "30%",
                maxSize: "100%",
                sort: "descending",
                gap: 6,
                label: {
                    show: true,
                    position: "inside",
                    color: "#08131f",
                    formatter: function (params) {
                        return params.name + "\n" + params.value;
                    }
                },
                itemStyle: {
                    borderColor: "rgba(9,19,31,0.95)",
                    borderWidth: 2
                },
                data: data
            }],
            graphic: emptyGraphic(data.length, "暂无漏洞处置阶段数据")
        }, true);
    }

    function renderTrendTabs(cards) {
        trendCards = Array.isArray(cards) ? cards : [];
        if (!trendCards.length) {
            document.getElementById("trendTabs").innerHTML = "";
            document.getElementById("trendTitle").textContent = "风险趋势";
            document.getElementById("trendSubtitle").textContent = "-";
            renderTrendChart(null);
            return;
        }
        if (!currentTrendKey || !trendCards.some(function (item) { return item.key === currentTrendKey; })) {
            currentTrendKey = trendCards[0].key;
        }
        updateTrendTabs();
        renderTrendByKey(currentTrendKey);
    }

    function updateTrendTabs() {
        document.getElementById("trendTabs").innerHTML = trendCards.map(function (item) {
            var activeClass = item.key === currentTrendKey ? " is-active" : "";
            return '<button type="button" class="trend-tab' + activeClass + '" data-trend-key="' + escapeHtml(item.key) + '">'
                + escapeHtml(item.label || item.key || "-")
                + '</button>';
        }).join("");
        Array.prototype.forEach.call(document.querySelectorAll(".trend-tab"), function (button) {
            button.addEventListener("click", function () {
                var key = button.getAttribute("data-trend-key");
                currentTrendKey = key;
                updateTrendTabs();
                renderTrendByKey(key);
            });
        });
    }

    function renderTrendByKey(key) {
        var card = trendCards.find(function (item) { return item.key === key; }) || trendCards[0];
        document.getElementById("trendTitle").textContent = card && card.label ? card.label : "风险趋势";
        document.getElementById("trendSubtitle").textContent = card && card.subtitle ? card.subtitle : "-";
        renderTrendChart(card);
    }

    function renderTrendChart(card) {
        var points = card && Array.isArray(card.points) ? card.points : [];
        charts.trend.setOption({
            color: ["#22d3ee"],
            tooltip: {trigger: "axis"},
            grid: {left: 36, right: 18, top: 26, bottom: 34},
            xAxis: {
                type: "category",
                data: points.map(function (item) { return item.label || "-"; }),
                axisLine: {lineStyle: {color: "rgba(154,180,214,0.22)"}},
                axisLabel: {color: "rgba(224,236,255,0.7)"}
            },
            yAxis: {
                type: "value",
                splitLine: {lineStyle: {color: "rgba(154,180,214,0.14)"}},
                axisLabel: {color: "rgba(224,236,255,0.68)"}
            },
            series: [{
                type: "line",
                smooth: true,
                symbol: "circle",
                symbolSize: 8,
                lineStyle: {width: 3},
                areaStyle: {
                    color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
                        {offset: 0, color: "rgba(34,211,238,0.32)"},
                        {offset: 1, color: "rgba(34,211,238,0.02)"}
                    ])
                },
                data: points.map(function (item) { return item.count || 0; })
            }],
            graphic: emptyGraphic(points.length, "暂无趋势数据")
        }, true);
    }

    function buildRingLinks(nodes) {
        if (!nodes || nodes.length < 2) {
            return [];
        }
        return nodes.map(function (node, index) {
            return {
                source: index,
                target: (index + 1) % nodes.length
            };
        });
    }

    function buildStarCenterGraphic(starRing) {
        return [{
            type: "circle",
            left: "center",
            top: "middle",
            shape: {r: 76},
            style: {
                fill: "rgba(4, 24, 40, 0.88)",
                stroke: "rgba(61, 225, 255, 0.28)",
                lineWidth: 2
            }
        }, {
            type: "text",
            left: "center",
            top: "43%",
            style: {
                text: safeText(starRing.platformScore, "0"),
                fill: "#ffffff",
                fontSize: 34,
                fontWeight: 800,
                textAlign: "center"
            }
        }, {
            type: "text",
            left: "center",
            top: "53%",
            style: {
                text: "Platform Score",
                fill: "rgba(212,226,248,0.74)",
                fontSize: 13,
                textAlign: "center"
            }
        }];
    }

    function riskCategoryIndex(level) {
        if (level === "critical") {
            return 3;
        }
        if (level === "high") {
            return 2;
        }
        if (level === "medium") {
            return 1;
        }
        return 0;
    }

    function emptyGraphic(hasData, text) {
        var exists = typeof hasData === "boolean" ? hasData : !!hasData;
        if (exists) {
            return [];
        }
        return [{
            type: "text",
            left: "center",
            top: "middle",
            style: {
                text: text,
                fill: "rgba(178, 198, 225, 0.72)",
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
                loadingIndex = layer.load(1, {shade: [0.08, "#08131f"]});
            }
            return;
        }
        if (loadingIndex != null) {
            layer.close(loadingIndex);
            loadingIndex = null;
        }
    }

    function numeric(value) {
        return value == null ? 0 : Number(value) || 0;
    }

    function formatPercent(value) {
        var numericValue = Number(value || 0);
        return numericValue.toFixed(2).replace(/\.00$/, "") + "%";
    }

    function safeText(value, fallback) {
        return value == null || value === "" ? fallback : String(value);
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
