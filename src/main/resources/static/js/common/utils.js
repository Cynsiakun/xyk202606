(function (window) {
    var PHONE_REGEX = /^1[3-9]\d{9}$/;
    var EMAIL_REGEX = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;

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

    function renderFieldError(input, message) {
        var block = input.closest(".layui-input-block") || input.parentNode;
        var errorEl = block.querySelector(".field-error");
        if (message) {
            if (!errorEl) {
                errorEl = document.createElement("div");
                errorEl.className = "field-error";
                block.appendChild(errorEl);
            }
            errorEl.textContent = message;
        } else if (errorEl) {
            errorEl.remove();
        }
    }

    function validateField(container, field) {
        var input = container.querySelector(field.selector);
        if (!input) {
            return true;
        }
        var value = (input.value || "").trim();
        var valid = true;
        if (!value) {
            valid = !field.required;
        } else if (field.type === "email") {
            valid = EMAIL_REGEX.test(value);
        } else if (field.type === "phone") {
            valid = PHONE_REGEX.test(value);
        }
        renderFieldError(input, valid ? "" : field.message);
        return valid;
    }

    /**
     * 对弹窗表单做实时校验：格式不合法时在字段下方显示错误，并禁用保存按钮。
     * container 为弹窗根 DOM 节点，options.fields 描述需要校验的字段，options.saveButton 为保存按钮选择器。
     */
    function bindLiveValidation(container, options) {
        if (!container) {
            return function () { return true; };
        }
        var fields = options.fields || [];
        var saveButton = options.saveButton ? container.querySelector(options.saveButton) : null;

        function validateAll() {
            var allValid = fields.map(function (field) {
                return validateField(container, field);
            }).every(Boolean);

            if (saveButton) {
                saveButton.disabled = !allValid;
                if (allValid) {
                    saveButton.classList.remove("layui-btn-disabled");
                } else {
                    saveButton.classList.add("layui-btn-disabled");
                }
            }
            return allValid;
        }

        fields.forEach(function (field) {
            var input = container.querySelector(field.selector);
            if (input) {
                input.addEventListener("input", validateAll);
                input.addEventListener("blur", validateAll);
            }
        });

        validateAll();
        return validateAll;
    }

    /** 在弹窗表单顶部显示服务端返回的错误（避免 toast 被弹窗遮挡）。 */
    function showFormError(form, message) {
        if (!form) {
            return;
        }
        var banner = form.querySelector(".form-error");
        if (!banner) {
            banner = document.createElement("div");
            banner.className = "form-error";
            form.insertBefore(banner, form.firstChild);
        }
        banner.textContent = message || "操作失败";
    }

    function clearFormError(form) {
        if (!form) {
            return;
        }
        var banner = form.querySelector(".form-error");
        if (banner) {
            banner.remove();
        }
    }

    window.AppUtils = {
        formatDateTime: formatDateTime,
        fallback: fallback,
        getTemplateHtml: getTemplateHtml,
        bindLiveValidation: bindLiveValidation,
        showFormError: showFormError,
        clearFormError: clearFormError
    };
})(window);
