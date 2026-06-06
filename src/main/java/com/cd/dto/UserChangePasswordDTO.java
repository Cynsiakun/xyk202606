package com.cd.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class UserChangePasswordDTO {

    @NotBlank(message = "原密码不能为空")
    private String oldPwd;

    @NotBlank(message = "新密码不能为空")
    @Size(min = 8, max = 64, message = "新密码长度不少于8位，最长不超过64位")
    private String newPwd;

    @NotBlank(message = "确认密码不能为空")
    private String confirmPwd;
}
