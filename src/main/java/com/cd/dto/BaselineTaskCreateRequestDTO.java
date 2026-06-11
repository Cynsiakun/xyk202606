package com.cd.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

@Data
public class BaselineTaskCreateRequestDTO {

    @NotBlank(message = "任务名称不能为空")
    private String taskName;

    private String executeType;

    private String cronExpr;

    @NotEmpty(message = "主机范围不能为空")
    private List<Long> hostIds;

    @NotEmpty(message = "规则范围不能为空")
    private List<Long> ruleIds;
}
