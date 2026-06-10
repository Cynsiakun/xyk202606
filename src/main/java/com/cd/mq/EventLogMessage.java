package com.cd.mq;

import com.cd.entity.AccountChangeLogEntity;
import com.cd.entity.LoginSecurityLogEntity;
import com.cd.entity.WindowsEventLogEntity;
import com.rabbitmq.client.Channel;
import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * 进入批量缓冲队列的一条 Windows 事件日志。
 *
 * <p>同时持有解析好的待入库实体，以及该消息对应的 {@link Channel} 与 deliveryTag，
 * 以便批次写库成功/失败后对消息做统一 ACK / NACK。每条消息在自己被消费的 channel 上
 * 确认（不同 channel 不可跨用同一个 deliveryTag）。</p>
 *
 * <p>{@code login} / {@code account} 为分流解析出的子记录，仅登录/账户变更事件才非空；
 * 其 {@code sourceLogId} 在批量写入器确定 windows_event_logs.id 后回填，再与主记录在同一事务内入库。</p>
 */
@Data
@AllArgsConstructor
public class EventLogMessage {

    private final WindowsEventLogEntity entity;
    /** 登录安全子记录，非登录事件为 null。 */
    private final LoginSecurityLogEntity login;
    /** 账户变更子记录，非账户事件为 null。 */
    private final AccountChangeLogEntity account;
    private final Channel channel;
    private final long deliveryTag;
}
