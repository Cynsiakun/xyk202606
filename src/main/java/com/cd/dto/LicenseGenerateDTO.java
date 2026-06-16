package com.cd.dto;

import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class LicenseGenerateDTO {

    @Size(max = 32, message = "edition length must be less than or equal to 32")
    private String edition;

    @Size(max = 32, message = "planCode length must be less than or equal to 32")
    private String planCode;

    @Min(value = 0, message = "hostLimit must be greater than or equal to 0")
    private Integer hostLimit;

    @Min(value = 0, message = "userLimit must be greater than or equal to 0")
    private Integer userLimit;

    @NotNull(message = "expireTime must not be null")
    @Future(message = "expireTime must be in the future")
    private LocalDateTime expireTime;

    @Size(max = 128, message = "machineId length must be less than or equal to 128")
    private String machineId;

    private Integer status;
}
