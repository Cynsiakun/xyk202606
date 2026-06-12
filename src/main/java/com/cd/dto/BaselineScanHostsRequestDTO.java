package com.cd.dto;

import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

/**
 * 单台/批量「立即检测」请求：对所选主机各自下发一次基线扫描。
 */
@Data
public class BaselineScanHostsRequestDTO {

    @NotEmpty(message = "主机范围不能为空")
    private List<Long> hostIds;
}
