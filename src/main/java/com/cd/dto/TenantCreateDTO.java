package com.cd.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class TenantCreateDTO {

    @NotBlank(message = "name must not be blank")
    @Size(max = 100, message = "name length must be less than or equal to 100")
    private String name;

    @Size(max = 100, message = "contact length must be less than or equal to 100")
    private String contact;

    private Integer status;

    @NotBlank(message = "adminUserName must not be blank")
    @Size(max = 50, message = "adminUserName length must be less than or equal to 50")
    private String adminUserName;

    @NotBlank(message = "adminPassword must not be blank")
    @Size(max = 64, message = "adminPassword length must be less than or equal to 64")
    private String adminPassword;

    @Size(max = 20, message = "adminPhone length must be less than or equal to 20")
    private String adminPhone;

    @Size(max = 100, message = "adminEmail length must be less than or equal to 100")
    private String adminEmail;
}
