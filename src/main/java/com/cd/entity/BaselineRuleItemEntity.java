package com.cd.entity;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class BaselineRuleItemEntity {

    private Long id;
    private Long ruleId;
    private Integer itemOrder;
    private String checkKey;
    private String operator;
    private String expectedValue;
    private String matchType;
    private String remark;
    private LocalDateTime createTime;
}
