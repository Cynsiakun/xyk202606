(function (window) {
    var TOKEN_KEY = "token";
    var CURRENT_USER_NAME_KEY = "currentUserName";

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
        redirectToLogin: redirectToLogin
    };
})(window);
