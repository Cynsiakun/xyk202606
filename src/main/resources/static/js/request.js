(function (window) {
    var TOKEN_KEY = "token";
    var CURRENT_USER_NAME_KEY = "currentUserName";

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
        if (!window.location.pathname.endsWith("/login.html")) {
            window.top.location.href = "./login.html";
        }
    }

    function formatDateTime(value) {
        if (!value) {
            return "-";
        }
        var date = new Date(value);
        if (Number.isNaN(date.getTime())) {
            return value;
        }
        var year = date.getFullYear();
        var month = String(date.getMonth() + 1).padStart(2, "0");
        var day = String(date.getDate()).padStart(2, "0");
        var hour = String(date.getHours()).padStart(2, "0");
        var minute = String(date.getMinutes()).padStart(2, "0");
        var second = String(date.getSeconds()).padStart(2, "0");
        return year + "-" + month + "-" + day + " " + hour + ":" + minute + ":" + second;
    }

    async function request(url, options, extraOptions) {
        var requestOptions = options || {};
        var customOptions = extraOptions || {};
        var headers = Object.assign({}, requestOptions.headers || {});
        var token = getToken();
        var body = requestOptions.body;

        if (token) {
            headers.Authorization = "Bearer " + token;
        }

        if (body && typeof body === "object" && !(body instanceof FormData)) {
            headers["Content-Type"] = headers["Content-Type"] || "application/json";
            body = JSON.stringify(body);
        }

        var response = await fetch(url, Object.assign({}, requestOptions, {
            headers: headers,
            body: body
        }));

        var result = null;
        try {
            result = await response.json();
        } catch (error) {
            result = null;
        }

        if (!response.ok || !result || result.code !== 200) {
            var message = result && result.message ? result.message : "请求失败";
            try {
                if (window.layui && layui.layer && typeof layui.layer.msg === 'function') {
                    layui.layer.msg(message);
                } else if (window.layer && typeof layer.msg === 'function') {
                    layer.msg(message);
                } else {
                    console.warn('提示库未加载，使用 console 输出：', message);
                }
            } catch (e) {
                console.error(e);
            }

            var requestError = new Error(message);
            requestError.code = result && result.code ? result.code : response.status;

            if (requestError.code === 401 && customOptions.redirectOnUnauthorized !== false) {
                clearLogin();
                redirectToLogin();
            }
            throw requestError;
        }

        // 成功处理：如果调用方提供了 successMessage 则展示；否则对写操作（POST/PUT）默认提示 "保存成功"
        try {
            var method = (requestOptions.method || 'GET').toUpperCase();
            if (customOptions.successMessage) {
                if (window.layui && layui.layer && typeof layui.layer.msg === 'function') {
                    layui.layer.msg(customOptions.successMessage);
                } else if (window.layer && typeof layer.msg === 'function') {
                    layer.msg(customOptions.successMessage);
                }
            } else if (method === 'POST' || method === 'PUT') {
                var successMsg = '保存成功';
                if (window.layui && layui.layer && typeof layui.layer.msg === 'function') {
                    layui.layer.msg(successMsg);
                } else if (window.layer && typeof layer.msg === 'function') {
                    layer.msg(successMsg);
                }
            }
        } catch (e) {
            console.error(e);
        }

        return result;
    }

    window.AppRequest = {
        request: request,
        getToken: getToken,
        saveLogin: saveLogin,
        clearLogin: clearLogin,
        getCurrentUserName: getCurrentUserName,
        isLoggedIn: isLoggedIn,
        redirectToLogin: redirectToLogin,
        formatDateTime: formatDateTime
    };
})(window);
