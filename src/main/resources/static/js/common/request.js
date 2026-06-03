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
            var requestError = new Error(result && result.message ? result.message : "请求失败");
            requestError.code = result && result.code ? result.code : response.status;

            if (customOptions.showErrorMessage !== false) {
                showMessage(requestError.message, 2, customOptions.errorTime || 2200);
            }

            if ((requestError.code === 401 || requestError.code === 403) && customOptions.redirectOnUnauthorized !== false) {
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
        showMessage: showMessage
    };
})(window);
