package com.cd.mapper;

import com.cd.entity.LoginSecurityLogEntity;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * {@code login_security_logs} 持久化。
 */
public interface LoginSecurityLogMapper {

    /**
     * 批量插入，{@code INSERT IGNORE} 命中唯一键 {@code uk_source_log(source_log_id)} 的重复记录被忽略。
     *
     * @return 实际写入行数
     */
    int insertBatchIgnore(@Param("list") List<LoginSecurityLogEntity> list);

    int countFailedLogins(@Param("hostId") Long hostId,
                          @Param("username") String username,
                          @Param("sourceIp") String sourceIp,
                          @Param("since") java.time.LocalDateTime since,
                          @Param("until") java.time.LocalDateTime until);

    List<String> selectFailedUsernamesByIp(@Param("hostId") Long hostId,
                                           @Param("sourceIp") String sourceIp,
                                           @Param("since") java.time.LocalDateTime since,
                                           @Param("until") java.time.LocalDateTime until);

    List<LoginSecurityLogEntity> selectFailedWithinWindow(@Param("hostId") Long hostId,
                                                          @Param("since") java.time.LocalDateTime since,
                                                          @Param("until") java.time.LocalDateTime until);
}
