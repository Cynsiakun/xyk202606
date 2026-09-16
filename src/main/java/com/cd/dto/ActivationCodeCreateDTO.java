package com.cd.dto;

import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ActivationCodeCreateDTO {

    @NotNull(message = "expireTime must not be null")
    @Future(message = "expireTime must be in the future")
    private LocalDateTime expireTime;

    @Size(max = 255, message = "remark length must be less than or equal to 255")
    private String remark;

    @Min(value = 1, message = "quantity must be greater than or equal to 1")
    @Max(value = 100, message = "quantity must be less than or equal to 100")
    private Integer quantity;
}
