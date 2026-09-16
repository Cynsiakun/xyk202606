layui.use(["element", "layer"], function () {
    var element = layui.element;
    var layer = layui.layer;
    var contentFrame = document.getElementById("contentFrame");
    var pageTitle = document.getElementById("pageTitle");
    var pageDescription = document.getElementById("pageDescription");
    var currentUserName = document.getElementById("currentUserName");
    var sideNav = document.getElementById("sideNav");
    var menuToggle = document.getElementById("menuToggle");
    var appSidebar = document.getElementById("appSidebar");
    var logoutButton = document.getElementById("logoutButton");
    var profileButton = document.getElementById("profileButton");
    var tenantBadge = document.getElementById("tenantBadge");
    var licenseBadge = document.getElementById("licenseBadge");
    var quotaBadge = document.getElementById("quotaBadge");

    var API_CONFIG = {
        currentUser: "/api/current-user",
        logout: "/api/user/logout",
        currentPermissions: "/api/rbac/permission/current",
        currentLicense: "/api/license/current",
        currentAccess: "/api/access/effective"
    };

    var MENU_SCHEMA = [
        {title: "仪表盘", page: "./pages/dashboard.html", icon: "layui-icon-chart", policyKey: "COMMON_DASHBOARD_VIEW"},
        {title: "主机管理", page: "./pages/host.html", icon: "layui-icon-component", policyKey: "TENANT_HOST_VIEW"},
        {title: "授权主机", page: "./pages/tenant-machine.html", icon: "layui-icon-auz", policyKey: "TENANT_MACHINE_VIEW"},
        {title: "资产管理", icon: "layui-icon-tabs", children: [
            {title: "账号资产", page: "./pages/asset-account.html", icon: "layui-icon-user", policyKey: "TENANT_ASSET_VIEW"},
            {title: "服务资产", page: "./pages/asset-service.html", icon: "layui-icon-engine", policyKey: "TENANT_ASSET_VIEW"},
            {title: "进程资产", page: "./pages/asset-process.html", icon: "layui-icon-console", policyKey: "TENANT_ASSET_VIEW"},
            {title: "应用资产", page: "./pages/asset-app.html", icon: "layui-icon-app", policyKey: "TENANT_ASSET_VIEW"},
            {title: "端口资产", page: "./pages/asset-port.html", icon: "layui-icon-release", policyKey: "TENANT_ASSET_VIEW"},
            {title: "资产统计概览", page: "./pages/asset-statistics.html", icon: "layui-icon-chart-screen", policyKey: "TENANT_ASSET_STATS_VIEW"},
            {title: "端口资产规则管理", page: "./pages/asset-fingerprint-rule.html", icon: "layui-icon-auz", policyKey: "PLATFORM_ASSET_FINGERPRINT_RULE_VIEW"}
        ]},
        {title: "补丁安全", icon: "layui-icon-vercode", children: [
            {title: "补丁风险", page: "./pages/patch-security.html", icon: "layui-icon-shield", policyKey: "TENANT_PATCH_VIEW"},
            {title: "补丁管理", page: "./pages/patch-management.html", icon: "layui-icon-list", policyKey: "TENANT_PATCH_VIEW"}
        ]},
        {title: "漏洞管理", icon: "layui-icon-search", children: [
            {title: "漏洞检测", page: "./pages/vuln-detection.html", icon: "layui-icon-search", policyKey: "TENANT_VULN_VIEW"},
            {title: "漏洞运营仪表盘", page: "./pages/vuln-ops-dashboard.html", icon: "layui-icon-chart-screen", policyKey: "TENANT_VULN_DASHBOARD_VIEW"}
        ]},
        {title: "日志与事件", icon: "layui-icon-log", children: [
            {title: "安全日志中心", page: "./pages/security-log-center.html", icon: "layui-icon-log", policyKey: "TENANT_LOG_VIEW"},
            {title: "登录日志", page: "./pages/log.html", icon: "layui-icon-date", policyKey: "TENANT_LOG_VIEW"},
            {title: "登录安全", page: "./pages/login-security-log.html", icon: "layui-icon-password", policyKey: "TENANT_LOG_VIEW"},
            {title: "账号变更", page: "./pages/account-change-log.html", icon: "layui-icon-user", policyKey: "TENANT_LOG_VIEW"},
            {title: "安全事件", page: "./pages/security-event.html", icon: "layui-icon-notice", policyKey: "TENANT_LOG_VIEW"},
            {title: "主机日志", page: "./pages/host-log.html", icon: "layui-icon-file-b", policyKey: "TENANT_LOG_VIEW"}
        ]},
        {title: "基线合规", icon: "layui-icon-survey", children: [
            {title: "主机合规总览", page: "./pages/baseline-host.html", icon: "layui-icon-survey", policyKey: "TENANT_BASELINE_VIEW"},
            {title: "基线任务", page: "./pages/baseline-task.html", icon: "layui-icon-template", policyKey: "TENANT_BASELINE_VIEW"},
            {title: "整改工单", page: "./pages/baseline-workorder.html", icon: "layui-icon-form", policyKey: "TENANT_BASELINE_WORKORDER_VIEW"}
        ]},
        {title: "用户管理", page: "./pages/user.html", icon: "layui-icon-username", policyKey: "TENANT_USER_VIEW"},
        {title: "套餐中心", page: "./pages/license-center.html", icon: "layui-icon-diamond", policyKey: "COMMON_PROFILE_VIEW"},
        {title: "平台管理", icon: "layui-icon-component", children: [
            {title: "租户管理", page: "./pages/platform-tenant.html", icon: "layui-icon-template-1", policyKey: "PLATFORM_TENANT_VIEW"},
            {title: "CVE 管理", page: "./pages/cve-management.html", icon: "layui-icon-dialogue", policyKey: "PLATFORM_CVE_VIEW"},
            {title: "漏洞库管理", page: "./pages/vuln-rule-management.html", icon: "layui-icon-table", policyKey: "PLATFORM_VULN_RULE_VIEW"},
            {title: "基线规则管理", page: "./pages/baseline-rule.html", icon: "layui-icon-list", policyKey: "PLATFORM_BASELINE_RULE_VIEW"},
            {title: "角色管理", page: "./pages/role.html", icon: "layui-icon-group", policyKey: "PLATFORM_RBAC_VIEW"},
            {title: "权限管理", page: "./pages/permission.html", icon: "layui-icon-auz", policyKey: "PLATFORM_RBAC_VIEW"}
        ]},
        {title: "个人中心", page: "./pages/profile.html", icon: "layui-icon-user", policyKey: "COMMON_PROFILE_VIEW"}
    ];

    init();

    async function init() {
        if (!AppAuth.isLoggedIn()) {
            AppAuth.redirectToLogin();
            return;
        }

        bindSidebarToggle();
        bindLogout();
        bindProfileButton();
        bindLicenseStatus();
        bindFrameGuard();
        fillCurrentUser();
        renderLicenseStatus(null);

        await syncCurrentUserFromApi();
        await loadPermissions();
        await loadCurrentAccess();
        await loadMenus();
    }

    async function loadPermissions() {
        try {
            var result = await AppRequest.request(API_CONFIG.currentPermissions, {method: "GET"});
            AppAuth.setPermissions(result.data || []);
        } catch (error) {
            AppAuth.setPermissions([]);
        }
    }

    async function loadCurrentLicense() {
        try {
            var result = await AppRequest.request(API_CONFIG.currentLicense, {method: "GET"}, {showErrorMessage: false});
            AppAuth.setLicenseInfo(result.data || null);
            renderLicenseStatus(result.data || null);
        } catch (error) {
            AppAuth.setLicenseInfo(null);
            renderLicenseStatus(null);
        }
    }

    async function loadCurrentAccess() {
        try {
            var result = await AppRequest.request(API_CONFIG.currentAccess, {method: "GET"}, {showErrorMessage: false});
            var info = result.data || null;
            AppAuth.setAccessInfo(info);
            AppAuth.setLicenseInfo(info);
            renderLicenseStatus(info);
        } catch (error) {
            AppAuth.setAccessInfo(null);
            await loadCurrentLicense();
        }
    }

    async function loadMenus() {
        try {
            var menus = normalizeMenus(MENU_SCHEMA);
            renderMenus(menus);
            bindMenuEvents();
            element.render("nav");
            activateDefaultMenu();
        } catch (error) {
            layer.msg("\u83dc\u5355\u52a0\u8f7d\u5931\u8d25", {icon: 2});
        }
    }

    function normalizeMenus(menus) {
        var filtered = filterMenusByPolicy(menus);
        logMenuSnapshot(filtered);
        return filtered;
    }

    function filterMenusByPolicy(menus) {
        return (menus || []).map(function (menu) {
            var copied = Object.assign({}, menu);
            if (Array.isArray(menu.children) && menu.children.length > 0) {
                copied.children = filterMenusByPolicy(menu.children);
                return copied.children.length > 0 ? copied : null;
            }
            return isPolicyAllowed(copied.policyKey) ? copied : null;
        }).filter(Boolean);
    }

    function isPolicyAllowed(policyKey) {
        if (!policyKey) {
            return true;
        }
        if (policyKey === "PLATFORM_ASSET_FINGERPRINT_RULE_VIEW") {
            return AppAuth.isSuperAdmin() && AppAuth.getTenantId() === 0 && AppAuth.canPolicy(policyKey);
        }
        return AppAuth.canPolicy(policyKey);
    }

    function logMenuSnapshot(menus) {
        if (!window.console || !console.info) {
            return;
        }
        var access = AppAuth.getAccessInfo() || {};
        console.info("[ACCESS MENU SNAPSHOT]", {
            tenant: access.tenantName || access.tenantId || "-",
            edition: access.edition || "-",
            allowedPolicies: access.allowedPolicies || [],
            visibleMenus: flattenMenuTitles(menus)
        });
    }

    function flattenMenuTitles(menus) {
        var result = [];
        (menus || []).forEach(function (menu) {
            if (menu.children && menu.children.length) {
                menu.children.forEach(function (child) {
                    result.push(menu.title + " / " + child.title);
                });
            } else {
                result.push(menu.title);
            }
        });
        return result;
    }

    function bindMenuEvents() {
        sideNav.querySelectorAll("a[data-page]").forEach(function (link) {
            link.addEventListener("click", function () {
                switchTo(link);
            });
        });
    }

    function bindSidebarToggle() {
        menuToggle.addEventListener("click", function () {
            appSidebar.classList.toggle("is-collapsed");
        });
    }

    function bindLogout() {
        logoutButton.addEventListener("click", async function () {
            try {
                await AppRequest.request(API_CONFIG.logout, {method: "POST"});
            } finally {
                AppAuth.clearLogin();
                AppAuth.redirectToLogin();
            }
        });
    }

    function bindProfileButton() {
        profileButton.addEventListener("click", function () {
            var link = sideNav.querySelector('a[data-page="./pages/profile.html"]');
            if (link) {
                switchTo(link);
            }
        });
    }

    function bindLicenseStatus() {
        document.getElementById("licenseStrip").addEventListener("click", function () {
            openLicenseStatusDialog();
        });
    }

    function bindFrameGuard() {
        contentFrame.addEventListener("load", function () {
            try {
                var frameLocation = contentFrame.contentWindow.location.href;
                if (frameLocation === "http://localhost:8080/" || frameLocation === "http://127.0.0.1:8080/") {
                    AppAuth.redirectToLogin();
                }
            } catch (error) {
                return null;
            }
            return null;
        });
    }

    function fillCurrentUser() {
        currentUserName.textContent = AppAuth.getCurrentUserName() || "\u7ba1\u7406\u5458";
    }

    async function syncCurrentUserFromApi() {
        try {
            var result = await AppRequest.request(API_CONFIG.currentUser, {method: "GET"});
            if (result && result.data) {
                currentUserName.textContent = result.data.userName || currentUserName.textContent;
                localStorage.setItem("currentUserName", currentUserName.textContent);
                if (result.data.tenantName) {
                    AppAuth.setTenantName(result.data.tenantName);
                }
            }
        } catch (error) {
            return null;
        }
    }

    function renderMenus(menus) {
        sideNav.innerHTML = buildMenuHtml(menus);
    }

    function buildMenuHtml(menus) {
        return menus.map(function (menu) {
            var hasChildren = menu.children && menu.children.length > 0;

            if (hasChildren) {
                var childHtml = '<dl class="layui-nav-child">'
                    + menu.children.map(function (child) {
                        return '<dd>'
                            + '<a href="javascript:;" data-title="' + escapeAttr(child.title) + '" data-page="' + escapeAttr(child.page) + '">'
                            + (child.icon ? '<i class="layui-icon ' + escapeAttr(child.icon) + '"></i>' : "")
                            + '<span>' + escapeHtml(child.title) + "</span>"
                            + "</a>"
                            + "</dd>";
                    }).join("")
                    + "</dl>";

                return '<li class="layui-nav-item">'
                    + '<a href="javascript:;">'
                    + (menu.icon ? '<i class="layui-icon ' + escapeAttr(menu.icon) + '"></i>' : "")
                    + '<span>' + escapeHtml(menu.title) + "</span>"
                    + "</a>"
                    + childHtml
                    + "</li>";
            }

            return '<li class="layui-nav-item">'
                + '<a href="javascript:;" data-title="' + escapeAttr(menu.title) + '" data-page="' + escapeAttr(menu.page) + '">'
                + (menu.icon ? '<i class="layui-icon ' + escapeAttr(menu.icon) + '"></i>' : "")
                + '<span>' + escapeHtml(menu.title) + "</span>"
                + "</a>"
                + "</li>";
        }).join("");
    }

    function activateDefaultMenu() {
        var defaultLink = sideNav.querySelector("a[data-page]");
        if (defaultLink) {
            switchTo(defaultLink);
        }
    }

    function switchTo(link) {
        var title = link.dataset.title;
        var page = link.dataset.page;
        setActiveMenu(link);
        pageTitle.textContent = title;
        pageDescription.textContent = "";
        contentFrame.src = page;
    }

    function navigateToPage(page, title) {
        var link = sideNav.querySelector('a[data-page="' + page + '"]');
        if (link) {
            switchTo(link);
            return;
        }
        pageTitle.textContent = title || "";
        pageDescription.textContent = "";
        contentFrame.src = page;
    }

    function setActiveMenu(link) {
        sideNav.querySelectorAll(".layui-nav-item, .layui-nav-child dd").forEach(function (item) {
            item.classList.remove("layui-this");
        });

        var parent = link.parentElement;
        if (parent.tagName === "DD") {
            parent.classList.add("layui-this");
            var navItem = parent.closest(".layui-nav-item");
            if (navItem) {
                navItem.classList.add("layui-nav-itemed");
            }
        } else {
            parent.classList.add("layui-this");
        }

        element.render("nav");
    }

    function renderLicenseStatus(info) {
        var tenantId = AppAuth.getTenantId();
        var tenantName = info && info.tenantName ? info.tenantName : AppAuth.getTenantName();
        tenantBadge.textContent = tenantName || (tenantId == null ? "租户 -" : "租户 " + tenantId);
        setPillState(tenantBadge, "muted");

        if (!info) {
            licenseBadge.textContent = "License loading";
            quotaBadge.textContent = "Quota -";
            setPillState(licenseBadge, "muted");
            setPillState(quotaBadge, "muted");
            return;
        }

        if (info.tenantName) {
            AppAuth.setTenantName(info.tenantName);
            tenantBadge.textContent = info.tenantName;
        }

        if (info.effective === false) {
            licenseBadge.textContent = "\u672a\u6388\u6743";
            quotaBadge.textContent = info.message || "\u65e0\u6709\u6548 License";
            setPillState(licenseBadge, "danger");
            setPillState(quotaBadge, "danger");
            return;
        }

        licenseBadge.textContent = formatEdition(info.edition) + formatExpire(info.expireTime);
        quotaBadge.textContent = "\u4e3b\u673a " + formatQuota(info.hostUsed, info.hostLimit)
            + " / \u7528\u6237 " + formatQuota(info.userUsed, info.userLimit);
        setPillState(licenseBadge, getExpireLevel(info.expireTime) || (info.edition === "TRIAL" ? "warn" : ""));
        setPillState(quotaBadge, quotaIsNearLimit(info) ? "warn" : "");
    }

    function setPillState(element, state) {
        element.classList.remove("muted", "warn", "danger");
        if (state) {
            element.classList.add(state);
        }
    }

    function formatEdition(edition) {
        var labels = {
            PLATFORM: "\u5e73\u53f0\u79df\u6237",
            TRIAL: "Trial",
            STANDARD: "Standard",
            PROFESSIONAL: "Professional",
            NONE: "\u672a\u6388\u6743"
        };
        return labels[edition] || edition || "-";
    }

    function formatExpire(expireTime) {
        if (!expireTime) {
            return "";
        }
        return " · " + AppUtils.formatDateTime(expireTime).slice(0, 10);
    }

    function openLicenseStatusDialog() {
        var info = AppAuth.getAccessInfo() || AppAuth.getLicenseInfo() || {};
        var expireLevel = getExpireLevel(info.expireTime);
        var html = '<div class="license-status-dialog">'
            + '<div class="license-status-grid">'
            + statusItem("当前版本", formatEdition(info.edition))
            + statusItem("到期时间", info.expireTime ? AppUtils.formatDateTime(info.expireTime) : "长期有效")
            + statusItem("状态", formatLicenseState(info))
            + statusItem("主机配额", formatQuota(info.hostUsed, info.hostLimit))
            + statusItem("用户配额", formatQuota(info.userUsed, info.userLimit))
            + statusItem("租户", info.tenantName || AppAuth.getTenantName() || "-")
            + "</div>"
            + expireAlertHtml(info.expireTime, expireLevel)
            + '<div class="license-status-actions">'
            + '<button type="button" class="layui-btn layui-btn-primary" data-action="license-center">套餐中心</button>'
            + purchaseButtonsHtml()
            + "</div>"
            + "</div>";
        var index = layer.open({
            type: 1,
            title: "License 状态",
            area: ["620px", "430px"],
            content: html,
            success: function (layero) {
                layero.find('[data-action="license-center"]').on("click", function () {
                    layer.close(index);
                    navigateToPage("./pages/license-center.html", "套餐中心");
                });
                layero.find('[data-action="license-contact"]').on("click", function () {
                    layer.msg("请联系销售或管理员开通服务", {icon: 0});
                });
            }
        });
    }

    function purchaseButtonsHtml() {
        if (AppAuth.isSuperAdmin() || !AppAuth.isTenantAdmin()) {
            return "";
        }
        return '<button type="button" class="layui-btn" data-action="license-contact">升级套餐</button>'
            + '<button type="button" class="layui-btn layui-btn-normal" data-action="license-contact">续费</button>'
            + '<button type="button" class="layui-btn layui-btn-warm" data-action="license-contact">联系销售</button>';
    }

    function statusItem(label, value) {
        return '<div class="license-status-item">'
            + '<div class="license-status-label">' + escapeHtml(label) + '</div>'
            + '<div class="license-status-value">' + escapeHtml(value == null || value === "" ? "-" : value) + '</div>'
            + '</div>';
    }

    function formatLicenseState(info) {
        if (!info || info.effective === false) {
            return info && info.message ? info.message : "未授权";
        }
        if (info.status === 0) {
            return "已禁用";
        }
        return "有效";
    }

    function expireAlertHtml(expireTime, level) {
        var days = daysUntilExpire(expireTime);
        if (!level || days == null) {
            return "";
        }
        return '<div class="license-status-alert ' + level + '">授权将在 ' + days + ' 天后到期，请及时续费。</div>';
    }

    function getExpireLevel(expireTime) {
        var days = daysUntilExpire(expireTime);
        if (days == null) {
            return "";
        }
        if (days <= 7) {
            return "danger";
        }
        if (days <= 30) {
            return "warn";
        }
        return "";
    }

    function daysUntilExpire(expireTime) {
        if (!expireTime) {
            return null;
        }
        var expire = new Date(expireTime).getTime();
        if (Number.isNaN(expire)) {
            return null;
        }
        return Math.ceil((expire - Date.now()) / 86400000);
    }

    function formatQuota(used, limit) {
        var current = Number(used || 0);
        if (Number(limit || 0) === 0) {
            return current + "/\u4e0d\u9650";
        }
        return current + "/" + limit;
    }

    function quotaIsNearLimit(info) {
        return isNearLimit(info.hostUsed, info.hostLimit) || isNearLimit(info.userUsed, info.userLimit);
    }

    function isNearLimit(used, limit) {
        var safeLimit = Number(limit || 0);
        return safeLimit > 0 && Number(used || 0) / safeLimit >= 0.8;
    }

    function escapeHtml(text) {
        return String(text == null ? "" : text)
            .replace(/&/g, "&amp;")
            .replace(/</g, "&lt;")
            .replace(/>/g, "&gt;")
            .replace(/"/g, "&quot;")
            .replace(/'/g, "&#39;");
    }

    function escapeAttr(text) {
        return escapeHtml(text).replace(/`/g, "&#96;");
    }

    window.AppShell = {
        navigateToAlert: function (alertId) {
            var page = "./pages/security-event.html";
            var link = sideNav.querySelector('a[data-page="' + page + '"]');
            if (link) {
                setActiveMenu(link);
                pageTitle.textContent = link.dataset.title || "\u5b89\u5168\u4e8b\u4ef6";
                pageDescription.textContent = "";
            } else {
                pageTitle.textContent = "\u5b89\u5168\u4e8b\u4ef6";
            }
            contentFrame.src = page + "?focus=" + encodeURIComponent(alertId);
        },
        refreshLicense: loadCurrentLicense
    };
});
