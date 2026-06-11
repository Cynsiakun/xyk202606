package com.cd.common.config;

import org.springframework.amqp.core.AcknowledgeMode;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.beans.factory.annotation.Value;
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
    public static final String VULN_VERIFY_RESULT_QUEUE = "vuln_verify_result_queue";
    public static final String VULN_VERIFY_RESULT_ROUTING_KEY = "vuln_verify_result";
    public static final String BASELINE_QUEUE = "baseline_queue";
    public static final String BASELINE_ROUTING_KEY = "baseline";

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

    /** 资产探测结果队列：账户。 */
    public static final String ACCOUNT_QUEUE = "account_queue";
    /** 资产探测结果队列：服务。 */
    public static final String SERVICE_QUEUE = "service_queue";
    /** 资产探测结果队列：进程。 */
    public static final String PROCESS_QUEUE = "process_queue";
    /** 资产探测结果队列：安装软件。 */
    public static final String APP_QUEUE = "app_queue";
    public static final String PATCH_SCAN_EXCHANGE = "patch_exchange";
    public static final String PATCH_SCAN_ROUTING_KEY = "patch_scan";
    public static final String PATCH_SCAN_QUEUE = "patch_scan_queue";

    /**
     * Windows 事件日志队列：采集端通过 {@code log_exchange} + 路由键 {@code security_log}
     * 投递，消费后批量写入 {@code windows_event_logs}。
     *
     * <p>该队列与交换机由采集端/运维预先创建，本服务<b>只消费、不声明</b>，避免参数不一致
     * 导致的 PRECONDITION_FAILED。无死信队列，坏消息改落 {@code mq_error_logs} 表后正常 ACK。</p>
     */
    public static final String LOG_QUEUE = "log_queue";
    /** 重试次数消息头：主机未注册时按此计数，超过上限不再重投，落 mq_error_logs。 */
    public static final String HEADER_RETRY_COUNT = "x-retry-count";

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
    public Queue vulnVerifyResultQueue() {
        return new Queue(VULN_VERIFY_RESULT_QUEUE, true);
    }

    @Bean
    public Binding vulnVerifyResultBinding() {
        return new Binding(
                VULN_VERIFY_RESULT_QUEUE,
                Binding.DestinationType.QUEUE,
                SYSINFO_EXCHANGE,
                VULN_VERIFY_RESULT_ROUTING_KEY,
                null
        );
    }

    @Bean
    public Queue baselineQueue() {
        return new Queue(BASELINE_QUEUE, true);
    }

    @Bean
    public Binding baselineBinding() {
        return new Binding(
                BASELINE_QUEUE,
                Binding.DestinationType.QUEUE,
                SYSINFO_EXCHANGE,
                BASELINE_ROUTING_KEY,
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

    @Bean
    public Queue accountQueue() {
        return new Queue(ACCOUNT_QUEUE, true);
    }

    @Bean
    public Queue serviceQueue() {
        return new Queue(SERVICE_QUEUE, true);
    }

    @Bean
    public Queue processQueue() {
        return new Queue(PROCESS_QUEUE, true);
    }

    @Bean
    public Queue appQueue() {
        return new Queue(APP_QUEUE, true);
    }

    @Bean
    public DirectExchange patchScanExchange() {
        return new DirectExchange(PATCH_SCAN_EXCHANGE, true, false);
    }

    @Bean
    public Queue patchScanQueue() {
        return new Queue(PATCH_SCAN_QUEUE, true);
    }

    @Bean
    public Binding patchScanBinding() {
        return BindingBuilder.bind(patchScanQueue()).to(patchScanExchange()).with(PATCH_SCAN_ROUTING_KEY);
    }

    /**
     * Windows 日志专用手动 ACK 容器工厂：并发数可配置，单条预取放大以喂饱批量缓冲。
     *
     * <p>{@code concurrency} 由 {@code app.windows-log.concurrency} 控制（默认 2）。
     * 入库的确认由批量写入器统一完成；消息可靠性靠手动 ACK 保证，坏消息落 mq_error_logs。</p>
     */
    @Bean
    public SimpleRabbitListenerContainerFactory windowsLogContainerFactory(
            ConnectionFactory connectionFactory,
            SimpleRabbitListenerContainerFactoryConfigurer configurer,
            @Value("${app.windows-log.concurrency:2}") int concurrency,
            @Value("${app.windows-log.prefetch:200}") int prefetch) {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        configurer.configure(factory, connectionFactory);
        factory.setAcknowledgeMode(AcknowledgeMode.MANUAL);
        factory.setConcurrentConsumers(concurrency);
        factory.setMaxConcurrentConsumers(Math.max(concurrency, concurrency * 2));
        factory.setPrefetchCount(prefetch);
        return factory;
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
