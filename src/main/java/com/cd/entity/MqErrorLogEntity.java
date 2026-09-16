package com.cd.entity;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * MQ 消息异常记录：格式校验失败的消息不入业务表，写入此表并 ACK，避免丢失与无限重试。
 */
@Data
public class MqErrorLogEntity {

    private Long id;
    private Long tenantId;
    private String queueName;
    private String rawMessage;
    private String errorReason;
    private LocalDateTime createdAt;
}
