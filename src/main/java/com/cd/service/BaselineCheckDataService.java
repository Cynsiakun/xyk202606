package com.cd.service;

/**
 * 基线检测回传结果处理：对队列消息做基本字段校验，通过则将完整 JSON 原样写入
 * {@code baseline_check_data}，失败则写入 {@code mq_error_logs}。
 *
 * <p>本阶段仅做接收与入库，不做任何规则比对，原始数据供后续规则引擎判定使用。</p>
 */
public interface BaselineCheckDataService {

    /**
     * 处理一条基线检测回传结果消息。
     *
     * @param queueName 来源队列名，用于异常记录
     * @param message   JSON 字符串
     */
    void processBaselineResult(String queueName, String message);
}
