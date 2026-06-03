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
        logout: "/api/user/logout"
    };

    var menuDescriptions = {
        "后台主页": "查看用户、登录和活跃情况统计。",
        "用户管理": "维护后台用户，支持新增、编辑、删除、搜索和分页。",
        "登录日志": "审计登录成功和失败记录，支持用户名与状态筛选。",
        "个人信息": "查看并维护当前登录账号的基础资料和密码。",
        "角色管理": "角色管理模块。",
        "权限管理": "权限管理模块。"
    };

    init();

    async function init() {
        if (!AppAuth.isLoggedIn()) {
            AppAuth.redirectToLogin();
            return;
        }

        bindMenuEvents();
        bindSidebarToggle();
        bindLogout();
        bindProfileButton();
        fillCurrentUser();
        await syncCurrentUserFromApi();
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

    function switchTo(link) {
        var title = link.dataset.title;
        var page = link.dataset.page;
        setActiveMenu(link);
        pageTitle.textContent = title;
        pageDescription.textContent = menuDescriptions[title] || "";
        contentFrame.src = page;
    }

    function setActiveMenu(link) {
        sideNav.querySelectorAll(".layui-nav-item").forEach(function (item) {
            item.classList.remove("layui-this");
        });
        link.parentElement.classList.add("layui-this");
        element.render("nav");
    }
});
