package com.cd.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class UserChangePasswordDTO {

    @NotBlank(message = "oldPwd must not be blank")
    private String oldPwd;

    @NotBlank(message = "newPwd must not be blank")
    @Size(min = 1, max = 64, message = "newPwd length must be between 1 and 64")
    private String newPwd;
}
