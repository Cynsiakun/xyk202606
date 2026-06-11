package com.cd.mapper;

import com.cd.entity.BaselineResultEntity;
import org.apache.ibatis.annotations.Param;

public interface BaselineResultMapper {

    int insert(BaselineResultEntity entity);

    /** 统计某 task_host 已生成的 result 数量，用于幂等判断。 */
    int countByTaskHostId(@Param("taskHostId") Long taskHostId);
}
