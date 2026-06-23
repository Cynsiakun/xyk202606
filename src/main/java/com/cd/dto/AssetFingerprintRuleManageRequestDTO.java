package com.cd.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class AssetFingerprintRuleManageRequestDTO {

    @NotBlank(message = "ruleCode不能为空")
    private String ruleCode;

    @NotBlank(message = "name不能为空")
    private String name;

    @NotBlank(message = "category不能为空")
    private String category;

    private String subCategory;

    @NotBlank(message = "protocol不能为空")
    private String protocol;

    @Min(value = 1, message = "port必须大于0")
    @Max(value = 65535, message = "port不能超过65535")
    private Integer port;

    private String bannerRegex;

    private String vendor;

    @NotBlank(message = "product不能为空")
    private String product;

    private String versionExpr;

    @Min(value = 0, message = "confidence不能小于0")
    @Max(value = 100, message = "confidence不能大于100")
    private Integer confidence;

    private String description;

    private Integer enabled;

    @Min(value = 0, message = "priority不能小于0")
    private Integer priority;
}
