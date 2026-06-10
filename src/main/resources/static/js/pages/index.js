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

    var API_CONFIG = {
        currentUser: "/api/current-user",
        logout: "/api/user/logout",
        currentMenus: "/api/rbac/menu/current",
        currentPermissions: "/api/rbac/permission/current"
    };

    var menuDescriptions = {
        "后台首页": "查看用户、登录和活跃情况统计。",
        "用户管理": "维护后台用户，支持新增、编辑、删除、搜索和分页。",
        "登录日志": "审计登录成功和失败记录，支持用户名与状态筛选。",
        "个人信息": "查看并维护当前登录账户的基础资料和密码。",
        "角色管理": "角色管理模块。",
        "权限管理": "权限管理模块。",
        "主机管理": "查看与维护上报的主机系统信息，支持搜索、新增、编辑和删除。",
        "资产管理": "查看各主机资产探测记录，包括账号、服务、进程、APP。",
        "账号资产": "浏览与搜索主机账号探测记录，支持详情与删除。",
        "服务资产": "浏览与搜索主机服务探测记录，支持详情与删除。",
        "进程资产": "浏览与搜索主机进程探测记录，支持详情与删除。",
        "APP资产": "浏览与搜索主机安装软件探测记录，支持详情与删除。",
        "风险发现": "聚合安全风险入口，优先呈现需要处置的主机与证据。",
        "补丁安全": "查看存在补丁风险的主机、风险证据与处置建议，支持重新分析和补丁扫描。",
        "补丁管理": "维护主机已安装补丁记录，支持分页查询、筛选、编辑、详情与批量删除。",
        "CVE管理": "维护补丁与CVE映射情报，支持分页查询、筛选、编辑、详情与批量删除。",
        "漏洞检测": "展示漏洞规则命中结果，围绕待验证、验证中、已验证和待修复推进处置。",
        "漏洞库管理": "维护漏洞规则库，支持分页查询、条件检索、新增、编辑、查看与批量删除。",
        "漏洞运营仪表盘": "面向安全管理层的漏洞运营驾驶舱，聚焦趋势、效能与资产健康度。",
        "日志中心": "浏览 Windows 原始事件日志，支持类型切换、关键字与高级筛选、服务端分页与详情查看。"
    };

    init();

    async function init() {
        if (!AppAuth.isLoggedIn()) {
            AppAuth.redirectToLogin();
            return;
        }

        bindSidebarToggle();
        bindLogout();
        fillCurrentUser();
        await syncCurrentUserFromApi();
        await loadPermissions();
        await loadMenus();
        bindProfileButton();
    }

    async function loadPermissions() {
        try {
            var result = await AppRequest.request(API_CONFIG.currentPermissions, {method: "GET"});
            AppAuth.setPermissions(result.data || []);
        } catch (error) {
            AppAuth.setPermissions([]);
        }
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

    bindFrameGuard();

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
                var frameWindow = contentFrame.contentWindow;
                var frameLocation = frameWindow.location.href;
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
        currentUserName.textContent = AppAuth.getCurrentUserName() || "管理员";
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

    async function loadMenus() {
        try {
            var result = await AppRequest.request(API_CONFIG.currentMenus, {method: "GET"});
            var menus = result.data || [];
            renderMenus(menus);
            bindMenuEvents();
            element.render("nav");
            activateDefaultMenu();
        } catch (error) {
            layer.msg("菜单加载失败", {icon: 2});
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
                            + '<a href="javascript:;" data-title="' + child.title + '" data-page="' + child.page + '">'
                            + (child.icon ? '<i class="layui-icon ' + child.icon + '"></i>' : '')
                            + '<span>' + child.title + '</span>'
                            + '</a>'
                            + '</dd>';
                    }).join("")
                    + "</dl>";

                return '<li class="layui-nav-item">'
                    + '<a href="javascript:;">'
                    + (menu.icon ? '<i class="layui-icon ' + menu.icon + '"></i>' : '')
                    + '<span>' + menu.title + "</span>"
                    + "</a>"
                    + childHtml
                    + "</li>";
            }

            return '<li class="layui-nav-item">'
                + '<a href="javascript:;" data-title="' + menu.title + '" data-page="' + menu.page + '">'
                + (menu.icon ? '<i class="layui-icon ' + menu.icon + '"></i>' : '')
                + '<span>' + menu.title + "</span>"
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
        pageDescription.textContent = menuDescriptions[title] || "";
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
});
