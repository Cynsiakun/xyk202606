(function (window) {
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

    function fallback(value, defaultValue) {
        return value == null || value === "" ? defaultValue : value;
    }

    function getTemplateHtml(templateId) {
        var template = document.getElementById(templateId);
        return template ? template.innerHTML.trim() : "";
    }

    window.AppUtils = {
        formatDateTime: formatDateTime,
        fallback: fallback,
        getTemplateHtml: getTemplateHtml
    };
})(window);
