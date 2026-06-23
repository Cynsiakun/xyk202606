package com.cd.mq;

import com.cd.common.config.RabbitMQConfig;
import com.cd.service.AgentUnifiedResultService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class AgentResultListener {

    private final AgentUnifiedResultService agentUnifiedResultService;

    @RabbitListener(queues = RabbitMQConfig.AGENT_RESULT_QUEUE)
    public void onMessage(String message) {
        try {
            agentUnifiedResultService.processAgentResult(RabbitMQConfig.AGENT_RESULT_QUEUE, message);
        } catch (Exception e) {
            log.error("AgentResultListener unexpected error (message already ACKed): queue={}, msg={}",
                    RabbitMQConfig.AGENT_RESULT_QUEUE, message, e);
        }
    }
}
