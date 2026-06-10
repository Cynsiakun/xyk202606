package com.cd.mapper;

import com.cd.entity.AccountChangeLogEntity;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * {@code account_change_logs} 持久化。
 */
public interface AccountChangeLogMapper {

    /**
     * 批量插入，{@code INSERT IGNORE} 命中唯一键 {@code uk_source_log(source_log_id)} 的重复记录被忽略。
     *
     * @return 实际写入行数
     */
    int insertBatchIgnore(@Param("list") List<AccountChangeLogEntity> list);

    List<AccountChangeLogEntity> selectRecentActions(@Param("hostId") Long hostId,
                                                     @Param("targetUsername") String targetUsername,
                                                     @Param("actionTypes") List<String> actionTypes,
                                                     @Param("since") java.time.LocalDateTime since,
                                                     @Param("until") java.time.LocalDateTime until);

    List<AccountChangeLogEntity> selectRecentActionsByHost(@Param("hostId") Long hostId,
                                                           @Param("actionTypes") List<String> actionTypes,
                                                           @Param("since") java.time.LocalDateTime since,
                                                           @Param("until") java.time.LocalDateTime until);
}
