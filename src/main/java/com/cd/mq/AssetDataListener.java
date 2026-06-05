package com.cd.mq;

import com.cd.common.config.RabbitMQConfig;
import com.cd.service.AssetDataService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/**
 * 监听资产探测结果队列，消费后交由 {@link AssetDataService} 校验并入库。
 *
 * <p>使用默认 auto-ACK：业务层保证无论校验通过与否都已完成持久化，无需拒绝消息。
 * 所有异常在业务层兜底，避免 MQ 无限重试。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AssetDataListener {

    private final AssetDataService assetDataService;

    @RabbitListener(queues = RabbitMQConfig.ACCOUNT_QUEUE)
    public void onAccountMessage(String message) {
        safeProcess(RabbitMQConfig.ACCOUNT_QUEUE, message);
    }

    @RabbitListener(queues = RabbitMQConfig.SERVICE_QUEUE)
    public void onServiceMessage(String message) {
        safeProcess(RabbitMQConfig.SERVICE_QUEUE, message);
    }

    @RabbitListener(queues = RabbitMQConfig.PROCESS_QUEUE)
    public void onProcessMessage(String message) {
        safeProcess(RabbitMQConfig.PROCESS_QUEUE, message);
    }

    @RabbitListener(queues = RabbitMQConfig.APP_QUEUE)
    public void onAppMessage(String message) {
        safeProcess(RabbitMQConfig.APP_QUEUE, message);
    }

    private void safeProcess(String queueName, String message) {
        try {
            assetDataService.processAssetMessage(queueName, message);
        } catch (Exception e) {
            log.error("AssetDataListener 未预期的异常 (消息已 ACK): queue={}, msg={}", queueName, message, e);
        }
    }
}
