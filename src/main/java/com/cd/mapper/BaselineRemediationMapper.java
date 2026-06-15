package com.cd.mapper;

import com.cd.entity.BaselineRemediationEntity;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface BaselineRemediationMapper {

    int insert(BaselineRemediationEntity entity);

    BaselineRemediationEntity selectById(@Param("id") Long id);

    BaselineRemediationEntity selectLatestSuccessfulByResultId(@Param("resultId") Long resultId);

    BaselineRemediationEntity selectLatestSuccessfulByResultScope(@Param("hostId") Long hostId,
                                                                  @Param("ruleId") Long ruleId,
                                                                  @Param("checkKey") String checkKey);

    BaselineRemediationEntity selectLatestPendingByResultId(@Param("resultId") Long resultId);

    BaselineRemediationEntity selectLatestFinishedByResultId(@Param("resultId") Long resultId);

    List<BaselineRemediationEntity> selectByResultId(@Param("resultId") Long resultId);

    List<BaselineRemediationEntity> selectByResultScope(@Param("hostId") Long hostId,
                                                        @Param("ruleId") Long ruleId,
                                                        @Param("checkKey") String checkKey);

    int updateStarted(@Param("id") Long id);

    int updateResult(@Param("id") Long id,
                     @Param("status") String status,
                     @Param("oldValue") String oldValue,
                     @Param("newValue") String newValue,
                     @Param("backupData") String backupData,
                     @Param("message") String message);

    int markFailed(@Param("id") Long id);
}
