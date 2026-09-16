package com.cd.mq;

import com.cd.common.config.RabbitMQConfig;
import com.cd.service.PortScanResultService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class PortScanResultListener {

    private final PortScanResultService portScanResultService;

    @RabbitListener(queues = RabbitMQConfig.PORT_SCAN_QUEUE)
    public void onPortScanMessage(String message) {
        try {
            portScanResultService.processPortScanMessage(RabbitMQConfig.PORT_SCAN_QUEUE, message);
        } catch (Exception e) {
            log.error("PortScanResultListener unexpected error (message already ACKed): queue={}, msg={}",
                    RabbitMQConfig.PORT_SCAN_QUEUE, message, e);
        }
    }
}
