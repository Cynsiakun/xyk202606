package com.cd.service;

import com.cd.dto.ProbeStrategyDTO;
import com.cd.entity.ProbeStrategyEntity;

import java.time.LocalDateTime;

public interface ProbeStrategyService {

    /**
     * 读取全局探测策略（供前端展示）。
     */
    ProbeStrategyDTO getStrategy();

    /**
     * 保存全局探测策略（写入数据库，立即生效，无需重启）。
     */
    ProbeStrategyDTO updateStrategy(ProbeStrategyDTO dto);

    /**
     * 供调度器读取的原始实体（含 lastRunAt 等）。
     */
    ProbeStrategyEntity getStrategyEntity();

    /**
     * 调度器下发后回写本次执行时间。
     */
    void markRun(LocalDateTime runAt);
}
