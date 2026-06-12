package com.cd.dto;

import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

/**
 * 创建工单请求：对所选不合规结果创建整改工单。
 */
@Data
public class BaselineWorkorderRequestDTO {

    @NotEmpty(message = "工单目标不能为空")
    private List<Long> resultIds;

    private String assignee;

    private String remark;
}
