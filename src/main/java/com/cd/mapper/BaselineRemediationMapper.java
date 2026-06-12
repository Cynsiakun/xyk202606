package com.cd.mapper;

import com.cd.entity.BaselineRemediationEntity;
import org.apache.ibatis.annotations.Param;

public interface BaselineRemediationMapper {

    int insert(BaselineRemediationEntity entity);

    BaselineRemediationEntity selectById(@Param("id") Long id);

    BaselineRemediationEntity selectLatestSuccessfulByResultId(@Param("resultId") Long resultId);

    BaselineRemediationEntity selectLatestPendingByResultId(@Param("resultId") Long resultId);

    int updateStarted(@Param("id") Long id);

    int updateResult(@Param("id") Long id,
                     @Param("status") String status,
                     @Param("oldValue") String oldValue,
                     @Param("newValue") String newValue,
                     @Param("backupData") String backupData);

    int markRollback(@Param("id") Long id);

    int markFailed(@Param("id") Long id);
}
