layui.use(["form", "layer"], function () {
    var form = layui.form;
    var layer = layui.layer;

    form.verify({
        userName: function (value) {
            if (!value || !value.trim()) {
                return "用户名不能为空";
            }
        },
        password: function (value) {
            if (!value || !value.trim()) {
                return "密码不能为空";
            }
        }
    });

    form.on("submit(loginSubmit)", function (data) {
        submitLogin(data.field);
        return false;
    });

    async function submitLogin(payload) {
        try {
            var result = await AppRequest.request("/api/user/login", {
                method: "POST",
                body: payload
            }, {
                redirectOnUnauthorized: false
            });

            AppRequest.saveLogin({
                token: result.data.token,
                userName: result.data.userName
            });
            window.location.href = "./index.html";
        } catch (error) {
            layer.msg(error.message || "登录失败", {icon: 2, time: 2000});
        }
    }
});
