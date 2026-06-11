package com.cd.dto;

import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

/**
 * 批量确认 / 处理的请求体。
 */
@Data
public class AlertIdsDTO {

    @NotEmpty(message = "ids 不能为空")
    private List<Long> ids;
}
