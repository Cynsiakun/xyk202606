(function (window) {
    var TOKEN_KEY = "token";
    var CURRENT_USER_NAME_KEY = "currentUserName";
    var PERMISSIONS_KEY = "permissions";

    function isInFrame() {
        try {
            return window.self !== window.top;
        } catch (error) {
            return true;
        }
    }

    function getToken() {
        return localStorage.getItem(TOKEN_KEY);
    }

    function saveLogin(authData) {
        localStorage.setItem(TOKEN_KEY, authData.token);
        localStorage.setItem(CURRENT_USER_NAME_KEY, authData.userName);
    }

    function clearLogin() {
        localStorage.removeItem(TOKEN_KEY);
        localStorage.removeItem(CURRENT_USER_NAME_KEY);
        localStorage.removeItem(PERMISSIONS_KEY);
    }

    function setPermissions(permissionCodes) {
        localStorage.setItem(PERMISSIONS_KEY, JSON.stringify(permissionCodes || []));
    }

    function getPermissions() {
        try {
            var stored = JSON.parse(localStorage.getItem(PERMISSIONS_KEY));
            return Array.isArray(stored) ? stored : [];
        } catch (error) {
            return [];
        }
    }

    /**
     * 是否拥有指定权限码。通配 "*"（超级管理员）一律返回 true。
     * 当本地尚未加载到权限列表时（例如直接打开子页面）默认放行，真正的拦截仍由后端保证。
     */
    function hasPermission(permissionCode) {
        var permissions = getPermissions();
        if (permissions.length === 0) {
            return true;
        }
        return permissions.indexOf("*") > -1 || permissions.indexOf(permissionCode) > -1;
    }

    function getCurrentUserName() {
        return localStorage.getItem(CURRENT_USER_NAME_KEY);
    }

    function isLoggedIn() {
        return !!getToken();
    }

    function redirectToLogin() {
        var targetWindow = isInFrame() ? window.top : window;
        var currentPath = targetWindow.location.pathname || "";
        if (!currentPath.endsWith("/login.html")) {
            targetWindow.location.href = "/login.html";
        }
    }

    window.AppAuth = {
        getToken: getToken,
        saveLogin: saveLogin,
        clearLogin: clearLogin,
        getCurrentUserName: getCurrentUserName,
        isLoggedIn: isLoggedIn,
        redirectToLogin: redirectToLogin,
        setPermissions: setPermissions,
        getPermissions: getPermissions,
        hasPermission: hasPermission
    };
})(window);
