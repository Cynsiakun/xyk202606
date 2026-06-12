package com.cd.dto;

import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

/**
 * 自动修复请求：对所选不合规结果下发修复脚本。
 */
@Data
public class BaselineRemediationRequestDTO {

    @NotEmpty(message = "修复目标不能为空")
    private List<Long> resultIds;
}
