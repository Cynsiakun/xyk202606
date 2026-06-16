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
        currentMenus: "/api/rbac/menu/current",
        currentPermissions: "/api/rbac/permission/current",
        currentLicense: "/api/license/current"
    };

    var pageFeatureMap = [
        {pattern: "host.html", feature: "HOST_VIEW"},
        {pattern: "user.html", feature: "USER_MANAGE"},
        {pattern: "role.html", feature: "ROLE_MANAGE"},
        {pattern: "permission.html", feature: "ROLE_MANAGE"},
        {pattern: "asset-", feature: "ASSET_MANAGE"},
        {pattern: "patch-", feature: "PATCH"},
        {pattern: "cve-", feature: "PATCH"},
        {pattern: "vuln-", feature: "VULN"},
        {pattern: "log.html", feature: "LOG"},
        {pattern: "security-log", feature: "LOG"},
        {pattern: "security-event", feature: "LOG"},
        {pattern: "account-change-log", feature: "LOG"},
        {pattern: "login-security-log", feature: "LOG"},
        {pattern: "host-log", feature: "LOG"},
        {pattern: "baseline-", feature: "BASELINE"}
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
        bindFrameGuard();
        fillCurrentUser();
        renderLicenseStatus(null);

        await syncCurrentUserFromApi();
        await loadPermissions();
        await loadCurrentLicense();
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

    async function loadMenus() {
        try {
            var result = await AppRequest.request(API_CONFIG.currentMenus, {method: "GET"});
            var menus = normalizeMenus(result.data || []);
            renderMenus(menus);
            bindMenuEvents();
            element.render("nav");
            activateDefaultMenu();
        } catch (error) {
            layer.msg("\u83dc\u5355\u52a0\u8f7d\u5931\u8d25", {icon: 2});
        }
    }

    function normalizeMenus(menus) {
        var filtered = filterMenusByLicense(menus);
        if (AppAuth.isSuperAdmin()) {
            filtered.push({
                title: "\u5e73\u53f0\u7ba1\u7406",
                icon: "layui-icon-component",
                children: [
                    {
                        title: "\u79df\u6237\u7ba1\u7406",
                        page: "./pages/platform-tenant.html",
                        icon: "layui-icon-template-1"
                    }
                ]
            });
        }
        return filtered;
    }

    function filterMenusByLicense(menus) {
        return (menus || []).map(function (menu) {
            var copied = Object.assign({}, menu);
            if (Array.isArray(menu.children) && menu.children.length > 0) {
                copied.children = filterMenusByLicense(menu.children);
                return copied.children.length > 0 ? copied : null;
            }
            return isMenuAllowed(copied) ? copied : null;
        }).filter(Boolean);
    }

    function isMenuAllowed(menu) {
        var page = String(menu.page || "");
        if (!page) {
            return true;
        }
        if (page.indexOf("dashboard.html") > -1 || page.indexOf("profile.html") > -1) {
            return true;
        }
        var feature = findRequiredFeature(page);
        return !feature || AppAuth.hasFeature(feature);
    }

    function findRequiredFeature(page) {
        for (var i = 0; i < pageFeatureMap.length; i++) {
            if (page.indexOf(pageFeatureMap[i].pattern) > -1) {
                return pageFeatureMap[i].feature;
            }
        }
        return null;
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
        tenantBadge.textContent = tenantId == null ? "Tenant -" : "Tenant " + tenantId;
        setPillState(tenantBadge, "muted");

        if (!info) {
            licenseBadge.textContent = "License loading";
            quotaBadge.textContent = "Quota -";
            setPillState(licenseBadge, "muted");
            setPillState(quotaBadge, "muted");
            return;
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
        setPillState(licenseBadge, info.edition === "TRIAL" ? "warn" : "");
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
