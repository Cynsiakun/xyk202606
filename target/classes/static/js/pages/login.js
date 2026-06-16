layui.use(["form", "layer"], function () {
    var form = layui.form;
    var layer = layui.layer;
    var tenantOptions = [];

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

    init();

    form.on("submit(loginSubmit)", function (data) {
        submitLogin(data.field);
        return false;
    });

    async function init() {
        await loadTenantOptions();
        bindEnterKey();
    }

    async function loadTenantOptions() {
        var select = document.getElementById("tenantId");
        try {
            var result = await AppRequest.request("/api/tenant/options", {method: "GET"}, {showErrorMessage: false});
            tenantOptions = result.data || [];
            var html = ['<option value="">请选择租户</option>'];
            tenantOptions.forEach(function (tenant) {
                html.push('<option value="' + tenant.id + '">' + escapeHtml(tenant.name) + "</option>");
            });
            select.innerHTML = html.join("");
            form.render("select");
        } catch (error) {
            select.innerHTML = '<option value="">加载失败</option>';
            form.render("select");
            layer.msg("租户列表加载失败", {icon: 2});
        }
    }

    async function submitLogin(payload) {
        try {
            var result = await AppRequest.request("/api/user/login", {
                method: "POST",
                body: {
                    tenantId: Number(payload.tenantId),
                    userName: payload.userName,
                    password: payload.password
                }
            }, {
                redirectOnUnauthorized: false
            });

            AppAuth.saveLogin({
                token: result.data.token,
                userId: result.data.userId,
                tenantId: result.data.tenantId,
                tenantName: findTenantName(result.data.tenantId),
                userName: result.data.userName
            });
            window.location.href = "/index.html";
        } catch (error) {
            return null;
        }
    }

    function bindEnterKey() {
        document.getElementById("loginForm").addEventListener("keydown", function (event) {
            if (event.key === "Enter") {
                event.preventDefault();
                document.querySelector('button[lay-filter="loginSubmit"]').click();
            }
        });
    }

    function findTenantName(tenantId) {
        var id = Number(tenantId);
        var matched = tenantOptions.find(function (tenant) {
            return Number(tenant.id) === id;
        });
        return matched ? matched.name : "";
    }

    function escapeHtml(text) {
        return String(text == null ? "" : text)
            .replace(/&/g, "&amp;")
            .replace(/</g, "&lt;")
            .replace(/>/g, "&gt;")
            .replace(/"/g, "&quot;")
            .replace(/'/g, "&#39;");
    }
});
