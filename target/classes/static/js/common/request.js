(function (window) {
    function getLayer() {
        if (window.layui && window.layui.layer) {
            return window.layui.layer;
        }
        if (window.layer) {
            return window.layer;
        }
        return null;
    }

    function showMessage(content, icon, time) {
        var layer = getLayer();
        if (layer && typeof layer.msg === "function") {
            layer.msg(content, {
                icon: icon,
                time: time || 1800
            });
        }
    }

    function normalizeErrorMessage(message) {
        if (!message) {
            return "请求失败";
        }
        if (message.indexOf("License feature not allowed: AI_") === 0) {
            return "当前授权版本不支持 AI 功能";
        }
        if (message === "License feature not allowed: ASSET_EXPORT") {
            return "当前授权版本不支持资产导出";
        }
        if (message.indexOf("License feature not allowed:") === 0) {
            return "当前授权版本不支持该功能";
        }
        if (message === "User quota exceeded") {
            return "用户配额已满";
        }
        if (message === "Host quota exceeded" || message === "Host limit exceeded") {
            return "主机配额已满";
        }
        if (message === "No effective License for current tenant") {
            return "当前租户暂无有效授权";
        }
        return message;
    }

    async function request(url, options, extraOptions) {
        var requestOptions = options || {};
        var customOptions = extraOptions || {};
        var headers = Object.assign({}, requestOptions.headers || {});
        var token = window.AppAuth.getToken();
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
            var rawMessage = result && result.message ? result.message : "请求失败";
            var requestError = new Error(normalizeErrorMessage(rawMessage));
            requestError.code = result && result.code ? result.code : response.status;

            if (customOptions.showErrorMessage !== false) {
                showMessage(requestError.message, 2, customOptions.errorTime || 2200);
            }

            if (requestError.code === 401 && customOptions.redirectOnUnauthorized !== false) {
                window.AppAuth.clearLogin();
                window.AppAuth.redirectToLogin();
            }
            throw requestError;
        }

        if (customOptions.successMessage) {
            showMessage(customOptions.successMessage, 1, customOptions.successTime || 1600);
        }

        return result;
    }

    window.AppRequest = {
        request: request,
        showMessage: showMessage,
        normalizeErrorMessage: normalizeErrorMessage
    };
})(window);
