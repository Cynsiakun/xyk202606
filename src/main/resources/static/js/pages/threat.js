layui.use(["layer"], function () {
    var layer = layui.layer;
    var loadingIndex = null;
    var charts = {};
    var trendCharts = {};
    var autoRefreshTimer = null;
    var motion = {
        backdropCanvas: null,
        backdropContext: null,
        backdropParticles: [],
        stageCanvas: null,
        stageContext: null,
        stageParticles: [],
        rafId: null,
        lastTimestamp: 0,
        stageIntensity: 0.35
    };

    init();

    function init() {
        ensureLogin();
        bindEvents();
        initCharts();
        initMotionEffects();
        loadOverview();
        autoRefreshTimer = window.setInterval(loadOverview, 30000);
        window.addEventListener("resize", resizeCharts);
        window.addEventListener("beforeunload", destroyThreatScreen);
    }

    function ensureLogin() {
        if (!AppAuth.isLoggedIn()) {
            AppAuth.redirectToLogin();
        }
    }

    function bindEvents() {
        document.getElementById("backToPlatformButton").addEventListener("click", function () {
            window.location.href = "/index.html";
        });
        document.getElementById("refreshThreatScreenButton").addEventListener("click", loadOverview);
        document.getElementById("toggleFullscreenButton").addEventListener("click", toggleFullscreen);
        document.addEventListener("fullscreenchange", syncFullscreenButtonLabel);
    }

    function initCharts() {
        charts.assetCategory = echarts.init(document.getElementById("threatAssetCategoryChart"));
        charts.topProducts = echarts.init(document.getElementById("threatTopProductsChart"));
        charts.core = echarts.init(document.getElementById("threatCoreChart"));
        charts.baselineStatus = echarts.init(document.getElementById("threatBaselineStatusChart"));
        charts.severity = echarts.init(document.getElementById("threatSeverityChart"));
        charts.funnel = echarts.init(document.getElementById("threatFunnelChart"));
    }

    async function loadOverview() {
        setLoading(true);
        try {
            var result = await AppRequest.request("/api/threat-screen/overview", {method: "GET"});
            render(result.data || {});
        } catch (error) {
            renderEmpty();
            layer.msg(error.message || "态势大屏加载失败", {icon: 2});
        } finally {
            setLoading(false);
        }
    }

    function render(data) {
        renderLabels(data.labels || {}, data.viewMode || "tenant");
        renderLastUpdated(data.lastUpdatedLabel);
        renderMetrics(data.metrics || []);
        renderAssetCategory(data.assetCategories || []);
        renderTopProducts(data.topProducts || []);
        renderCore(data.core || {});
        renderBaselineStatus(data.baselineStatus || {});
        renderSeverity(data.vulnSeverity || {});
        renderFunnel(data.vulnFunnel || []);
        renderTrends(data.trends || []);
        renderEventFeed(data.eventFeed || []);
    }

    function renderEmpty() {
        renderLastUpdated("-");
        renderMetrics([]);
        renderAssetCategory([]);
        renderTopProducts([]);
        renderCore({});
        renderBaselineStatus({});
        renderSeverity({});
        renderFunnel([]);
        renderTrends([]);
        renderEventFeed([]);
    }

    function renderLastUpdated(text) {
        document.getElementById("threatLastUpdatedLabel").textContent = "最后更新时间 " + safeText(text, "-");
    }

    function renderLabels(labels, viewMode) {
        document.getElementById("threatScreenTitle").textContent = safeText(labels.title, "安全态势大屏");
        document.getElementById("threatScreenSubtitle").textContent = safeText(labels.subtitle, "以全屏视角展示资产、基线、漏洞、补丁和日志五类态势信号。");
        document.getElementById("threatAssetCategoryTitle").textContent = safeText(labels.assetCategoryTitle, "资产分类玫瑰图");
        document.getElementById("threatTopProductsTitle").textContent = safeText(labels.topProductsTitle, viewMode === "platform" ? "TOP 风险企业 / 主机规模" : "TOP 服务 / 产品");

        var coreBaselineSignal = document.querySelector(".threat-stage-signal-baseline span");
        var coreVulnSignal = document.querySelector(".threat-stage-signal-vuln span");
        var corePatchSignal = document.querySelector(".threat-stage-signal-patch span");
        var coreLogSignal = document.querySelector(".threat-stage-signal-log span");
        if (viewMode === "platform") {
            if (coreBaselineSignal) {
                coreBaselineSignal.textContent = "ENTERPRISE / BASELINE";
            }
            if (coreVulnSignal) {
                coreVulnSignal.textContent = "ENTERPRISE / VULNERABILITY";
            }
            if (corePatchSignal) {
                corePatchSignal.textContent = "ENTERPRISE / PATCH";
            }
            if (coreLogSignal) {
                coreLogSignal.textContent = "ENTERPRISE / LOG";
            }
            return;
        }
        if (coreBaselineSignal) {
            coreBaselineSignal.textContent = "BASELINE";
        }
        if (coreVulnSignal) {
            coreVulnSignal.textContent = "VULNERABILITY";
        }
        if (corePatchSignal) {
            corePatchSignal.textContent = "PATCH";
        }
        if (coreLogSignal) {
            coreLogSignal.textContent = "LOG";
        }
    }

    function renderMetrics(metrics) {
        var items = Array.isArray(metrics) && metrics.length ? metrics : defaultMetrics();
        document.getElementById("threatMetricsStrip").innerHTML = items.map(function (item) {
            return '<article class="threat-metric-card tone-' + escapeHtml(item.tone || "cyan") + '">'
                + '<div class="threat-metric-card-label">' + escapeHtml(item.label || "-") + '</div>'
                + '<div class="threat-metric-card-value" data-value="' + escapeHtml(item.value || "0") + '">' + escapeHtml(item.value || "0") + '</div>'
                + '<div class="threat-metric-card-sub">' + escapeHtml(item.subText || "-") + '</div>'
                + '</article>';
        }).join("");
        animateMetricValues();
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
                radius: ["26%", "76%"],
                roseType: "radius",
                center: ["50%", "50%"],
                itemStyle: {
                    borderColor: "rgba(3,10,18,0.96)",
                    borderWidth: 2
                },
                label: {
                    color: "#ebf6ff",
                    formatter: "{b}\n{d}%"
                },
                data: data
            }],
            graphic: emptyGraphic(data.length, "暂无资产分类数据")
        }, true);
    }

    function renderTopProducts(items) {
        var top = items.slice(0, 8);
        charts.topProducts.setOption({
            tooltip: {
                trigger: "axis",
                axisPointer: {type: "shadow"}
            },
            grid: {left: 112, right: 14, top: 12, bottom: 10, containLabel: false},
            xAxis: {
                type: "value",
                splitLine: {lineStyle: {color: "rgba(94, 138, 182, 0.16)"}},
                axisLabel: {color: "rgba(204, 225, 246, 0.72)"}
            },
            yAxis: {
                type: "category",
                inverse: true,
                data: top.map(function (item) { return item.name || "-"; }),
                axisTick: {show: false},
                axisLine: {show: false},
                axisLabel: {
                    color: "#ebf6ff",
                    formatter: function (value) {
                        return value.length > 13 ? value.slice(0, 13) + "..." : value;
                    }
                }
            },
            series: [{
                type: "bar",
                barWidth: 12,
                data: top.map(function (item) { return item.count || 0; }),
                itemStyle: {
                    borderRadius: [0, 9, 9, 0],
                    color: new echarts.graphic.LinearGradient(1, 0, 0, 0, [
                        {offset: 0, color: "#2563eb"},
                        {offset: 1, color: "#22d3ee"}
                    ])
                }
            }],
            graphic: emptyGraphic(top.length, "暂无产品分布")
        }, true);
    }

    function renderCore(core) {
        motion.stageIntensity = Math.max(0.25, Math.min(1, numeric(core.platformScore) / 100));
        var nodes = Array.isArray(core.nodes) ? core.nodes : [];
        var layout = buildCoreLayout(nodes.slice(0, 12), nodes.some(function (node) { return node.nodeType === "tenant"; }) ? "platform" : "tenant");

        charts.core.setOption({
            backgroundColor: "transparent",
            animationDurationUpdate: 700,
            animationEasingUpdate: "cubicOut",
            tooltip: {
                trigger: "item",
                formatter: function (params) {
                    if (params.seriesName === "signalAnchors") {
                        return '<strong>' + escapeHtml(params.data.signalLabel || params.name || "-") + '</strong><br>四类风险信号向中心汇聚';
                    }
                    var host = params.data && params.data.host ? params.data.host : {};
                    if (host.nodeType === "tenant") {
                        return [
                            '<strong>' + escapeHtml(host.tenantName || host.hostName || params.name || "-") + '</strong>',
                            '主机数: ' + safeText(host.managedHostCount, "0"),
                            '风险主机: ' + safeText(host.managedRiskyHostCount, "0"),
                            '企业风险分: ' + safeText(host.riskScore, "0"),
                            '漏洞: C ' + safeText(host.criticalVulnCount, "0") + ' / H ' + safeText(host.highVulnCount, "0"),
                            '补丁风险: ' + safeText(host.patchRiskCount, "0"),
                            '告警: ' + safeText(host.alertCount, "0")
                        ].join("<br>");
                    }
                    return [
                        '<strong>' + escapeHtml(host.hostName || params.name || "-") + '</strong>',
                        'IP: ' + escapeHtml(host.ipv4 || "-"),
                        '风险分: ' + safeText(host.riskScore, "0"),
                        '主信号: ' + escapeHtml(formatPrimarySignal(host.primarySignal)),
                        '资产数: ' + safeText(host.assetCount, "0"),
                        '漏洞: C ' + safeText(host.criticalVulnCount, "0") + ' / H ' + safeText(host.highVulnCount, "0"),
                        '基线异常: ' + (numeric(host.baselineFailCount) + numeric(host.baselineErrorCount)),
                        '告警: ' + safeText(host.alertCount, "0")
                    ].join("<br>");
                }
            },
            legend: {
                bottom: 12,
                icon: "circle",
                itemWidth: 10,
                textStyle: {color: "rgba(212, 228, 246, 0.72)"},
                data: ["Safe", "Medium", "High", "Critical"]
            },
            graphic: buildCoreGraphic(core),
            xAxis: {
                min: 0,
                max: 100,
                show: false
            },
            yAxis: {
                min: 0,
                max: 100,
                show: false
            },
            series: [{
                name: "signalFlow",
                type: "lines",
                coordinateSystem: "cartesian2d",
                zlevel: 1,
                polyline: false,
                effect: {
                    show: true,
                    period: 3.2,
                    trailLength: 0.24,
                    symbol: "circle",
                    symbolSize: 5,
                    color: "#8ff7ff"
                },
                lineStyle: {
                    width: 1.4,
                    opacity: 0.52,
                    curveness: 0.22,
                    color: "#22d3ee"
                },
                data: buildSignalFlowLines()
            }, {
                name: "hostLinks",
                type: "lines",
                coordinateSystem: "cartesian2d",
                zlevel: 2,
                effect: {
                    show: true,
                    period: 4.2,
                    trailLength: 0.12,
                    symbol: "circle",
                    symbolSize: 4
                },
                lineStyle: {
                    width: 1.1,
                    opacity: 0.28,
                    curveness: 0.1
                },
                data: layout.lines
            }, {
                name: "signalAnchors",
                type: "effectScatter",
                coordinateSystem: "cartesian2d",
                zlevel: 3,
                rippleEffect: {
                    scale: 3.4,
                    brushType: "stroke"
                },
                symbolSize: function (value) {
                    return value[2] || 10;
                },
                label: {
                    show: true,
                    position: "top",
                    distance: 10,
                    color: "rgba(176, 238, 255, 0.88)",
                    fontSize: 10,
                    formatter: function (params) {
                        return params.data.signalLabel || params.name || "";
                    }
                },
                itemStyle: {
                    color: "#7df9ff",
                    shadowBlur: 24,
                    shadowColor: "rgba(34, 211, 238, 0.32)"
                },
                data: buildSignalAnchors()
            }, {
                name: "centerPulse",
                type: "effectScatter",
                coordinateSystem: "cartesian2d",
                silent: true,
                zlevel: 3,
                rippleEffect: {
                    scale: 8,
                    brushType: "stroke"
                },
                symbolSize: 16,
                itemStyle: {
                    color: "rgba(125, 245, 255, 0.86)",
                    shadowBlur: 24,
                    shadowColor: "rgba(34, 211, 238, 0.36)"
                },
                data: [{value: [50, 50, 16]}]
            }, {
                name: "hostNodes",
                type: "effectScatter",
                coordinateSystem: "cartesian2d",
                zlevel: 4,
                rippleEffect: {
                    scale: 2.6,
                    brushType: "stroke"
                },
                symbolSize: function (value, params) {
                    return params.data.symbolSize;
                },
                label: {
                    show: true,
                    position: function (params) {
                        return params.data.labelPosition || "right";
                    },
                    color: "#ecf6ff",
                    fontSize: 11,
                    formatter: function (params) {
                        return trimLabel(params.data.name, 12);
                    }
                },
                itemStyle: {
                    borderColor: "rgba(255,255,255,0.45)",
                    borderWidth: 1.4,
                    shadowBlur: 24
                },
                emphasis: {
                    scale: 1.16
                },
                data: layout.nodes
            }]
        }, true);
    }

    function renderBaselineStatus(status) {
        var passCount = numeric(status.passCount);
        var failCount = numeric(status.failCount);
        var errorCount = numeric(status.errorCount);
        var hasData = passCount + failCount + errorCount > 0;
        charts.baselineStatus.setOption({
            color: ["#22c55e", "#fb7185", "#f59e0b"],
            tooltip: {trigger: "item"},
            series: [{
                type: "pie",
                radius: ["46%", "72%"],
                center: ["50%", "44%"],
                label: {show: false},
                data: [
                    {name: "PASS", value: passCount},
                    {name: "FAIL", value: failCount},
                    {name: "ERROR", value: errorCount}
                ]
            }],
            graphic: hasData ? [{
                type: "text",
                left: "center",
                top: "39%",
                style: {
                    text: formatPercent(status.complianceRate) + "\n合规率",
                    fill: "#f4fbff",
                    fontSize: 16,
                    fontWeight: 700,
                    textAlign: "center"
                }
            }] : emptyGraphic(false, "暂无基线结果")
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
            tooltip: {
                trigger: "axis",
                axisPointer: {type: "shadow"}
            },
            grid: {left: 34, right: 16, top: 10, bottom: 8},
            xAxis: {
                type: "value",
                splitLine: {lineStyle: {color: "rgba(94, 138, 182, 0.16)"}},
                axisLabel: {color: "rgba(204, 225, 246, 0.72)"}
            },
            yAxis: {
                type: "category",
                data: labels,
                axisTick: {show: false},
                axisLine: {show: false},
                axisLabel: {color: "#ebf6ff"}
            },
            series: [{
                type: "bar",
                barWidth: 14,
                data: values.map(function (value, index) {
                    return {
                        value: value,
                        itemStyle: {
                            borderRadius: [0, 10, 10, 0],
                            color: ["#60a5fa", "#facc15", "#fb7185", "#f97316"][index]
                        }
                    };
                })
            }],
            graphic: emptyGraphic(values.some(function (value) { return value > 0; }), "暂无漏洞分级")
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
                left: "6%",
                top: 8,
                bottom: 8,
                width: "88%",
                minSize: "36%",
                maxSize: "100%",
                sort: "descending",
                gap: 4,
                label: {
                    show: true,
                    position: "inside",
                    color: "#07111e",
                    fontSize: 11,
                    formatter: function (params) {
                        return params.name + "\n" + params.value;
                    }
                },
                itemStyle: {
                    borderColor: "rgba(4,12,22,0.95)",
                    borderWidth: 2
                },
                data: data
            }],
            graphic: emptyGraphic(data.length, "暂无处置漏斗")
        }, true);
    }

    function renderTrends(trends) {
        var normalizedTrends = normalizeTrends(trends);
        var container = document.getElementById("threatTrendsGrid");
        container.innerHTML = normalizedTrends.map(function (trend) {
            return '<article class="threat-trend-card">'
                + '<h3>' + escapeHtml(trend.label || "-") + '</h3>'
                + '<div class="threat-trend-chart" id="trendChart_' + escapeHtml(trend.key || "x") + '"></div>'
                + '</article>';
        }).join("");

        Object.keys(trendCharts).forEach(function (key) {
            if (trendCharts[key]) {
                trendCharts[key].dispose();
            }
        });
        trendCharts = {};

        normalizedTrends.forEach(function (trend) {
            var element = document.getElementById("trendChart_" + trend.key);
            if (!element) {
                return;
            }
            var chart = echarts.init(element);
            trendCharts[trend.key] = chart;
            chart.setOption({
                color: [resolveTrendColor(trend.tone)],
                grid: {left: 28, right: 10, top: 14, bottom: 24},
                tooltip: {trigger: "axis"},
                xAxis: {
                    type: "category",
                    data: (trend.points || []).map(function (item) { return item.label || "-"; }),
                    axisLine: {lineStyle: {color: "rgba(94, 138, 182, 0.18)"}},
                    axisLabel: {color: "rgba(204, 225, 246, 0.66)", fontSize: 10}
                },
                yAxis: {
                    type: "value",
                    splitLine: {lineStyle: {color: "rgba(94, 138, 182, 0.14)"}},
                    axisLabel: {color: "rgba(204, 225, 246, 0.62)", fontSize: 10}
                },
                series: [{
                    type: "line",
                    smooth: true,
                    symbol: "circle",
                    symbolSize: 6,
                    lineStyle: {width: 2.4},
                    showSymbol: true,
                    animationDuration: 900,
                    animationEasing: "cubicOut",
                    areaStyle: {
                        color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
                            {offset: 0, color: resolveTrendAreaColor(trend.tone, 0.34)},
                            {offset: 1, color: resolveTrendAreaColor(trend.tone, 0.02)}
                        ])
                    },
                    data: (trend.points || []).map(function (item) { return item.count || 0; })
                }],
                graphic: emptyGraphic(hasTrendData(trend.points), "暂无趋势数据")
            }, true);
        });
    }

    function renderEventFeed(items) {
        var normalized = Array.isArray(items) ? items.slice() : [];
        if (!normalized.length) {
            document.getElementById("threatEventFeedTrack").innerHTML = '<div class="threat-feed-item"><div class="threat-feed-content"><div class="threat-feed-title">暂无实时事件</div></div></div>';
            return;
        }
        var doubled = normalized.concat(normalized);
        document.getElementById("threatEventFeedTrack").innerHTML = doubled.map(function (item) {
            return '<article class="threat-feed-item">'
                + '<div class="threat-feed-badge level-' + escapeHtml(item.level || "medium") + '">' + escapeHtml(formatSource(item.sourceType)) + '</div>'
                + '<div class="threat-feed-content">'
                + '<div class="threat-feed-title">' + escapeHtml(item.title || "-") + '</div>'
                + '<div class="threat-feed-meta">' + escapeHtml(buildEventFeedMeta(item)) + '</div>'
                + '<div class="threat-feed-detail">' + escapeHtml(item.detail || "-") + '</div>'
                + '</div>'
                + '</article>';
        }).join("");
    }

    function buildCoreGraphic(core) {
        var isPlatform = numeric(core.totalTenants) > 0;
        return [{
            type: "circle",
            left: "center",
            top: "middle",
            shape: {r: 164},
            silent: true,
            style: {
                stroke: "rgba(37, 99, 235, 0.18)",
                lineWidth: 1.6,
                fill: "transparent"
            }
        }, {
            type: "circle",
            left: "center",
            top: "middle",
            shape: {r: 122},
            silent: true,
            style: {
                stroke: "rgba(34, 211, 238, 0.16)",
                lineWidth: 1.4,
                fill: "transparent"
            }
        }, {
            type: "circle",
            left: "center",
            top: "middle",
            shape: {r: 92},
            style: {
                fill: "rgba(4, 18, 32, 0.88)",
                stroke: "rgba(34, 211, 238, 0.28)",
                lineWidth: 2.4,
                shadowBlur: 34,
                shadowColor: "rgba(34,211,238,0.24)"
            }
        }, {
            type: "circle",
            left: "center",
            top: "middle",
            shape: {r: 74},
            silent: true,
            style: {
                stroke: "rgba(125, 245, 255, 0.14)",
                lineWidth: 1,
                fill: "rgba(8, 26, 42, 0.36)"
            }
        }, {
            type: "text",
            left: "center",
            top: "38%",
            style: {
                text: safeText(core.platformScore, "0"),
                fill: "#ffffff",
                fontSize: 46,
                fontWeight: 800,
                textAlign: "center"
            }
        }, {
            type: "text",
            left: "center",
            top: "47%",
            style: {
                text: "Platform Risk Score",
                fill: "rgba(216,230,248,0.74)",
                fontSize: 13,
                textAlign: "center"
            }
        }, {
            type: "text",
            left: "center",
            top: "57.5%",
            style: {
                text: "风险主机 " + safeText(core.riskyHostCount, "0")
                    + "  |  漏洞 " + safeText(core.todayVulnCount, "0")
                    + "  |  告警 " + safeText(core.todayAlertCount, "0"),
                fill: "rgba(190,210,236,0.7)",
                fontSize: 12,
                textAlign: "center"
            }
        }, {
            type: "text",
            left: "center",
            top: "63%",
            style: {
                text: isPlatform
                    ? "企业 " + safeText(core.totalTenants, "0") + "  |  风险企业 " + safeText(core.riskyTenantCount, "0")
                    : "总主机 " + safeText(core.totalHosts, "0") + "  |  总资产 " + safeText(core.totalAssets, "0"),
                fill: "rgba(136, 219, 244, 0.62)",
                fontSize: 11,
                textAlign: "center"
            }
        }];
    }

    function buildCoreLayout(nodes, viewMode) {
        var radiusX = 31;
        var radiusY = 24;
        var centerX = 50;
        var centerY = 50;
        var total = Math.max(nodes.length, 1);
        var data = [];
        var lines = [];

        nodes.forEach(function (node, index) {
            var angle = -Math.PI / 2 + (Math.PI * 2 * index) / total;
            var radiusBoost = Math.min(6, numeric(node.riskScore) / 22);
            var x = centerX + Math.cos(angle) * (radiusX + radiusBoost);
            var y = centerY + Math.sin(angle) * (radiusY + radiusBoost * 0.6);
            var symbolSize = Math.max(16, Math.min(36, 15 + numeric(node.assetCount) * 0.04 + numeric(node.riskScore) * 0.14));
            var color = resolveRiskColor(node.riskLevel);
            var labelPosition = x >= centerX ? "right" : "left";

            data.push({
                name: viewMode === "platform"
                    ? (node.tenantName || node.hostName || ("Tenant#" + safeText(node.tenantId, "")))
                    : (node.hostName || ("Host#" + safeText(node.hostId, ""))),
                value: [x, y, numeric(node.riskScore)],
                symbolSize: symbolSize,
                itemStyle: {
                    color: color,
                    shadowColor: color
                },
                host: node,
                labelPosition: labelPosition
            });

            lines.push({
                coords: [[x, y], [centerX, centerY]],
                lineStyle: {
                    color: color,
                    width: Math.max(1.1, Math.min(2.8, 1 + numeric(node.riskScore) / 42)),
                    opacity: 0.24 + numeric(node.riskScore) / 240
                },
                effect: {
                    color: color
                }
            });
        });

        return {
            nodes: data,
            lines: lines
        };
    }

    function buildEventFeedMeta(item) {
        var pieces = [];
        if (item.tenantName) {
            pieces.push(item.tenantName);
        }
        pieces.push(item.hostName || "-");
        pieces.push(item.ipv4 || "-");
        pieces.push(formatDateTime(item.eventTime));
        return pieces.join(" / ");
    }

    function buildSignalFlowLines() {
        return [{
            coords: [[16, 22], [50, 50]]
        }, {
            coords: [[16, 78], [50, 50]]
        }, {
            coords: [[84, 22], [50, 50]]
        }, {
            coords: [[84, 78], [50, 50]]
        }];
    }

    function buildSignalAnchors() {
        return [{
            value: [16, 22, 10],
            signalLabel: "BASELINE"
        }, {
            value: [16, 78, 10],
            signalLabel: "VULNERABILITY"
        }, {
            value: [84, 22, 10],
            signalLabel: "PATCH"
        }, {
            value: [84, 78, 10],
            signalLabel: "LOG"
        }];
    }

    function resolveRiskColor(level) {
        if (level === "critical") {
            return "#f97316";
        }
        if (level === "high") {
            return "#fb7185";
        }
        if (level === "medium") {
            return "#facc15";
        }
        return "#22d3ee";
    }

    function trimLabel(text, maxLength) {
        var value = safeText(text, "-");
        return value.length > maxLength ? value.slice(0, maxLength) + "..." : value;
    }

    function resolveRiskCategoryIndex(level) {
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

    function formatPrimarySignal(signal) {
        if (signal === "vuln") {
            return "漏洞";
        }
        if (signal === "patch") {
            return "补丁";
        }
        if (signal === "log") {
            return "日志";
        }
        if (signal === "baseline") {
            return "基线";
        }
        return "安全";
    }

    function formatSource(source) {
        if (source === "VULN") {
            return "漏洞";
        }
        if (source === "PATCH") {
            return "补丁";
        }
        if (source === "LOG") {
            return "日志";
        }
        if (source === "BASELINE") {
            return "基线";
        }
        return "事件";
    }

    function resolveTrendColor(tone) {
        if (tone === "red") {
            return "#fb7185";
        }
        if (tone === "amber") {
            return "#f59e0b";
        }
        if (tone === "blue") {
            return "#60a5fa";
        }
        return "#22d3ee";
    }

    function resolveTrendAreaColor(tone, alpha) {
        if (tone === "red") {
            return "rgba(251, 113, 133, " + alpha + ")";
        }
        if (tone === "amber") {
            return "rgba(245, 158, 11, " + alpha + ")";
        }
        if (tone === "blue") {
            return "rgba(96, 165, 250, " + alpha + ")";
        }
        return "rgba(34, 211, 238, " + alpha + ")";
    }

    function normalizeTrends(trends) {
        var trendMap = {};
        (trends || []).forEach(function (trend) {
            trendMap[trend.key] = trend;
        });
        return [{
            key: "vuln",
            label: "漏洞趋势",
            tone: "red"
        }, {
            key: "patch",
            label: "补丁风险趋势",
            tone: "amber"
        }, {
            key: "log",
            label: "日志告警趋势",
            tone: "cyan"
        }, {
            key: "baseline",
            label: "基线风险趋势",
            tone: "blue"
        }].map(function (template) {
            var current = trendMap[template.key] || {};
            return {
                key: template.key,
                label: current.label || template.label,
                tone: current.tone || template.tone,
                points: Array.isArray(current.points) && current.points.length ? current.points : createZeroTrendPoints()
            };
        });
    }

    function createZeroTrendPoints() {
        var points = [];
        for (var i = 6; i >= 0; i--) {
            var current = new Date();
            current.setDate(current.getDate() - i);
            points.push({
                label: String(current.getMonth() + 1).padStart(2, "0") + "-" + String(current.getDate()).padStart(2, "0"),
                count: 0
            });
        }
        return points;
    }

    function hasTrendData(points) {
        return (points || []).some(function (item) {
            return numeric(item && item.count) > 0;
        });
    }

    function formatDateTime(value) {
        if (!value) {
            return "-";
        }
        return AppUtils && AppUtils.formatDateTime ? AppUtils.formatDateTime(value) : String(value);
    }

    function initMotionEffects() {
        motion.backdropCanvas = document.getElementById("threatBackdropCanvas");
        motion.stageCanvas = document.getElementById("threatStageParticles");
        if (!motion.backdropCanvas || !motion.stageCanvas) {
            return;
        }
        motion.backdropContext = motion.backdropCanvas.getContext("2d");
        motion.stageContext = motion.stageCanvas.getContext("2d");
        buildBackdropParticles();
        buildStageParticles();
        resizeMotionCanvases();
        startMotionLoop();
    }

    function buildBackdropParticles() {
        motion.backdropParticles = [];
        for (var i = 0; i < 42; i++) {
            motion.backdropParticles.push({
                x: Math.random(),
                y: Math.random(),
                radius: 0.8 + Math.random() * 2.6,
                speedX: (Math.random() - 0.5) * 0.0009,
                speedY: 0.0004 + Math.random() * 0.0016,
                alpha: 0.16 + Math.random() * 0.34
            });
        }
    }

    function buildStageParticles() {
        motion.stageParticles = [];
        for (var i = 0; i < 52; i++) {
            motion.stageParticles.push({
                orbit: 110 + Math.random() * 200,
                angle: Math.random() * Math.PI * 2,
                speed: 0.0025 + Math.random() * 0.004,
                size: 1.2 + Math.random() * 2.8,
                hue: i % 3 === 0 ? "255,255,255" : (i % 2 === 0 ? "34,211,238" : "96,165,250"),
                alpha: 0.24 + Math.random() * 0.48,
                ellipse: 0.55 + Math.random() * 0.55
            });
        }
    }

    function resizeMotionCanvases() {
        resizeCanvasToDisplaySize(motion.backdropCanvas);
        resizeCanvasToDisplaySize(motion.stageCanvas);
    }

    function resizeCanvasToDisplaySize(canvas) {
        if (!canvas) {
            return;
        }
        var rect = canvas.getBoundingClientRect();
        var ratio = Math.max(1, Math.min(window.devicePixelRatio || 1, 2));
        canvas.width = Math.max(1, Math.floor(rect.width * ratio));
        canvas.height = Math.max(1, Math.floor(rect.height * ratio));
        var context = canvas.getContext("2d");
        context.setTransform(ratio, 0, 0, ratio, 0, 0);
    }

    function startMotionLoop() {
        if (motion.rafId) {
            window.cancelAnimationFrame(motion.rafId);
        }
        motion.lastTimestamp = 0;
        motion.rafId = window.requestAnimationFrame(stepMotion);
    }

    function stepMotion(timestamp) {
        if (!motion.lastTimestamp) {
            motion.lastTimestamp = timestamp;
        }
        var delta = Math.min(32, timestamp - motion.lastTimestamp);
        motion.lastTimestamp = timestamp;
        drawBackdrop(delta);
        drawStage(delta);
        motion.rafId = window.requestAnimationFrame(stepMotion);
    }

    function drawBackdrop(delta) {
        if (!motion.backdropCanvas || !motion.backdropContext) {
            return;
        }
        var canvas = motion.backdropCanvas;
        var context = motion.backdropContext;
        var width = canvas.clientWidth;
        var height = canvas.clientHeight;
        context.clearRect(0, 0, width, height);

        motion.backdropParticles.forEach(function (particle) {
            particle.x += particle.speedX * delta;
            particle.y += particle.speedY * delta;
            if (particle.y > 1.06) {
                particle.y = -0.06;
                particle.x = Math.random();
            }
            if (particle.x < -0.04) {
                particle.x = 1.04;
            } else if (particle.x > 1.04) {
                particle.x = -0.04;
            }
        });

        for (var i = 0; i < motion.backdropParticles.length; i++) {
            var current = motion.backdropParticles[i];
            var currentX = current.x * width;
            var currentY = current.y * height;
            context.beginPath();
            context.fillStyle = "rgba(140, 233, 255, " + current.alpha + ")";
            context.arc(currentX, currentY, current.radius, 0, Math.PI * 2);
            context.fill();

            for (var j = i + 1; j < motion.backdropParticles.length; j++) {
                var target = motion.backdropParticles[j];
                var dx = current.x - target.x;
                var dy = current.y - target.y;
                var distance = Math.sqrt(dx * dx + dy * dy);
                if (distance < 0.16) {
                    context.beginPath();
                    context.strokeStyle = "rgba(52, 184, 255, " + ((0.16 - distance) * 0.18) + ")";
                    context.lineWidth = 1;
                    context.moveTo(currentX, currentY);
                    context.lineTo(target.x * width, target.y * height);
                    context.stroke();
                }
            }
        }
    }

    function drawStage(delta) {
        if (!motion.stageCanvas || !motion.stageContext) {
            return;
        }
        var canvas = motion.stageCanvas;
        var context = motion.stageContext;
        var width = canvas.clientWidth;
        var height = canvas.clientHeight;
        var centerX = width / 2;
        var centerY = height / 2;
        context.clearRect(0, 0, width, height);

        motion.stageParticles.forEach(function (particle, index) {
            particle.angle += particle.speed * delta * (0.65 + motion.stageIntensity);
            var x = centerX + Math.cos(particle.angle + index * 0.03) * particle.orbit;
            var y = centerY + Math.sin(particle.angle) * particle.orbit * particle.ellipse;
            var glow = 0.15 + ((Math.sin(particle.angle * 2) + 1) / 2) * particle.alpha;

            context.beginPath();
            context.fillStyle = "rgba(" + particle.hue + ", " + glow + ")";
            context.shadowBlur = 10;
            context.shadowColor = "rgba(" + particle.hue + ", 0.3)";
            context.arc(x, y, particle.size, 0, Math.PI * 2);
            context.fill();

            context.beginPath();
            context.strokeStyle = "rgba(34, 211, 238, " + (0.04 + glow * 0.08) + ")";
            context.lineWidth = 0.8;
            context.moveTo(centerX, centerY);
            context.lineTo(x, y);
            context.stroke();
            context.shadowBlur = 0;
        });
    }

    function toggleFullscreen() {
        if (!document.fullscreenElement) {
            document.documentElement.requestFullscreen().catch(function () {
                layer.msg("浏览器不支持全屏切换", {icon: 0});
            });
            return;
        }
        document.exitFullscreen().catch(function () {
            layer.msg("退出全屏失败", {icon: 2});
        });
    }

    function syncFullscreenButtonLabel() {
        document.getElementById("toggleFullscreenButton").textContent = document.fullscreenElement ? "退出全屏" : "全屏";
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
                fill: "rgba(176,198,228,0.72)",
                fontSize: 13
            }
        }];
    }

    function resizeCharts() {
        resizeMotionCanvases();
        Object.keys(charts).forEach(function (key) {
            if (charts[key]) {
                charts[key].resize();
            }
        });
        Object.keys(trendCharts).forEach(function (key) {
            if (trendCharts[key]) {
                trendCharts[key].resize();
            }
        });
    }

    function setLoading(loading) {
        if (loading) {
            if (loadingIndex == null) {
                loadingIndex = layer.load(1, {shade: [0.08, "#040b16"]});
            }
            return;
        }
        if (loadingIndex != null) {
            layer.close(loadingIndex);
            loadingIndex = null;
        }
    }

    function destroyThreatScreen() {
        if (autoRefreshTimer != null) {
            window.clearInterval(autoRefreshTimer);
            autoRefreshTimer = null;
        }
        if (motion.rafId) {
            window.cancelAnimationFrame(motion.rafId);
            motion.rafId = null;
        }
        Object.keys(charts).forEach(function (key) {
            if (charts[key]) {
                charts[key].dispose();
                charts[key] = null;
            }
        });
        Object.keys(trendCharts).forEach(function (key) {
            if (trendCharts[key]) {
                trendCharts[key].dispose();
                trendCharts[key] = null;
            }
        });
    }

    function numeric(value) {
        return value == null ? 0 : Number(value) || 0;
    }

    function animateMetricValues() {
        Array.prototype.forEach.call(document.querySelectorAll(".threat-metric-card-value"), function (element) {
            var targetText = element.getAttribute("data-value") || element.textContent;
            var numericTarget = Number(String(targetText).replace(/,/g, ""));
            if (!Number.isFinite(numericTarget)) {
                element.textContent = targetText;
                return;
            }
            var startValue = Number(element.getAttribute("data-current")) || 0;
            var startTime = performance.now();
            var duration = 720;

            function tick(now) {
                var progress = Math.min(1, (now - startTime) / duration);
                var eased = 1 - Math.pow(1 - progress, 3);
                var currentValue = Math.round(startValue + (numericTarget - startValue) * eased);
                element.textContent = formatMetricNumber(targetText, currentValue);
                if (progress < 1) {
                    window.requestAnimationFrame(tick);
                    return;
                }
                element.setAttribute("data-current", String(numericTarget));
                element.textContent = targetText;
            }

            window.requestAnimationFrame(tick);
        });
    }

    function formatMetricNumber(targetText, value) {
        if (String(targetText).indexOf(",") > -1) {
            return value.toLocaleString("en-US");
        }
        return String(value);
    }

    function defaultMetrics() {
        return [{
            label: "平台风险指数",
            value: "-",
            subText: "综合评估当前全网风险热度",
            tone: "cyan"
        }, {
            label: "风险主机数",
            value: "-",
            subText: "按漏洞、基线、补丁、日志联合评分",
            tone: "amber"
        }, {
            label: "今日新增漏洞",
            value: "-",
            subText: "按 host_vuln_result 当日新增统计",
            tone: "red"
        }, {
            label: "基线异常数",
            value: "-",
            subText: "当前 FAIL / ERROR 基线结果总量",
            tone: "amber"
        }, {
            label: "日志告警数",
            value: "-",
            subText: "security_alerts 当日新增告警",
            tone: "cyan"
        }];
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
