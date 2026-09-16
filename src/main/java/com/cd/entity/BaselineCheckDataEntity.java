package com.cd.entity;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 基线检测原始数据：Agent 回传的完整结果 JSON 原样存入 {@code check_data}，不经过规则引擎。
 */
@Data
public class BaselineCheckDataEntity {

    private Long id;
    private Long tenantId;
    private Long taskId;
    private Long hostId;
    private String checkData;
    private LocalDateTime createTime;
}
