layui.use(["layer", "form"], function () {
    var layer = layui.layer;
    var form = layui.form;
    var currentUser = null;
    var selectedAvatarFile = null;
    var defaultAvatarTip = "支持 jpg、jpeg、png、gif，5MB 以内";
    var profileAvatar = document.getElementById("profileAvatar");
    var avatarFileInput = document.getElementById("avatarFileInput");
    var avatarFileName = document.getElementById("avatarFileName");

    loadProfile();

    document.getElementById("editProfileButton").addEventListener("click", openProfileDialog);
    document.getElementById("changePasswordButton").addEventListener("click", openPasswordDialog);
    document.getElementById("selectAvatarButton").addEventListener("click", function () {
        avatarFileInput.click();
    });
    document.getElementById("uploadAvatarButton").addEventListener("click", uploadAvatar);
    avatarFileInput.addEventListener("change", handleAvatarFileChange);

    form.on("submit(saveProfile)", function (data) {
        var formEl = document.querySelector('form[lay-filter="profileForm"]');
        AppUtils.clearFormError(formEl);
        (async function () {
            try {
                var result = await AppRequest.request("/api/user/updateSelf", {
                    method: "PUT",
                    body: data.field
                }, {
                    successMessage: "保存成功",
                    showErrorMessage: false
                });
                currentUser = result.data || {};
                renderProfile(currentUser);
                layer.closeAll("page");
            } catch (error) {
                AppUtils.showFormError(formEl, error.message);
            }
        })();
        return false;
    });

    form.on("submit(savePassword)", function (data) {
        var formEl = document.querySelector('form[lay-filter="passwordForm"]');
        AppUtils.clearFormError(formEl);
        (async function () {
            try {
                await AppRequest.request("/api/user/changePassword", {
                    method: "POST",
                    body: data.field
                }, {
                    successMessage: "密码修改成功",
                    showErrorMessage: false
                });
                layer.closeAll("page");
            } catch (error) {
                AppUtils.showFormError(formEl, error.message);
            }
        })();
        return false;
    });

    async function loadProfile() {
        try {
            var result = await AppRequest.request("/api/current-user", {method: "GET"});
            currentUser = result.data || {};
            renderProfile(currentUser);
        } catch (error) {
            return null;
        }
    }

    function renderProfile(data) {
        profileAvatar.src = data.userAvatar || "https://api.dicebear.com/8.x/initials/svg?seed=" + encodeURIComponent(data.userName || "user");
        document.getElementById("profileUserName").textContent = data.userName || "-";
        document.getElementById("profileStatus").textContent = "状态：" + (data.status === 1 ? "启用" : "禁用");
        document.getElementById("profilePhone").textContent = data.userPhone || "-";
        document.getElementById("profileEmail").textContent = data.userEmail || "-";
        document.getElementById("profileCreateAt").textContent = AppUtils.formatDateTime(data.createAt);
        document.getElementById("profileLastLoginTime").textContent = AppUtils.formatDateTime(data.lastLoginTime);
    }

    function openProfileDialog() {
        var index = layer.open({
            type: 1,
            title: "修改信息",
            area: ["520px", "360px"],
            content: AppUtils.getTemplateHtml("profileFormTemplate"),
            success: function (layero) {
                form.render();
                form.val("profileForm", {
                    userPhone: currentUser.userPhone || "",
                    userEmail: currentUser.userEmail || ""
                });
                AppUtils.bindLiveValidation(layero[0], {
                    saveButton: 'button[lay-filter="saveProfile"]',
                    fields: [
                        {selector: 'input[name="userPhone"]', type: "phone", message: "手机号格式不正确"},
                        {selector: 'input[name="userEmail"]', type: "email", message: "邮箱格式不正确"}
                    ]
                });
                layero.find('[data-action="close"]').on("click", function () {
                    layer.close(index);
                });
            }
        });
    }

    function openPasswordDialog() {
        var index = layer.open({
            type: 1,
            title: "修改密码",
            area: ["500px", "300px"],
            content: AppUtils.getTemplateHtml("passwordFormTemplate"),
            success: function (layero) {
                form.render();
                layero.find('[data-action="close"]').on("click", function () {
                    layer.close(index);
                });
            }
        });
    }

    function handleAvatarFileChange(event) {
        var file = event.target.files && event.target.files[0] ? event.target.files[0] : null;
        if (!file) {
            selectedAvatarFile = null;
            avatarFileName.textContent = defaultAvatarTip;
            return;
        }

        var validationMessage = validateAvatarFile(file);
        if (validationMessage) {
            selectedAvatarFile = null;
            avatarFileInput.value = "";
            avatarFileName.textContent = defaultAvatarTip;
            AppRequest.showMessage(validationMessage, 2, 2200);
            return;
        }

        selectedAvatarFile = file;
        avatarFileName.textContent = "已选择：" + file.name;
    }

    async function uploadAvatar() {
        if (!selectedAvatarFile) {
            AppRequest.showMessage("请先选择图片", 2, 2000);
            return;
        }

        var formData = new FormData();
        formData.append("file", selectedAvatarFile);

        try {
            var result = await AppRequest.request("/api/user/avatar/upload", {
                method: "POST",
                body: formData
            }, {
                successMessage: "上传成功"
            });

            currentUser = currentUser || {};
            currentUser.userAvatar = result.data && result.data.avatarUrl ? result.data.avatarUrl : currentUser.userAvatar;
            renderProfile(currentUser);
            selectedAvatarFile = null;
            avatarFileInput.value = "";
            avatarFileName.textContent = defaultAvatarTip;
        } catch (error) {
            return null;
        }
    }

    function validateAvatarFile(file) {
        var maxSize = 5 * 1024 * 1024;
        var fileName = file.name || "";
        var extension = fileName.indexOf(".") > -1 ? fileName.split(".").pop().toLowerCase() : "";
        var allowedExtensions = ["jpg", "jpeg", "png", "gif"];

        if (allowedExtensions.indexOf(extension) === -1) {
            return "仅支持 jpg、jpeg、png、gif 格式图片";
        }

        if (file.size > maxSize) {
            return "图片大小不能超过5MB";
        }

        return "";
    }

});
