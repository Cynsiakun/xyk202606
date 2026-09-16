package com.cd.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class TenantStatusUpdateDTO {

    @NotNull(message = "status must not be null")
    private Integer status;
}
