package com.cd.service;

/**
 * 资产探测结果处理：对队列消息做格式校验，通过则写入业务表，失败则写入异常记录表。
 *
 * <p>当前阶段仅做透传入库，不参与业务逻辑。task_id 保留供后续扩展任务追踪。</p>
 */
public interface AssetDataService {

    /**
     * 处理一条资产探测结果消息。
     *
     * @param queueName 来源队列名，用于 type 匹配校验
     * @param message   JSON 字符串
     */
    void processAssetMessage(String queueName, String message);
}
