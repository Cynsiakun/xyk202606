package com.cd.entity;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 基线检测结果：一条记录表示某台主机某项规则（或规则项）的检查结果。
 *
 * <p>由规则引擎将 {@code baseline_check_data} 中的原始数据逐 item 比对后生成，
 * {@code status} 取值 PASS/FAIL/ERROR/UNKNOWN。</p>
 */
@Data
public class BaselineResultEntity {

    private Long id;
    private Long tenantId;
    private Long taskId;
    private Long taskHostId;
    private Long hostId;
    private Long ruleId;
    private Integer ruleVersion;
    private String checkKey;
    private String status;
    private String actualValue;
    private String expectedValue;
    private String message;
    private String evidence;
    private String remediationStatus;
    private LocalDateTime scanTime;
    private LocalDateTime createTime;
}
