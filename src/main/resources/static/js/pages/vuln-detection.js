layui.use(["layer"], function () {
    var layer = layui.layer;
    var currentView = "host";
    var currentFilter = "all";
    var searchKeyword = "";
    var vulnSearchKeyword = "";
    var selectedSeverities = [];
    var selectedVulnStatuses = [];
    var hostCards = [];
    var vulnCards = [];
    var affectedHostsByRule = {};
    var expandedRules = {};
    var selectedResultsByRule = {};
    var pollingTimer = null;
    var activeButtons = {};
    var lastMetricValues = {};

    var API = {
        summary: "/api/vuln-detection/summary",
        hosts: "/api/vuln-detection/hosts",
        vulnerabilities: "/api/vuln-detection/vulnerabilities",
        verifyResults: "/api/vuln-verification/results/verify",
        ignoreResults: "/api/vuln-detection/results/ignore",
        evaluateAll: "/api/vuln-detection/evaluate-all",
        batchVerify: "/api/vuln-verification/batch-verify",
        verifyHost: function (hostId) {
            return "/api/vuln-verification/hosts/" + hostId + "/verify";
        },
        verifyResult: function (hostId, resultId) {
            return "/api/vuln-verification/hosts/" + hostId + "/results/" + resultId + "/verify";
        },
        hostDetail: function (hostId) {
            return "/api/vuln-detection/hosts/" + hostId + "/results";
        },
        vulnHosts: function (ruleId) {
            return "/api/vuln-detection/vulnerabilities/" + ruleId + "/hosts";
        },
        fixHost: function (hostId) {
            return "/api/vuln-detection/hosts/" + hostId + "/fix";
        },
        fixVuln: function (ruleId) {
            return "/api/vuln-detection/vulnerabilities/" + ruleId + "/fix";
        }
    };

    init();

    function init() {
        bindEvents();
        refreshPage(false, true);
        pollingTimer = window.setInterval(function () {
            refreshPage(false, false);
        }, 6000);
        window.addEventListener("beforeunload", function () {
            if (pollingTimer) {
                window.clearInterval(pollingTimer);
            }
        });
    }

    function bindEvents() {
        document.getElementById("refreshButton").addEventListener("click", function () {
            refreshPage(false, true);
        });

        document.getElementById("rematchButton").addEventListener("click", function () {
            confirmAction("重新匹配", "重新匹配会基于当前资产和规则库重建静态结果，确认继续？", function () {
                refreshPage(true, true);
            });
        });

        document.getElementById("batchVerifyButton").addEventListener("click", function () {
            if (currentView === "vuln") {
                confirmAction("批量验证", "将对当前筛选结果中的所有可验证漏洞主机下发任务，确认继续？", function () {
                    verifyFilteredVulns(document.getElementById("batchVerifyButton"));
                });
                return;
            }
            var hostIds = filteredHosts()
                .filter(canDispatchHost)
                .map(function (host) { return host.hostId; })
                .filter(Boolean);
            if (hostIds.length === 0) {
                AppRequest.showMessage("当前没有可下发验证的主机", 0, 1800);
                return;
            }
            confirmAction("批量验证", "将对当前筛选结果中的所有可验证漏洞下发验证任务，确认继续？", function () {
                batchVerify(hostIds);
            });
        });

        document.getElementById("viewSwitch").addEventListener("click", function (event) {
            var tab = event.target.closest(".view-tab");
            if (!tab) return;
            currentView = tab.dataset.view || "host";
            document.querySelectorAll(".view-tab").forEach(function (item) {
                item.classList.toggle("is-active", item === tab);
            });
            document.getElementById("hostDashboard").style.display = currentView === "host" ? "" : "none";
            document.getElementById("hostViewPanel").classList.toggle("is-active", currentView === "host");
            document.getElementById("vulnViewPanel").classList.toggle("is-active", currentView === "vuln");
            renderVulns();
        });

        document.getElementById("hostSearchInput").addEventListener("input", function (event) {
            searchKeyword = event.target.value.trim().toLowerCase();
            renderHosts();
        });

        document.getElementById("vulnSearchInput").addEventListener("input", function (event) {
            vulnSearchKeyword = event.target.value.trim().toLowerCase();
            renderVulns();
        });

        bindMultiFilter("severityFilter", selectedSeverities, renderVulns);
        bindMultiFilter("vulnStatusFilter", selectedVulnStatuses, renderVulns);

        document.getElementById("filterTabs").addEventListener("click", function (event) {
            var tab = event.target.closest(".filter-tab");
            if (!tab) return;
            document.querySelectorAll(".filter-tab").forEach(function (item) {
                item.classList.remove("is-active");
            });
            tab.classList.add("is-active");
            currentFilter = tab.dataset.filter || "all";
            renderHosts();
        });

        document.getElementById("hostCardGrid").addEventListener("click", function (event) {
            var verifyButton = event.target.closest("[data-action='verify-host']");
            if (verifyButton) {
                event.stopPropagation();
                var hostId = verifyButton.dataset.hostId;
                var label = verifyButton.dataset.retry === "true" ? "重新验证" : "下发验证";
                confirmAction(label, "将对该主机所有可验证漏洞下发任务，确认继续？", function () {
                    verifyHost(hostId, verifyButton);
                });
                return;
            }

            var fixHostButton = event.target.closest("[data-action='fix-host']");
            if (fixHostButton) {
                event.stopPropagation();
                var hostId = fixHostButton.dataset.hostId;
                var count = fixHostButton.dataset.count || "0";
                confirmAction("一键修复", "将修复该主机上 " + count + " 条已验证漏洞，确认继续？", function () {
                    fixHost(hostId, fixHostButton);
                });
                return;
            }

            var detailButton = event.target.closest("[data-action='view-detail']");
            if (detailButton) {
                event.stopPropagation();
                openHostDetail(detailButton.dataset.hostId);
                return;
            }

            var card = event.target.closest(".host-card");
            if (card) {
                openHostDetail(card.dataset.hostId);
            }
        });

        document.addEventListener("click", function (event) {
            var copyButton = event.target.closest("[data-action='copy-code']");
            if (copyButton) {
                copyText(copyButton.dataset.code || copyButton.textContent);
                return;
            }

            var evidenceButton = event.target.closest("[data-action='toggle-evidence']");
            if (evidenceButton) {
                var item = evidenceButton.closest(".vuln-item");
                var evidence = item ? item.querySelector(".evidence-block") : null;
                if (evidence) {
                    evidence.classList.toggle("is-open");
                    evidenceButton.textContent = evidence.classList.contains("is-open") ? "收起证据" : "证据";
                }
                return;
            }

            var resultButton = event.target.closest("[data-action='verify-result']");
            if (resultButton) {
                confirmAction("验证漏洞", "将只对该漏洞下发验证任务，确认继续？", function () {
                    verifyResult(resultButton.dataset.hostId, resultButton.dataset.resultId, resultButton);
                });
                return;
            }

            var vulnToggle = event.target.closest("[data-action='toggle-vuln-hosts']");
            if (vulnToggle) {
                toggleVulnHosts(vulnToggle.dataset.ruleId, vulnToggle);
                return;
            }

            var vulnVerify = event.target.closest("[data-action='verify-vuln']");
            if (vulnVerify) {
                var ruleId = vulnVerify.dataset.ruleId;
                confirmAction("一键下发验证", "将对该漏洞的所有可验证主机下发任务，确认继续？", function () {
                    verifyRule(ruleId, vulnVerify);
                });
                return;
            }

            var vulnFix = event.target.closest("[data-action='fix-vuln']");
            if (vulnFix) {
                var ruleId = vulnFix.dataset.ruleId;
                var count = vulnFix.dataset.count || "0";
                confirmAction("一键修复", "将修复该漏洞下 " + count + " 条已验证结果，确认继续？", function () {
                    fixVuln(ruleId, vulnFix);
                });
                return;
            }

            var rowVerify = event.target.closest("[data-action='verify-vuln-host']");
            if (rowVerify) {
                var text = rowVerify.dataset.retry === "true" ? "重新验证" : "下发验证";
                confirmAction(text, "将只对该漏洞在该主机上的结果下发验证任务，确认继续？", function () {
                    verifyResults([rowVerify.dataset.resultId], rowVerify);
                });
                return;
            }

            var selectAll = event.target.closest("[data-action='select-rule-all']");
            if (selectAll) {
                toggleRuleSelection(selectAll.dataset.ruleId, selectAll.checked);
                return;
            }

            var resultCheck = event.target.closest("[data-action='select-result']");
            if (resultCheck) {
                updateResultSelection(resultCheck.dataset.ruleId, resultCheck.dataset.resultId, resultCheck.checked);
                return;
            }

            var bulkVerify = event.target.closest("[data-action='bulk-verify-rule']");
            if (bulkVerify) {
                var verifyIds = selectedResultIdsForRule(bulkVerify.dataset.ruleId);
                if (verifyIds.length === 0) {
                    AppRequest.showMessage("请先选择要验证的主机", 0, 1800);
                    return;
                }
                confirmAction("批量下发验证", "将对已选主机下发验证任务，确认继续？", function () {
                    verifyResults(verifyIds, bulkVerify);
                });
                return;
            }

            var bulkIgnore = event.target.closest("[data-action='bulk-ignore-rule']");
            if (bulkIgnore) {
                var ignoreIds = selectedResultIdsForRule(bulkIgnore.dataset.ruleId);
                if (ignoreIds.length === 0) {
                    AppRequest.showMessage("请先选择要忽略的主机", 0, 1800);
                    return;
                }
                confirmAction("批量忽略", "将忽略已选漏洞结果，确认继续？", function () {
                    ignoreResults(ignoreIds, bulkIgnore);
                });
            }
        });
    }

    function bindMultiFilter(id, selectedValues, onChange) {
        var container = document.getElementById(id);
        if (!container) return;
        container.addEventListener("click", function (event) {
            var button = event.target.closest("button[data-value]");
            if (!button) return;
            var value = button.dataset.value;
            var index = selectedValues.indexOf(value);
            if (index >= 0) {
                selectedValues.splice(index, 1);
                button.classList.remove("is-active");
            } else {
                selectedValues.push(value);
                button.classList.add("is-active");
            }
            updateMultiFilterLabel(container, selectedValues);
            onChange();
        });
    }

    function updateMultiFilterLabel(container, selectedValues) {
        var summary = container.querySelector("summary");
        if (!summary) return;
        var base = container.id === "severityFilter" ? "风险等级" : "状态筛选";
        summary.textContent = selectedValues.length > 0 ? base + " · " + selectedValues.length : base;
    }

    async function refreshPage(runEvaluate, showLoading) {
        var loadingIndex = showLoading ? layer.load(2) : null;
        try {
            if (runEvaluate && AppAuth.hasPermission("vuln-detection:analyze")) {
                await AppRequest.request(API.evaluateAll, {method: "POST"}, {
                    showErrorMessage: false,
                    successMessage: "静态规则匹配已刷新"
                });
            }
            await Promise.all([loadSummary(), loadHosts(), loadVulns()]);
        } finally {
            if (loadingIndex !== null) {
                layer.close(loadingIndex);
            }
        }
    }

    async function loadSummary() {
        try {
            var result = await AppRequest.request(API.summary, {method: "GET"}, {
                showErrorMessage: false,
                redirectOnUnauthorized: false
            });
            renderSummary(result.data || {});
        } catch (error) {
            renderSummary({});
        }
    }

    async function loadHosts() {
        try {
            var result = await AppRequest.request(API.hosts, {method: "GET"}, {
                showErrorMessage: false,
                redirectOnUnauthorized: false
            });
            hostCards = Array.isArray(result.data) ? result.data : [];
        } catch (error) {
            hostCards = [];
        }
        renderHosts();
    }

    async function loadVulns() {
        try {
            var result = await AppRequest.request(API.vulnerabilities, {method: "GET"}, {
                showErrorMessage: false,
                redirectOnUnauthorized: false
            });
            vulnCards = Array.isArray(result.data) ? result.data : [];
        } catch (error) {
            vulnCards = [];
        }
        renderVulns();
    }

    function renderSummary(summary) {
        var data = Object.assign({
            criticalCount: 0,
            highCount: 0,
            mediumCount: 0,
            lowCount: 0,
            pendingCount: 0,
            verifyingCount: 0,
            verifiedCount: 0,
            repairCount: 0,
            fixedCount: 0
        }, summary || {});

        var riskTotal = sum(data.criticalCount, data.highCount, data.mediumCount, data.lowCount);
        var statusTotal = sum(data.pendingCount, data.verifyingCount, data.verifiedCount, data.repairCount, data.fixedCount);
        animateNumber("riskTotal", riskTotal);
        animateNumber("statusTotal", statusTotal);

        renderRiskSegment(data, riskTotal);
        document.getElementById("severityMetrics").innerHTML = [
            metricHtml("严重", data.criticalCount, "risk-critical"),
            metricHtml("高危", data.highCount, "risk-high"),
            metricHtml("中危", data.mediumCount, "risk-medium"),
            metricHtml("低危", data.lowCount, "risk-low")
        ].join("");

        document.getElementById("statusMetrics").innerHTML = [
            statusMetricHtml("待验证", data.pendingCount, "pending"),
            statusMetricHtml("验证中", data.verifyingCount, "verifying"),
            statusMetricHtml("已验证", data.verifiedCount, "verified"),
            statusMetricHtml("待修复", data.repairCount, "repair"),
            statusMetricHtml("已修复", data.fixedCount, "fixed")
        ].join("");
        animateMetricNumbers();
    }

    function renderRiskSegment(data, total) {
        var parts = [
            {cls: "critical", value: data.criticalCount},
            {cls: "high", value: data.highCount},
            {cls: "medium", value: data.mediumCount},
            {cls: "low", value: data.lowCount}
        ];
        document.getElementById("riskSegment").innerHTML = parts.map(function (part) {
            var width = total > 0 ? Math.max(3, Math.round(numberValue(part.value) * 100 / total)) : 0;
            return '<span class="risk-segment-part ' + part.cls + '" style="width:' + width + '%"></span>';
        }).join("");
    }

    function renderHosts() {
        var rows = filteredHosts();
        var grid = document.getElementById("hostCardGrid");
        var empty = document.getElementById("hostEmpty");

        if (rows.length === 0) {
            grid.innerHTML = "";
            empty.style.display = "flex";
            return;
        }

        empty.style.display = "none";
        grid.innerHTML = rows.map(renderHostCard).join("");
        animateMetricNumbers(grid);
    }

    function renderVulns() {
        var rows = filteredVulns();
        var grid = document.getElementById("vulnCardGrid");
        var empty = document.getElementById("vulnEmpty");
        if (!grid || !empty) return;

        renderVulnSummary(rows);
        if (rows.length === 0) {
            grid.innerHTML = "";
            empty.style.display = "flex";
            return;
        }

        empty.style.display = "none";
        grid.innerHTML = rows.map(renderVulnCard).join("");
        rows.forEach(function (vuln) {
            if (expandedRules[String(vuln.ruleId)]) {
                renderAffectedHosts(vuln.ruleId);
            }
        });
        animateMetricNumbers(grid);
    }

    function renderVulnSummary(rows) {
        var total = rows.length;
        var pending = rows.filter(function (item) { return numberValue(item.pendingCount) > 0; }).length;
        var verifying = rows.filter(function (item) { return numberValue(item.verifyingCount) > 0; }).length;
        var repair = rows.filter(function (item) { return numberValue(item.repairCount) > 0; }).length;
        animateNumber("vulnTotalKinds", total);
        animateNumber("vulnPendingKinds", pending);
        animateNumber("vulnVerifyingKinds", verifying);
        animateNumber("vulnRepairKinds", repair);
    }

    function filteredVulns() {
        return vulnCards.filter(function (vuln) {
            return matchVulnSearch(vuln) && matchVulnSeverity(vuln) && matchVulnStatus(vuln);
        });
    }

    function matchVulnSearch(vuln) {
        if (!vulnSearchKeyword) return true;
        return [
            vuln.title,
            vuln.cveId,
            vuln.ruleCode,
            vuln.ruleId,
            vuln.productName
        ].filter(Boolean).join(" ").toLowerCase().indexOf(vulnSearchKeyword) >= 0;
    }

    function matchVulnSeverity(vuln) {
        if (selectedSeverities.length === 0) return true;
        return selectedSeverities.indexOf(normalizeSeverity(vuln.severity)) >= 0;
    }

    function matchVulnStatus(vuln) {
        if (selectedVulnStatuses.length === 0) return true;
        return selectedVulnStatuses.some(function (status) {
            if (status === "pending") return numberValue(vuln.pendingCount) > 0;
            if (status === "verifying") return numberValue(vuln.verifyingCount) > 0;
            if (status === "repair") return numberValue(vuln.repairCount) > 0;
            return false;
        });
    }

    function renderVulnCard(vuln) {
        var ruleId = vuln.ruleId || "";
        var severity = normalizeSeverity(vuln.severity);
        var total = numberValue(vuln.totalHostCount);
        var pending = numberValue(vuln.pendingCount);
        var verifying = numberValue(vuln.verifyingCount);
        var repair = numberValue(vuln.repairCount);
        var fixed = numberValue(vuln.fixedCount);
        var fixedWidth = total > 0 ? Math.round(fixed * 100 / total) : 0;
        var expanded = !!expandedRules[String(ruleId)];
        return '<article class="vuln-card vuln-card-' + severity + (verifying > 0 ? ' is-verifying' : '') + '" data-rule-id="' + escapeHtml(ruleId) + '">'
            + '<div class="risk-top-line risk-' + severity + '"></div>'
            + '<div class="vuln-card-head">'
            + '<div class="vuln-title-block">'
            + '<div class="vuln-card-tags">' + renderSeverityTag(severity) + renderCveCode(vuln) + '</div>'
            + '<h3>' + escapeHtml(vuln.title || ("Rule #" + ruleId)) + '</h3>'
            + '<p title="' + escapeHtml(vuln.description || "-") + '">' + escapeHtml(vuln.description || "-") + '</p>'
            + '</div>'
            + '<div class="impact-number"><strong class="count-num" data-value="' + total + '">' + total + '</strong><span>影响主机</span></div>'
            + '</div>'
            + '<div class="vuln-status-row">'
            + statusMini("待验证", pending, "pending")
            + statusMini("验证中", verifying, "verifying")
            + statusMini("待修复", repair, "repair")
            + '</div>'
            + '<div class="fix-progress"><span style="width:' + fixedWidth + '%"></span></div>'
            + '<div class="vuln-card-foot">'
            + '<span>规则 #' + escapeHtml(ruleId) + ' · ' + escapeHtml(vuln.productType || "-") + ' / ' + escapeHtml(vuln.productName || "-") + '</span>'
            + '<div class="host-actions">'
            + '<button type="button" class="ghost-btn" data-action="toggle-vuln-hosts" data-rule-id="' + escapeHtml(ruleId) + '">' + (expanded ? "收起影响主机" : "查看影响主机") + '</button>'
            + '<button type="button" class="solid-btn" data-action="verify-vuln" data-rule-id="' + escapeHtml(ruleId) + '"' + (total > 0 ? "" : " disabled") + '>一键下发验证</button>'
            + (repair > 0 ? '<button type="button" class="solid-btn fix-btn" data-action="fix-vuln" data-count="' + repair + '" data-rule-id="' + escapeHtml(ruleId) + '">一键修复</button>' : '')
            + '</div>'
            + '</div>'
            + '<div class="affected-host-panel' + (expanded ? ' is-open' : '') + '" id="affectedHosts-' + escapeHtml(ruleId) + '">'
            + renderAffectedHostContent(ruleId)
            + '</div>'
            + '</article>';
    }

    function renderCveCode(vuln) {
        var code = vuln.cveId || vuln.ruleCode || ("#" + (vuln.ruleId || "-"));
        return '<button type="button" class="cve-code" title="点击复制" data-action="copy-code" data-code="' + escapeHtml(code) + '">' + escapeHtml(code) + '</button>';
    }

    function statusMini(label, value, state) {
        return '<div class="status-mini ' + state + '"><span>' + label + '</span><strong class="count-num" data-value="' + numberValue(value) + '">' + numberValue(value) + '</strong></div>';
    }

    function filteredHosts() {
        return hostCards.filter(function (host) {
            return matchFilter(host) && matchSearch(host);
        });
    }

    function matchSearch(host) {
        if (!searchKeyword) return true;
        return [host.hostname, host.ipv4, host.macAddress]
            .filter(Boolean)
            .join(" ")
            .toLowerCase()
            .indexOf(searchKeyword) >= 0;
    }

    function matchFilter(host) {
        if (currentFilter === "pending") return numberValue(host.pendingCount) > 0;
        if (currentFilter === "verifying") return numberValue(host.verifyingCount) > 0;
        if (currentFilter === "verified") return numberValue(host.verifiedCount) > 0;
        if (currentFilter === "repair") return numberValue(host.repairCount) > 0;
        if (currentFilter === "fixed") return numberValue(host.fixedCount) > 0;
        return true;
    }

    function renderHostCard(host) {
        var severity = normalizeSeverity(host.highestSeverity);
        var hostId = host.hostId || "";
        var total = numberValue(host.totalVulnCount);
        var pending = numberValue(host.pendingCount);
        var verifying = numberValue(host.verifyingCount);
        var verified = numberValue(host.verifiedCount);
        var repair = numberValue(host.repairCount);
        var fixed = numberValue(host.fixedCount);
        var retry = verifying > 0 || (pending === 0 && repair === 0 && (verified > 0 || fixed > 0));
        var actionText = retry ? "重新验证" : "下发验证";
        var disabled = canDispatchHost(host) ? "" : " disabled";

        return '<article class="host-card host-card-' + severity + (verifying > 0 ? ' is-verifying' : '') + '" data-host-id="' + escapeHtml(hostId) + '">'
            + '<div class="risk-top-line risk-' + severity + '"></div>'
            + '<div class="host-card-main">'
            + '<div class="host-icon"><i class="layui-icon layui-icon-engine"></i></div>'
            + '<div class="host-info">'
            + '<div class="host-name-row">'
            + '<h3>' + escapeHtml(host.hostname || ("Host #" + (hostId || "-"))) + '</h3>'
            + renderSeverityTag(severity)
            + '</div>'
            + '<div class="host-meta">'
            + metaTag(host.ipv4 || "-")
            + metaTag(host.macAddress || "-")
            + metaTag([host.osName, host.osVersion].filter(Boolean).join(" ") || "-")
            + '</div>'
            + '</div>'
            + '</div>'
            + '<div class="progress-stats">'
            + progressStat("漏洞", total, total, "total")
            + progressStat("待验证", pending, total, "pending")
            + progressStat("验证中", verifying, total, "verifying")
            + progressStat("已验证", verified, total, "verified")
            + progressStat("待修复", repair, total, "repair")
            + progressStat("已修复", fixed, total, "fixed")
            + '</div>'
            + '<div class="host-card-foot">'
            + '<span>最近检测：' + escapeHtml(formatDateTime(host.latestScanTime)) + '</span>'
            + '<div class="host-actions">'
            + '<button type="button" class="ghost-btn" data-action="view-detail" data-host-id="' + escapeHtml(hostId) + '">查看漏洞</button>'
            + '<button type="button" class="solid-btn" data-action="verify-host" data-retry="' + retry + '" data-host-id="' + escapeHtml(hostId) + '"' + disabled + '>' + actionText + '</button>'
            + (verified > 0 ? '<button type="button" class="solid-btn fix-btn" data-action="fix-host" data-count="' + verified + '" data-host-id="' + escapeHtml(hostId) + '">一键修复</button>' : '')
            + '</div>'
            + '</div>'
            + '</article>';
    }

    function renderVulnItem(item, hostId) {
        var severity = normalizeSeverity(item.severity);
        var state = normalizeStatus(item.verifyStatus, item.status);
        var canVerify = ["pending", "verifying", "verified", "repair", "fixed"].indexOf(state.key) >= 0;
        var actionText = ["verifying", "verified", "fixed"].indexOf(state.key) >= 0 ? "重新验证" : "验证";
        return '<article class="vuln-item">'
            + '<div class="vuln-item-head">'
            + '<div>'
            + '<h3>' + escapeHtml(item.vulnName || "-") + '</h3>'
            + '<div class="vuln-item-meta">'
            + '<span>资产：' + escapeHtml(item.productName || "-") + '</span>'
            + '<span>版本：' + escapeHtml(item.productVersion || "-") + '</span>'
            + '<span>规则：#' + escapeHtml(item.ruleId || "-") + '</span>'
            + '</div>'
            + '</div>'
            + '<div class="vuln-item-tags">' + renderSeverityTag(severity) + renderStatusTag(state) + '</div>'
            + '</div>'
            + '<p>' + escapeHtml(item.suggestion || "-") + '</p>'
            + '<div class="vuln-item-actions">'
            + '<button type="button" class="ghost-btn small" data-action="toggle-evidence">证据</button>'
            + '<button type="button" class="solid-btn small" data-action="verify-result" data-host-id="' + escapeHtml(hostId) + '" data-result-id="' + escapeHtml(item.id || "") + '"' + (canVerify ? "" : " disabled") + '>' + actionText + '</button>'
            + '</div>'
            + '<pre class="evidence-block">' + escapeHtml(formatEvidence(item.evidenceJson)) + '</pre>'
            + '</article>';
    }

    async function toggleVulnHosts(ruleId, button) {
        if (!ruleId) return;
        var key = String(ruleId);
        expandedRules[key] = !expandedRules[key];
        if (expandedRules[key] && !affectedHostsByRule[key]) {
            await withButtonLoading(button, async function () {
                await loadAffectedHosts(ruleId);
            });
        }
        renderVulns();
    }

    async function loadAffectedHosts(ruleId) {
        try {
            var result = await AppRequest.request(API.vulnHosts(ruleId), {method: "GET"}, {
                showErrorMessage: false,
                redirectOnUnauthorized: false
            });
            affectedHostsByRule[String(ruleId)] = Array.isArray(result.data) ? result.data : [];
        } catch (error) {
            affectedHostsByRule[String(ruleId)] = [];
        }
    }

    function renderAffectedHosts(ruleId) {
        var panel = document.getElementById("affectedHosts-" + ruleId);
        if (panel) {
            panel.innerHTML = renderAffectedHostContent(ruleId);
        }
    }

    function renderAffectedHostContent(ruleId) {
        var rows = affectedHostsByRule[String(ruleId)];
        if (!rows) {
            return '<div class="affected-loading"><i class="layui-icon layui-icon-loading layui-anim layui-anim-rotate layui-anim-loop"></i> 加载影响主机...</div>';
        }
        if (rows.length === 0) {
            return '<div class="detail-empty">暂无影响主机。</div>';
        }
        var selected = selectedResultsByRule[String(ruleId)] || {};
        var allChecked = rows.length > 0 && rows.every(function (row) { return !!selected[String(row.resultId)]; });
        return '<div class="affected-host-list">'
            + rows.map(function (row) { return renderAffectedHostRow(ruleId, row, selected); }).join("")
            + '</div>'
            + '<div class="affected-bulk-bar">'
            + '<label><input type="checkbox" data-action="select-rule-all" data-rule-id="' + escapeHtml(ruleId) + '"' + (allChecked ? " checked" : "") + '> 全选</label>'
            + '<div class="host-actions">'
            + '<button type="button" class="solid-btn small" data-action="bulk-verify-rule" data-rule-id="' + escapeHtml(ruleId) + '">批量下发验证</button>'
            + '<button type="button" class="ghost-btn small" data-action="bulk-ignore-rule" data-rule-id="' + escapeHtml(ruleId) + '">批量忽略</button>'
            + '</div>'
            + '</div>';
    }

    function renderAffectedHostRow(ruleId, row, selected) {
        var state = normalizeVulnPerspectiveStatus(row.verifyStatus, row.status);
        var retry = state.key === "verifying";
        var actionText = retry ? "重新验证" : "下发验证";
        var checked = selected[String(row.resultId)] ? " checked" : "";
        var matchedCount = numberValue(row.matchedItemCount);
        var matchedSummary = buildMatchedItemSummary(row);
        return '<div class="affected-host-row">'
            + '<label class="affected-check"><input type="checkbox" data-action="select-result" data-rule-id="' + escapeHtml(ruleId) + '" data-result-id="' + escapeHtml(row.resultId || "") + '"' + checked + '></label>'
            + '<div class="affected-host-main">'
            + '<strong>' + escapeHtml(row.hostname || ("Host #" + (row.hostId || "-"))) + '</strong>'
            + '<span>' + escapeHtml(row.ipv4 || "-") + ' · ' + escapeHtml(row.macAddress || "-") + '</span>'
            + (matchedCount > 1 || matchedSummary ? '<div class="affected-host-hit">命中 ' + matchedCount + ' 项' + (matchedSummary ? ' · ' + escapeHtml(matchedSummary) : '') + '</div>' : '')
            + '</div>'
            + renderStatusTag(state)
            + '<button type="button" class="solid-btn small" data-action="verify-vuln-host" data-retry="' + retry + '" data-result-id="' + escapeHtml(row.resultId || "") + '">' + actionText + '</button>'
            + '</div>';
    }

    function toggleRuleSelection(ruleId, checked) {
        var key = String(ruleId);
        selectedResultsByRule[key] = selectedResultsByRule[key] || {};
        (affectedHostsByRule[key] || []).forEach(function (row) {
            if (checked) {
                selectedResultsByRule[key][String(row.resultId)] = true;
            } else {
                delete selectedResultsByRule[key][String(row.resultId)];
            }
        });
        renderAffectedHosts(ruleId);
    }

    function updateResultSelection(ruleId, resultId, checked) {
        var key = String(ruleId);
        selectedResultsByRule[key] = selectedResultsByRule[key] || {};
        if (checked) {
            selectedResultsByRule[key][String(resultId)] = true;
        } else {
            delete selectedResultsByRule[key][String(resultId)];
        }
        renderAffectedHosts(ruleId);
    }

    function selectedResultIdsForRule(ruleId) {
        var selected = selectedResultsByRule[String(ruleId)] || {};
        return Object.keys(selected).map(function (id) { return Number(id); }).filter(Boolean);
    }

    function collectDispatchableResultIdsForRule(ruleId) {
        var rows = affectedHostsByRule[String(ruleId)];
        if (!rows) {
            var card = vulnCards.find(function (item) { return String(item.ruleId) === String(ruleId); });
            return card && numberValue(card.totalHostCount) > 0 ? [] : [];
        }
        return rows.filter(canDispatchVulnHost).map(function (row) { return row.resultId; }).filter(Boolean);
    }

    function collectDispatchableVulnResultIds(rows) {
        var ids = [];
        rows.forEach(function (vuln) {
            var ruleId = String(vuln.ruleId);
            var hosts = affectedHostsByRule[ruleId] || [];
            hosts.filter(canDispatchVulnHost).forEach(function (row) {
                if (row.resultId) ids.push(row.resultId);
            });
        });
        return uniqueNumbers(ids);
    }

    function canDispatchVulnHost(row) {
        var state = normalizeVulnPerspectiveStatus(row.verifyStatus, row.status);
        return ["pending", "verifying", "repair"].indexOf(state.key) >= 0;
    }

    async function verifyFilteredVulns(button) {
        await withButtonLoading(button, async function () {
            var rows = filteredVulns();
            await ensureAffectedHostsLoaded(rows.map(function (item) { return item.ruleId; }));
            var resultIds = collectDispatchableVulnResultIds(rows);
            if (resultIds.length === 0) {
                AppRequest.showMessage("当前没有可下发验证的漏洞主机", 0, 1800);
                return;
            }
            await submitResultVerification(resultIds);
        });
    }

    async function verifyRule(ruleId, button) {
        await withButtonLoading(button, async function () {
            await ensureAffectedHostsLoaded([ruleId]);
            var resultIds = collectDispatchableResultIdsForRule(ruleId);
            if (resultIds.length === 0) {
                AppRequest.showMessage("该漏洞暂无可下发验证的主机", 0, 1800);
                return;
            }
            await submitResultVerification(resultIds);
        });
    }

    async function ensureAffectedHostsLoaded(ruleIds) {
        var ids = uniqueNumbers(ruleIds);
        await Promise.all(ids.map(function (ruleId) {
            return affectedHostsByRule[String(ruleId)] ? Promise.resolve() : loadAffectedHosts(ruleId);
        }));
    }

    async function verifyHost(hostId, button) {
        await withButtonLoading(button, async function () {
            var result = await AppRequest.request(API.verifyHost(hostId), {method: "POST"});
            showDispatchMessage(result);
            await refreshPage(false, false);
        });
    }

    async function fixHost(hostId, button) {
        await withButtonLoading(button, async function () {
            var result = await AppRequest.request(API.fixHost(hostId), {method: "POST"});
            var updated = numberValue(result.data && result.data.updated);
            AppRequest.showMessage("已下发修复任务，" + updated + " 条", 1, 2000);
            await refreshPage(false, false);
        });
    }

    async function fixVuln(ruleId, button) {
        await withButtonLoading(button, async function () {
            var result = await AppRequest.request(API.fixVuln(ruleId), {method: "POST"});
            var updated = numberValue(result.data && result.data.updated);
            AppRequest.showMessage("已下发修复任务，" + updated + " 条", 1, 2000);
            await refreshPage(false, false);
            await reloadExpandedAffectedHosts();
        });
    }

    async function verifyResult(hostId, resultId, button) {
        await withButtonLoading(button, async function () {
            var result = await AppRequest.request(API.verifyResult(hostId, resultId), {method: "POST"});
            showDispatchMessage(result);
            await refreshPage(false, false);
            layer.closeAll("page");
            openHostDetail(hostId);
        });
    }

    async function batchVerify(hostIds) {
        var button = document.getElementById("batchVerifyButton");
        await withButtonLoading(button, async function () {
            var result = await AppRequest.request(API.batchVerify, {
                method: "POST",
                body: {hostIds: hostIds}
            });
            var data = result.data || {};
            var successCount = Object.keys(data).filter(function (key) { return data[key] != null; }).length;
            AppRequest.showMessage("批量验证已提交：" + successCount + "/" + hostIds.length, successCount > 0 ? 1 : 2, 2200);
            await refreshPage(false, false);
        });
    }

    async function verifyResults(resultIds, button) {
        var ids = uniqueNumbers(resultIds);
        if (ids.length === 0) return;
        await withButtonLoading(button, async function () {
            await submitResultVerification(ids);
        });
    }

    async function submitResultVerification(ids) {
        var result = await AppRequest.request(API.verifyResults, {
            method: "POST",
            body: {resultIds: ids}
        });
        var data = result.data || {};
        var successCount = Object.keys(data).filter(function (key) { return data[key] != null; }).length;
        AppRequest.showMessage("验证任务已提交：" + successCount + "/" + ids.length, successCount > 0 ? 1 : 2, 2200);
        await refreshPage(false, false);
        await reloadExpandedAffectedHosts();
    }

    async function ignoreResults(resultIds, button) {
        var ids = uniqueNumbers(resultIds);
        if (ids.length === 0) return;
        await withButtonLoading(button, async function () {
            var result = await AppRequest.request(API.ignoreResults, {
                method: "POST",
                body: {resultIds: ids}
            });
            AppRequest.showMessage("已忽略 " + numberValue(result.data && result.data.updated) + " 条结果", 1, 1800);
            await refreshPage(false, false);
            await reloadExpandedAffectedHosts();
        });
    }

    async function reloadExpandedAffectedHosts() {
        var ruleIds = Object.keys(expandedRules).filter(function (ruleId) { return expandedRules[ruleId]; });
        await Promise.all(ruleIds.map(function (ruleId) { return loadAffectedHosts(ruleId); }));
        renderVulns();
    }

    async function openHostDetail(hostId) {
        if (!hostId) return;
        var loadingIndex = layer.load(2);
        try {
            var result = await AppRequest.request(API.hostDetail(hostId), {method: "GET"}, {
                showErrorMessage: false,
                redirectOnUnauthorized: false
            });
            layer.close(loadingIndex);
            showDetailDrawer(hostId, Array.isArray(result.data) ? result.data : []);
        } catch (error) {
            layer.close(loadingIndex);
            showDetailDrawer(hostId, []);
        }
    }

    function showDetailDrawer(hostId, vulnerabilities) {
        layer.open({
            type: 1,
            title: false,
            shadeClose: true,
            area: [getDrawerWidth(), "100%"],
            offset: "r",
            anim: 5,
            content: buildDetailHtml(hostId, vulnerabilities)
        });
    }

    function buildDetailHtml(hostId, vulnerabilities) {
        var host = hostCards.find(function (item) { return String(item.hostId) === String(hostId); }) || {};
        var content = vulnerabilities.length > 0
            ? vulnerabilities.map(function (item) { return renderVulnItem(item, hostId); }).join("")
            : '<div class="detail-empty">该主机暂无漏洞明细。</div>';
        return '<div class="host-detail-drawer">'
            + '<div class="drawer-head">'
            + '<div>'
            + '<div class="section-kicker">Host Detail</div>'
            + '<h2>' + escapeHtml(host.hostname || ("Host #" + hostId)) + '</h2>'
            + '<p>' + escapeHtml(host.ipv4 || "-") + ' / ' + escapeHtml(host.macAddress || "-") + '</p>'
            + '</div>'
            + '<div class="drawer-actions">' + renderSeverityTag(normalizeSeverity(host.highestSeverity)) + '</div>'
            + '</div>'
            + '<div class="drawer-summary">'
            + progressStat("漏洞", host.totalVulnCount, host.totalVulnCount, "total")
            + progressStat("待验证", host.pendingCount, host.totalVulnCount, "pending")
            + progressStat("验证中", host.verifyingCount, host.totalVulnCount, "verifying")
            + progressStat("已验证", host.verifiedCount, host.totalVulnCount, "verified")
            + progressStat("待修复", host.repairCount, host.totalVulnCount, "repair")
            + progressStat("已修复", host.fixedCount, host.totalVulnCount, "fixed")
            + '</div>'
            + '<div class="vuln-list">' + content + '</div>'
            + '</div>';
    }

    function confirmAction(title, message, onConfirm) {
        layer.confirm(message, {
            title: title,
            btn: ["确认", "取消"]
        }, function (index) {
            layer.close(index);
            onConfirm();
        });
    }

    async function withButtonLoading(button, task) {
        if (!button) {
            await task();
            return;
        }
        var key = button.dataset.loadingKey || Math.random().toString(36).slice(2);
        button.dataset.loadingKey = key;
        if (activeButtons[key]) return;
        activeButtons[key] = true;
        var oldHtml = button.innerHTML;
        button.disabled = true;
        button.classList.add("is-loading");
        button.innerHTML = '<i class="layui-icon layui-icon-loading layui-anim layui-anim-rotate layui-anim-loop"></i> 处理中';
        try {
            await task();
        } finally {
            button.disabled = false;
            button.classList.remove("is-loading");
            button.innerHTML = oldHtml;
            delete activeButtons[key];
        }
    }

    function showDispatchMessage(result) {
        if (result.data && result.data.sent === false) {
            AppRequest.showMessage(result.data.message || "任务已创建但下发失败，可稍后重试", 2, 2200);
        } else {
            AppRequest.showMessage(result.data && result.data.message ? result.data.message : "验证任务已下发", 1, 1800);
        }
    }

    function metricHtml(label, value, cls) {
        return '<div class="metric-card ' + cls + '"><span>' + label + '</span><strong class="count-num" data-value="' + numberValue(value) + '">' + numberValue(value) + '</strong></div>';
    }

    function statusMetricHtml(label, value, state) {
        return '<div class="status-counter"><span class="status-dot ' + state + '"></span><div><span>' + label + '</span><strong class="count-num" data-value="' + numberValue(value) + '">' + numberValue(value) + '</strong></div></div>';
    }

    function progressStat(label, value, total, cls) {
        var current = numberValue(value);
        var width = numberValue(total) > 0 ? Math.min(100, Math.round(current * 100 / numberValue(total))) : 0;
        return '<div class="progress-stat ' + cls + '"><div class="progress-stat-head"><span>' + label + '</span><strong class="count-num" data-value="' + current + '">' + current + '</strong></div><div class="mini-progress"><span style="width:' + width + '%"></span></div></div>';
    }

    function renderSeverityTag(severity) {
        var labelMap = {critical: "严重", high: "高危", medium: "中危", low: "低危", clean: "健康", unknown: "未知"};
        return '<span class="severity-tag severity-' + severity + '">' + (labelMap[severity] || "未知") + '</span>';
    }

    function renderStatusTag(state) {
        return '<span class="status-chip status-' + state.key + '">' + state.label + '</span>';
    }

    function normalizeStatus(verifyStatus, status) {
        if (Number(status) === 0) return {key: "fixed", label: "已修复"};
        var normalized = String(verifyStatus || "").toUpperCase();
        if (normalized === "VERIFYING") return {key: "verifying", label: "验证中"};
        if (normalized === "VERIFIED" || normalized === "NOT_AFFECTED") return {key: "verified", label: "已验证"};
        if (normalized === "REPAIR_PENDING" || normalized === "TO_FIX") return {key: "repair", label: "修复中"};
        if (normalized === "FIXED") return {key: "fixed", label: "已修复"};
        return {key: "pending", label: "待验证"};
    }

    function normalizeVulnPerspectiveStatus(verifyStatus, status) {
        if (Number(status) === 0) return {key: "fixed", label: "已修复"};
        var normalized = String(verifyStatus || "").toUpperCase();
        if (normalized === "VERIFYING") return {key: "verifying", label: "验证中"};
        if (normalized === "VERIFIED" || normalized === "REPAIR_PENDING" || normalized === "TO_FIX") return {key: "repair", label: "修复中"};
        if (normalized === "FIXED" || normalized === "NOT_AFFECTED") return {key: "fixed", label: "已修复"};
        return {key: "pending", label: "待验证"};
    }

    function normalizeSeverity(value) {
        var normalized = String(value || "").toLowerCase();
        if (["critical", "严重", "severe"].indexOf(normalized) >= 0) return "critical";
        if (["high", "高危"].indexOf(normalized) >= 0) return "high";
        if (["medium", "中危"].indexOf(normalized) >= 0) return "medium";
        if (["low", "低危"].indexOf(normalized) >= 0) return "low";
        if (["clean", "none", "healthy"].indexOf(normalized) >= 0) return "clean";
        return "unknown";
    }

    function canDispatchHost(host) {
        return numberValue(host.pendingRuleCount) > 0;
    }

    function metaTag(value) {
        return '<span>' + escapeHtml(value) + '</span>';
    }

    function animateNumber(id, nextValue) {
        var element = document.getElementById(id);
        if (!element) return;
        var previous = numberValue(lastMetricValues[id] == null ? element.textContent : lastMetricValues[id]);
        var next = numberValue(nextValue);
        lastMetricValues[id] = next;
        if (previous === next) {
            element.textContent = next;
            return;
        }
        var start = performance.now();
        var duration = 450;
        function tick(now) {
            var progress = Math.min(1, (now - start) / duration);
            element.textContent = Math.round(previous + (next - previous) * progress);
            if (progress < 1) requestAnimationFrame(tick);
        }
        requestAnimationFrame(tick);
    }

    function animateMetricNumbers(scope) {
        (scope || document).querySelectorAll(".count-num").forEach(function (element) {
            var next = numberValue(element.dataset.value);
            element.textContent = next;
            element.classList.add("pop");
            window.setTimeout(function () { element.classList.remove("pop"); }, 240);
        });
    }

    function formatEvidence(value) {
        if (!value) return "{}";
        if (typeof value === "object") return JSON.stringify(value, null, 2);
        try {
            return JSON.stringify(JSON.parse(value), null, 2);
        } catch (error) {
            return String(value);
        }
    }

    function buildMatchedItemSummary(row) {
        var summary = String(row && row.matchedItemSummary ? row.matchedItemSummary : "").trim();
        if (!summary) return "";
        return truncateText(summary, 72);
    }

    function truncateText(text, maxLength) {
        var value = String(text == null ? "" : text);
        if (!maxLength || value.length <= maxLength) return value;
        return value.slice(0, Math.max(0, maxLength - 1)) + "…";
    }

    function sum() {
        return Array.prototype.slice.call(arguments).reduce(function (acc, item) {
            return acc + numberValue(item);
        }, 0);
    }

    function numberValue(value) {
        var number = Number(value);
        return Number.isFinite(number) ? number : 0;
    }

    function uniqueNumbers(values) {
        var seen = {};
        return (values || []).map(function (value) {
            return Number(value);
        }).filter(function (value) {
            if (!Number.isFinite(value) || value <= 0 || seen[value]) return false;
            seen[value] = true;
            return true;
        });
    }

    function formatDateTime(value) {
        if (window.AppUtils && typeof window.AppUtils.formatDateTime === "function") {
            return window.AppUtils.formatDateTime(value);
        }
        return value || "-";
    }

    function copyText(text) {
        if (navigator.clipboard && navigator.clipboard.writeText) {
            navigator.clipboard.writeText(text).then(function () {
                AppRequest.showMessage("已复制", 1, 1000);
            });
        }
    }

    function getDrawerWidth() {
        return window.innerWidth < 760 ? "94%" : "760px";
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
