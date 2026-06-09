package com.cd.mapper;

import com.cd.entity.HostEntity;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface HostMapper {

    int insert(HostEntity entity);

    int updateById(HostEntity entity);

    int deleteById(@Param("id") Long id);

    HostEntity selectById(@Param("id") Long id);

    HostEntity selectByMac(@Param("macAddress") String macAddress);

    HostEntity selectByNormalizedMac(@Param("normalizedMac") String normalizedMac);

    /**
     * 按 MAC 唯一键做存在即更新、不存在即插入。
     */
    int upsertByMac(HostEntity entity);

    /**
     * 心跳：按 MAC 将主机置为在线（status=1）并刷新 updated_at；不存在则插入新记录。
     */
    int heartbeatByMac(@Param("macAddress") String macAddress);

    /**
     * 离线检测：将所有 status=1 且 updated_at 早于（当前时间 - seconds 秒）的主机置为离线（status=0）。
     *
     * @return 本次被置为离线的记录数
     */
    int markOffline(@Param("seconds") int seconds);

    /**
     * 读取列表时按心跳时间校正在线状态：updated_at 在 seconds 秒内置为在线(1)，否则离线(0)。
     *
     * <p>仅更新状态与时间判定不一致的行，且显式保留 updated_at（避免被 {@code ON UPDATE CURRENT_TIMESTAMP}
     * 刷新而污染最后心跳时间）。</p>
     *
     * @return 本次被校正的记录数
     */
    int reconcileStatusByHeartbeat(@Param("seconds") int seconds);

    int updateLastScanTimeByMac(@Param("macAddress") String macAddress,
                                @Param("lastScanTime") java.time.LocalDateTime lastScanTime);

    int updateLastScanTimeById(@Param("id") Long id,
                               @Param("lastScanTime") java.time.LocalDateTime lastScanTime);

    List<HostEntity> selectAutoProbeCandidates(@Param("limit") int limit);

    List<Long> selectAllIds();

    List<HostEntity> selectPage(@Param("offset") int offset,
                                @Param("size") int size,
                                @Param("keyword") String keyword);

    long countAll(@Param("keyword") String keyword);
}
