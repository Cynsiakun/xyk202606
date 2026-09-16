package com.cd.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class UserLoginDTO {

    @NotNull(message = "tenantId must not be null")
    private Long tenantId;

    @NotBlank(message = "userName must not be blank")
    private String userName;

    @NotBlank(message = "password must not be blank")
    private String password;
}
