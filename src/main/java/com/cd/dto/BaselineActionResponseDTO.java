package com.cd.dto;

import lombok.Data;

/**
 * 主机视角动作（批量检测 / 自动修复 / 创建工单）的统一返回。
 */
@Data
public class BaselineActionResponseDTO {

    private Integer total;
    private Integer success;
    private Integer failed;
    private String message;
}
