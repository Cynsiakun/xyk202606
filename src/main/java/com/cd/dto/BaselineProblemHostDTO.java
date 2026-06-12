package com.cd.dto;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 问题主机行：用于「查看结果」弹窗中存在不合规项的主机列表。
 */
@Data
public class BaselineProblemHostDTO {

    private Long hostId;
    private String hostName;
    private String ipv4;
    private BigDecimal complianceRate;
    private Integer failRuleCount;
}
