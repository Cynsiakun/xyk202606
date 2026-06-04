package com.cd.common.config;

import org.springframework.amqp.core.AcknowledgeMode;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.boot.autoconfigure.amqp.SimpleRabbitListenerContainerFactoryConfigurer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * RabbitMQ 配置：声明系统信息上报队列、主机心跳队列及其与已有交换机的绑定。
 *
 * <p>这里只声明队列与绑定，不重新声明 {@code sysinfo_exchange}。该交换机由采集端
 * 维护，类型可能与本服务的声明不一致；只声明绑定可避免因类型不匹配导致声明失败。</p>
 */
@Configuration
public class RabbitMQConfig {

    public static final String SYSINFO_QUEUE = "sysinfo_queue";
    public static final String SYSINFO_EXCHANGE = "sysinfo_exchange";
    public static final String SYSINFO_ROUTING_KEY = "sysinfo";

    /** 主机心跳队列：采集端每 3 秒上报一次 {"mac_address":"..."}。 */
    public static final String STATUS_QUEUE = "status_queue";
    public static final String STATUS_ROUTING_KEY = "status";

    /** 客户端专属消息的 Direct 交换机，按 MAC 地址路由到各自的 {@code agent_<mac>_queue}。 */
    public static final String AGENT_EXCHANGE = "agent_exchange";
    /** 客户端专属队列名前缀，完整格式为 {@code agent_<mac>_queue}。 */
    public static final String AGENT_QUEUE_PREFIX = "agent_";
    public static final String AGENT_QUEUE_SUFFIX = "_queue";
    /** 队列 3 天（259200000ms）未被访问自动删除。 */
    public static final long AGENT_QUEUE_EXPIRES = 259_200_000L;
    /** 队列内消息最大存活 3 小时（10800000ms）。 */
    public static final long AGENT_MESSAGE_TTL = 10_800_000L;

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

    @Bean
    public Queue statusQueue() {
        return new Queue(STATUS_QUEUE, true);
    }

    @Bean
    public Binding statusBinding() {
        return new Binding(
                STATUS_QUEUE,
                Binding.DestinationType.QUEUE,
                SYSINFO_EXCHANGE,
                STATUS_ROUTING_KEY,
                null
        );
    }

    /**
     * 手动 ACK 的监听容器工厂，专供心跳监听器使用：入库成功才 ACK，异常时 NACK 重新入队。
     *
     * <p>不覆盖默认的 {@code rabbitListenerContainerFactory}（仍为自动 ACK），
     * 因此既有的 {@link com.cd.mq.SysInfoListener} 行为保持不变。</p>
     */
    @Bean
    public SimpleRabbitListenerContainerFactory manualAckContainerFactory(
            ConnectionFactory connectionFactory,
            SimpleRabbitListenerContainerFactoryConfigurer configurer) {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        configurer.configure(factory, connectionFactory);
        factory.setAcknowledgeMode(AcknowledgeMode.MANUAL);
        return factory;
    }
}
