package com.cd.mapper;

import com.cd.entity.MqErrorLogEntity;

/**
 * MQ 消息异常记录持久化。
 */
public interface MqErrorLogMapper {

    int insert(MqErrorLogEntity entity);
}
