(function (window) {
    function message(layer, content, icon, time) {
        layer.msg(content, {
            icon: icon,
            time: time || 1800
        });
    }

    function success(layer, content, time) {
        message(layer, content, 1, time || 1200);
    }

    function error(layer, content, time) {
        message(layer, content, 2, time || 1800);
    }

    function confirm(layer, content, onConfirm) {
        layer.confirm(content, {
            icon: 3,
            title: "确认"
        }, onConfirm);
    }

    window.AppDialog = {
        success: success,
        error: error,
        confirm: confirm
    };
})(window);
