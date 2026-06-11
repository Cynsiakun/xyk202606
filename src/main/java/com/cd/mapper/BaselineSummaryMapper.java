package com.cd.mapper;

import com.cd.entity.BaselineSummaryEntity;

public interface BaselineSummaryMapper {

    /** 按 (host_id, task_id) 唯一组合插入或更新汇总统计。 */
    int upsert(BaselineSummaryEntity entity);
}
