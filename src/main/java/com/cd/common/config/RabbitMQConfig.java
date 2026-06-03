package com.cd.common.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.Queue;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * RabbitMQ 配置：声明系统信息上报队列及其与已有交换机的绑定。
 *
 * <p>这里只声明队列与绑定，不重新声明 {@code sysinfo_exchange}。该交换机由采集端
 * 维护，类型可能与本服务的声明不一致；只声明绑定可避免因类型不匹配导致声明失败。</p>
 */
@Configuration
public class RabbitMQConfig {

    public static final String SYSINFO_QUEUE = "sysinfo_queue";
    public static final String SYSINFO_EXCHANGE = "sysinfo_exchange";
    public static final String SYSINFO_ROUTING_KEY = "sysinfo";

    @Bean
    public Queue sysinfoQueue() {
        return new Queue(SYSINFO_QUEUE, true);
    }

    @Bean
    public Binding sysinfoBinding() {
        return new Binding(
                SYSINFO_QUEUE,
                Binding.DestinationType.QUEUE,
                SYSINFO_EXCHANGE,
                SYSINFO_ROUTING_KEY,
                null
        );
    }
}
