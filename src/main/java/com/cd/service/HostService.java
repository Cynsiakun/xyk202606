package com.cd.service;

import com.cd.common.PageResult;
import com.cd.dto.AssetProbeDTO;
import com.cd.dto.HostCreateDTO;
import com.cd.dto.HostResponseDTO;
import com.cd.dto.HostUpdateDTO;
import com.cd.entity.HostEntity;

public interface HostService {

    HostResponseDTO create(HostCreateDTO dto);

    HostResponseDTO update(Long id, HostUpdateDTO dto);

    void deleteById(Long id);

    HostResponseDTO getById(Long id);

    PageResult<HostResponseDTO> list(int page, int size, String keyword);

    /**
     * 供 RabbitMQ 监听器使用：按 MAC 唯一性做存在即更新、不存在即插入。
     */
    void saveOrUpdateFromMessage(HostEntity entity);

    /**
     * 心跳处理：按 MAC 将主机置为在线并刷新最后活跃时间，不存在则新建。
     */
    void heartbeat(String macAddress);

    /**
     * 离线检测：将最后活跃时间超过阈值的在线主机置为离线。
     *
     * @param offlineThresholdSeconds 离线阈值（秒）
     * @return 本次被置为离线的主机数量
     */
    int markOfflineHosts(int offlineThresholdSeconds);

    int autoProbeOnlineHosts(int limit);

    /**
     * 下发资产探测任务：按勾选项组装消息并发送到 {@code agent_exchange}，
     * 路由键为目标主机的 MAC 地址（与客户端专属队列绑定时一致）。
     */
    void sendAssetProbe(AssetProbeDTO dto);
}
