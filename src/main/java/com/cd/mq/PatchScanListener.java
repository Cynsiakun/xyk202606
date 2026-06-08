package com.cd.mq;

import com.cd.common.config.RabbitMQConfig;
import com.cd.service.PatchScanService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class PatchScanListener {

    private final PatchScanService patchScanService;

    @RabbitListener(queues = RabbitMQConfig.PATCH_SCAN_QUEUE)
    public void onPatchScanMessage(String message) {
        try {
            patchScanService.processPatchScanMessage(RabbitMQConfig.PATCH_SCAN_QUEUE, message);
        } catch (Exception e) {
            log.error("PatchScanListener 未预期异常 (消息已 ACK): queue={}, msg={}",
                    RabbitMQConfig.PATCH_SCAN_QUEUE, message, e);
        }
    }
}
