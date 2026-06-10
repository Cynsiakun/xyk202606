package com.cd.mapper;

import com.cd.entity.SecurityAlertEntity;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

/**
 * {@code security_alerts} 持久化。
 */
public interface SecurityAlertMapper {

    int insertBatch(@Param("list") List<SecurityAlertEntity> list);

    List<String> selectExistingDedupKeys(@Param("dedupKeys") List<String> dedupKeys,
                                         @Param("since") LocalDateTime since);
}
