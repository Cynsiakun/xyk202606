package com.cd.mq;

import com.cd.entity.AccountChangeLogEntity;
import com.cd.entity.LoginSecurityLogEntity;
import com.cd.entity.SecurityAlertEntity;
import com.cd.entity.WindowsEventLogEntity;
import com.cd.mapper.AccountChangeLogMapper;
import com.cd.mapper.LoginSecurityLogMapper;
import com.cd.mapper.SecurityAlertMapper;
import com.cd.mapper.WindowsEventLogMapper;
import com.cd.security.SecurityEventContext;
import com.cd.service.SecurityAlertRuleEngine;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * Windows 事件日志批量写入器（含登录 / 账户变更分流）。
 *
 * <p>消费者只把校验、映射好的 {@link EventLogMessage} 放进线程安全的有界缓冲队列；本类用
 * {@code @Scheduled(fixedDelay = 1000)} 周期性 drain 最多 {@code batchSize} 条组成一个批次，
 * 在<b>同一个事务</b>内顺序完成：</p>
 * <ol>
 *   <li>{@code INSERT IGNORE} 批量写 {@code windows_event_logs}；</li>
 *   <li>按幂等键反查这批行的 id，作为子记录的 {@code source_log_id}；</li>
 *   <li>{@code INSERT IGNORE} 批量写 {@code login_security_logs} / {@code account_change_logs}。</li>
 * </ol>
 * <ul>
 *   <li>全部成功 → 提交事务 → 对该批每条消息统一 {@code basicAck}。</li>
 *   <li>任何异常 → 回滚事务（主表与子表一起回滚）→ 整批 {@code basicNack(requeue=true)} 重投，不丢失。</li>
 * </ul>
 *
 * <p>幂等：主表唯一键 {@code uk_host_log_rec}、子表唯一键 {@code uk_source_log} 配合 {@code INSERT IGNORE}，
 * 重复消费不产生重复行，并照常 ACK。</p>
 *
 * <p>缓冲队列有界，消费者用阻塞 {@code put} 入队：库写不动时消费线程被阻塞、prefetch 限制未确认消息数，
 * 形成自然背压，避免高峰期内存溢出。</p>
 */
@Slf4j
@Component
public class WindowsEventLogBatchWriter {

    private final WindowsEventLogMapper windowsEventLogMapper;
    private final LoginSecurityLogMapper loginSecurityLogMapper;
    private final AccountChangeLogMapper accountChangeLogMapper;
    private final SecurityAlertMapper securityAlertMapper;
    private final SecurityAlertRuleEngine securityAlertRuleEngine;
    private final TransactionTemplate transactionTemplate;
    private final BlockingQueue<EventLogMessage> buffer;
    private final int batchSize;
    private final int maxBatchesPerTick;

    public WindowsEventLogBatchWriter(
            WindowsEventLogMapper windowsEventLogMapper,
            LoginSecurityLogMapper loginSecurityLogMapper,
            AccountChangeLogMapper accountChangeLogMapper,
            SecurityAlertMapper securityAlertMapper,
            SecurityAlertRuleEngine securityAlertRuleEngine,
            PlatformTransactionManager transactionManager,
            @Value("${app.windows-log.queue-capacity:5000}") int queueCapacity,
            @Value("${app.windows-log.batch-size:100}") int batchSize,
            @Value("${app.windows-log.max-batches-per-tick:50}") int maxBatchesPerTick) {
        this.windowsEventLogMapper = windowsEventLogMapper;
        this.loginSecurityLogMapper = loginSecurityLogMapper;
        this.accountChangeLogMapper = accountChangeLogMapper;
        this.securityAlertMapper = securityAlertMapper;
        this.securityAlertRuleEngine = securityAlertRuleEngine;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.buffer = new LinkedBlockingQueue<>(queueCapacity);
        this.batchSize = batchSize;
        this.maxBatchesPerTick = maxBatchesPerTick;
    }

    /**
     * 由消费者调用，把一条待入库消息放入缓冲队列。队列满时阻塞，形成背压。
     *
     * @throws InterruptedException 等待入队时线程被中断
     */
    public void enqueue(EventLogMessage message) throws InterruptedException {
        buffer.put(message);
    }

    /**
     * 周期性从缓冲队列连续 drain 多个批次并入库，直到队列清空或达到单次上限
     * {@code maxBatchesPerTick}（防止单次调度长时间占用调度线程）。
     *
     * <p>循环 drain 而非每秒只处理一批，是为了避免 {@code fixedDelay=1000} 把吞吐
     * 钉死在「batchSize 条/秒」，从而能在高峰期快速消化积压。</p>
     */
    @Scheduled(fixedDelay = 1000)
    public void drainAndFlush() {
        int batches = 0;
        while (batches < maxBatchesPerTick && !buffer.isEmpty()) {
            List<EventLogMessage> batch = new ArrayList<>(batchSize);
            buffer.drainTo(batch, batchSize);
            if (batch.isEmpty()) {
                return;
            }
            flushBatch(batch);
            batches++;
        }
    }

    private void flushBatch(List<EventLogMessage> batch) {
        long start = System.currentTimeMillis();
        List<WindowsEventLogEntity> entities = new ArrayList<>(batch.size());
        for (EventLogMessage m : batch) {
            entities.add(m.getEntity());
        }

        try {
            BatchStats stats = transactionTemplate.execute(status -> persist(batch, entities));
            long cost = System.currentTimeMillis() - start;
            ackAll(batch);
            int duplicates = stats.winAttempted - stats.winInserted;
            if (duplicates > 0 || stats.loginInserted > 0 || stats.accountInserted > 0) {
                log.info("Batch insert: {} attempted, {} inserted, {} duplicates ignored; "
                                + "login +{}, account +{}, alert +{}; cost={}ms",
                        stats.winAttempted, stats.winInserted, duplicates,
                        stats.loginInserted, stats.accountInserted, stats.alertInserted, cost);
            } else {
                log.debug("Batch insert: {} attempted, {} inserted, cost={}ms",
                        stats.winAttempted, stats.winInserted, cost);
            }
        } catch (Exception e) {
            // 事务已回滚（主表与子表一起回滚）：整批 NACK 重新入队，保证不丢失
            log.error("批量写入失败，整批 NACK 重新入队: size={}", batch.size(), e);
            nackAll(batch);
        }
    }

    /**
     * 在单个事务内完成：主表入库 → 反查 source_log_id → 子表分流入库。
     * 任一步抛异常都会让整个事务回滚。
     */
    private BatchStats persist(List<EventLogMessage> batch, List<WindowsEventLogEntity> entities) {
        BatchStats stats = new BatchStats();
        stats.winAttempted = entities.size();
        stats.winInserted = windowsEventLogMapper.insertBatchIgnore(entities);

        // 反查这批行（含历史已存在的重复行）的 id，作为子记录的 source_log_id
        Map<String, Long> idByKey = new HashMap<>();
        for (WindowsEventLogEntity row : windowsEventLogMapper.selectIdsByKeys(entities)) {
            idByKey.put(idempotencyKey(row), row.getId());
        }

        List<LoginSecurityLogEntity> logins = new ArrayList<>();
        List<AccountChangeLogEntity> accounts = new ArrayList<>();
        for (EventLogMessage m : batch) {
            if (m.getLogin() == null && m.getAccount() == null) {
                continue;
            }
            Long sourceLogId = idByKey.get(idempotencyKey(m.getEntity()));
            if (sourceLogId == null) {
                // 理论上不会发生（主表刚写入/已存在）；缺 id 则跳过子记录，避免 source_log_id 为空
                log.warn("分流跳过：未找到 source_log_id, host={}, type={}, record={}",
                        m.getEntity().getHostId(), m.getEntity().getLogType(), m.getEntity().getRecordNumber());
                continue;
            }
            if (m.getLogin() != null) {
                m.getLogin().setSourceLogId(sourceLogId);
                logins.add(m.getLogin());
            }
            if (m.getAccount() != null) {
                m.getAccount().setSourceLogId(sourceLogId);
                accounts.add(m.getAccount());
            }
        }

        if (!logins.isEmpty()) {
            stats.loginInserted = loginSecurityLogMapper.insertBatchIgnore(logins);
        }
        if (!accounts.isEmpty()) {
            stats.accountInserted = accountChangeLogMapper.insertBatchIgnore(accounts);
        }

        try {
            List<SecurityEventContext> contexts = buildAlertContexts(batch, idByKey);
            if (!contexts.isEmpty()) {
                List<SecurityAlertEntity> alerts = securityAlertRuleEngine.evaluate(contexts);
                if (!alerts.isEmpty()) {
                    stats.alertInserted = securityAlertMapper.insertBatch(alerts);
                }
            }
        } catch (Exception ex) {
            log.error("Security alert evaluation failed, keeping raw log ingestion intact. batchSize={}", batch.size(), ex);
        }
        return stats;
    }

    private List<SecurityEventContext> buildAlertContexts(List<EventLogMessage> batch, Map<String, Long> idByKey) {
        List<SecurityEventContext> contexts = new ArrayList<>(batch.size());
        for (EventLogMessage message : batch) {
            Long sourceLogId = idByKey.get(idempotencyKey(message.getEntity()));
            if (sourceLogId == null) {
                continue;
            }
            contexts.add(SecurityEventContext.builder()
                    .sourceLogId(sourceLogId)
                    .eventLog(message.getEntity())
                    .loginLog(message.getLogin())
                    .accountLog(message.getAccount())
                    .eventData(WindowsSecurityEventParser.extractEventData(message.getEntity().getRawJson()))
                    .build());
        }
        return contexts;
    }

    private static String idempotencyKey(WindowsEventLogEntity e) {
        return e.getHostId() + "|" + e.getLogType() + "|" + e.getRecordNumber();
    }

    /** 单批写入统计，用于汇总日志。 */
    private static final class BatchStats {
        int winAttempted;
        int winInserted;
        int loginInserted;
        int accountInserted;
        int alertInserted;
    }

    private void ackAll(List<EventLogMessage> batch) {
        for (EventLogMessage m : batch) {
            try {
                m.getChannel().basicAck(m.getDeliveryTag(), false);
            } catch (Exception ex) {
                // channel 可能因连接抖动失效；该消息未确认会在重连后由 broker 重新投递，幂等保证不重复
                log.warn("basicAck 失败，等待消息重投: deliveryTag={}", m.getDeliveryTag(), ex);
            }
        }
    }

    private void nackAll(List<EventLogMessage> batch) {
        for (EventLogMessage m : batch) {
            try {
                m.getChannel().basicNack(m.getDeliveryTag(), false, true);
            } catch (Exception ex) {
                log.warn("basicNack 失败: deliveryTag={}", m.getDeliveryTag(), ex);
            }
        }
    }

    /**
     * 关闭前把缓冲里剩余的消息冲刷入库，尽量减少重连后重投的数量。
     */
    @PreDestroy
    public void flushOnShutdown() {
        List<EventLogMessage> remaining = new ArrayList<>();
        buffer.drainTo(remaining);
        if (remaining.isEmpty()) {
            return;
        }
        log.info("关闭前冲刷缓冲队列剩余 {} 条 Windows 日志", remaining.size());
        for (int i = 0; i < remaining.size(); i += batchSize) {
            flushBatch(remaining.subList(i, Math.min(i + batchSize, remaining.size())));
        }
    }
}
