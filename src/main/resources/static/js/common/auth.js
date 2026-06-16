(function (window) {
    var TOKEN_KEY = "token";
    var CURRENT_USER_ID_KEY = "currentUserId";
    var CURRENT_USER_NAME_KEY = "currentUserName";
    var TENANT_ID_KEY = "tenantId";
    var PERMISSIONS_KEY = "permissions";
    var LICENSE_INFO_KEY = "licenseInfo";

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
        var payload = decodeJwtPayload(authData.token);
        localStorage.setItem(TOKEN_KEY, authData.token);
        if (authData.userId != null) {
            localStorage.setItem(CURRENT_USER_ID_KEY, authData.userId);
        }
        localStorage.setItem(CURRENT_USER_NAME_KEY, authData.userName || payload.sub || "");
        if (authData.tenantId != null) {
            localStorage.setItem(TENANT_ID_KEY, authData.tenantId);
        } else if (payload.tenantId != null) {
            localStorage.setItem(TENANT_ID_KEY, payload.tenantId);
        }
    }

    function clearLogin() {
        localStorage.removeItem(TOKEN_KEY);
        localStorage.removeItem(CURRENT_USER_ID_KEY);
        localStorage.removeItem(CURRENT_USER_NAME_KEY);
        localStorage.removeItem(TENANT_ID_KEY);
        localStorage.removeItem(PERMISSIONS_KEY);
        localStorage.removeItem(LICENSE_INFO_KEY);
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

    function hasPermission(permissionCode) {
        var permissions = getPermissions();
        if (permissions.length === 0) {
            return true;
        }
        return permissions.indexOf("*") > -1 || permissions.indexOf(permissionCode) > -1;
    }

    function isSuperAdmin() {
        var permissions = getPermissions();
        return permissions.indexOf("*") > -1 || permissions.indexOf("ROLE_SUPER_ADMIN") > -1;
    }

    function getCurrentUserName() {
        return localStorage.getItem(CURRENT_USER_NAME_KEY);
    }

    function getCurrentUserId() {
        var value = localStorage.getItem(CURRENT_USER_ID_KEY);
        return value == null || value === "" ? null : Number(value);
    }

    function getTenantId() {
        var value = localStorage.getItem(TENANT_ID_KEY);
        if (value == null || value === "") {
            var payload = decodeJwtPayload(getToken());
            value = payload.tenantId;
        }
        return value == null || value === "" ? null : Number(value);
    }

    function isLoggedIn() {
        return !!getToken();
    }

    function setLicenseInfo(info) {
        localStorage.setItem(LICENSE_INFO_KEY, JSON.stringify(info || null));
    }

    function getLicenseInfo() {
        try {
            var stored = JSON.parse(localStorage.getItem(LICENSE_INFO_KEY));
            return stored && typeof stored === "object" ? stored : null;
        } catch (error) {
            return null;
        }
    }

    function hasFeature(featureName) {
        var license = getLicenseInfo();
        if (!license || license.effective === false) {
            return false;
        }
        if (license.edition === "PLATFORM") {
            return true;
        }
        var features = Array.isArray(license.featureFlags) ? license.featureFlags : [];
        return features.indexOf(featureName) > -1;
    }

    function redirectToLogin() {
        var targetWindow = isInFrame() ? window.top : window;
        var currentPath = targetWindow.location.pathname || "";
        if (!currentPath.endsWith("/login.html")) {
            targetWindow.location.href = "/login.html";
        }
    }

    function decodeJwtPayload(token) {
        if (!token || token.split(".").length < 2) {
            return {};
        }
        try {
            var base64Url = token.split(".")[1];
            var base64 = base64Url.replace(/-/g, "+").replace(/_/g, "/");
            var padded = base64.padEnd(base64.length + (4 - base64.length % 4) % 4, "=");
            return JSON.parse(decodeURIComponent(Array.prototype.map.call(atob(padded), function (char) {
                return "%" + ("00" + char.charCodeAt(0).toString(16)).slice(-2);
            }).join("")));
        } catch (error) {
            return {};
        }
    }

    window.AppAuth = {
        getToken: getToken,
        saveLogin: saveLogin,
        clearLogin: clearLogin,
        getCurrentUserId: getCurrentUserId,
        getCurrentUserName: getCurrentUserName,
        getTenantId: getTenantId,
        isLoggedIn: isLoggedIn,
        redirectToLogin: redirectToLogin,
        setPermissions: setPermissions,
        getPermissions: getPermissions,
        hasPermission: hasPermission,
        isSuperAdmin: isSuperAdmin,
        setLicenseInfo: setLicenseInfo,
        getLicenseInfo: getLicenseInfo,
        hasFeature: hasFeature
    };
})(window);
