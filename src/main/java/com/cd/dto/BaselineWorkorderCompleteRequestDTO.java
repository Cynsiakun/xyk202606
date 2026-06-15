package com.cd.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class BaselineWorkorderCompleteRequestDTO {

    @NotBlank(message = "处理说明不能为空")
    private String closeRemark;
}
