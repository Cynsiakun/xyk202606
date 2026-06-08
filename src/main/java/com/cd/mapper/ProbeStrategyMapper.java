package com.cd.mapper;

import com.cd.entity.ProbeStrategyEntity;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;

public interface ProbeStrategyMapper {

    /**
     * 读取全局探测策略（固定 id=1 的单行）。
     */
    ProbeStrategyEntity selectStrategy();

    /**
     * 更新全局探测策略（固定 id=1 的单行）。
     */
    int updateStrategy(ProbeStrategyEntity entity);

    /**
     * 调度器下发后回写本次执行时间。
     */
    int updateLastRunAt(@Param("lastRunAt") LocalDateTime lastRunAt);
}
