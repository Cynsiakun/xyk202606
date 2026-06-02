package com.cd.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class UserUpdateSelfDTO {

    @Size(max = 255, message = "userAvatar length must be less than or equal to 255")
    private String userAvatar;

    @Pattern(regexp = "^$|^1[3-9]\\d{9}$", message = "手机号格式不正确")
    private String userPhone;

    @Email(message = "邮箱格式不正确")
    @Size(max = 100, message = "userEmail length must be less than or equal to 100")
    private String userEmail;
}
