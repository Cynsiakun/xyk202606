package com.cd.mq;

import com.cd.common.config.RabbitMQConfig;
import com.cd.service.BaselineCheckDataService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/**
 * 监听基线检测回传结果队列 {@code baseline_queue}，消费后交由
 * {@link BaselineCheckDataService} 校验并原样入库。
 *
 * <p>使用默认 auto-ACK：业务层保证无论校验通过与否都已完成持久化（坏消息落
 * {@code mq_error_logs}），无需拒绝消息。所有异常在业务层兜底，避免 MQ 无限重试。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BaselineResultListener {

    private final BaselineCheckDataService baselineCheckDataService;

    @RabbitListener(queues = RabbitMQConfig.BASELINE_QUEUE)
    public void onBaselineResult(String message) {
        try {
            baselineCheckDataService.processBaselineResult(RabbitMQConfig.BASELINE_QUEUE, message);
        } catch (Exception e) {
            log.error("BaselineResultListener 未预期的异常 (消息已 ACK): queue={}, msg={}",
                    RabbitMQConfig.BASELINE_QUEUE, message, e);
        }
    }
}
