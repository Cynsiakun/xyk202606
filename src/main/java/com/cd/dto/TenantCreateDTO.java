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
}
