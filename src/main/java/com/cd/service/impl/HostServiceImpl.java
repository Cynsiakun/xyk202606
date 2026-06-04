package com.cd.service.impl;

import com.cd.common.PageResult;
import com.cd.common.config.RabbitMQConfig;
import com.cd.common.exception.ResourceNotFoundException;
import com.cd.dto.AssetProbeDTO;
import com.cd.dto.HostCreateDTO;
import com.cd.dto.HostResponseDTO;
import com.cd.dto.HostUpdateDTO;
import com.cd.entity.HostEntity;
import com.cd.mapper.HostMapper;
import com.cd.service.HostService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class HostServiceImpl implements HostService {

    /** 读取列表时的在线判定阈值（秒）：updated_at 在该时间内视为在线，否则离线。 */
    private static final int ONLINE_THRESHOLD_SECONDS = 4;

    /** 资产探测任务的类型标识，后续可扩展其他类型任务。 */
    private static final String PROBE_TYPE_ASSETS = "assets";

    /** 资产探测前的在线判定阈值（秒）：updated_at 距当前超过该值视为离线。 */
    private static final int PROBE_ONLINE_THRESHOLD_SECONDS = 15;

    private final HostMapper hostMapper;
    private final RabbitTemplate rabbitTemplate;
    private final ObjectMapper objectMapper;
    private final AmqpAdmin amqpAdmin;

    @Override
    public HostResponseDTO create(HostCreateDTO dto) {
        validateMacUnique(null, dto.getMacAddress());
        HostEntity entity = new HostEntity();
        entity.setHostname(dto.getHostname());
        entity.setIpv4(dto.getIpv4());
        entity.setMacAddress(dto.getMacAddress());
        entity.setOsName(dto.getOsName());
        entity.setOsVersion(dto.getOsVersion());
        entity.setOsArch(dto.getOsArch());
        entity.setOsRelease(dto.getOsRelease());
        entity.setCpuModel(dto.getCpuModel());
        entity.setCpuPhysicalCores(dto.getCpuPhysicalCores());
        entity.setCpuLogicalCores(dto.getCpuLogicalCores());
        entity.setMemTotal(dto.getMemTotal());
        entity.setMemUsed(dto.getMemUsed());
        entity.setMemAvailable(dto.getMemAvailable());
        entity.setMemUsage(dto.getMemUsage());
        entity.setStatus(dto.getStatus() == null ? 1 : dto.getStatus());
        hostMapper.insert(entity);
        return toResponse(hostMapper.selectById(entity.getId()));
    }

    @Override
    public HostResponseDTO update(Long id, HostUpdateDTO dto) {
        HostEntity existing = ensureExists(id);
        validateMacUnique(id, dto.getMacAddress());
        existing.setHostname(dto.getHostname());
        existing.setIpv4(dto.getIpv4());
        existing.setMacAddress(dto.getMacAddress());
        existing.setOsName(dto.getOsName());
        existing.setOsVersion(dto.getOsVersion());
        existing.setOsArch(dto.getOsArch());
        existing.setOsRelease(dto.getOsRelease());
        existing.setCpuModel(dto.getCpuModel());
        existing.setCpuPhysicalCores(dto.getCpuPhysicalCores());
        existing.setCpuLogicalCores(dto.getCpuLogicalCores());
        existing.setMemTotal(dto.getMemTotal());
        existing.setMemUsed(dto.getMemUsed());
        existing.setMemAvailable(dto.getMemAvailable());
        existing.setMemUsage(dto.getMemUsage());
        existing.setStatus(dto.getStatus() == null ? existing.getStatus() : dto.getStatus());
        hostMapper.updateById(existing);
        return toResponse(hostMapper.selectById(id));
    }

    @Override
    public void deleteById(Long id) {
        ensureExists(id);
        hostMapper.deleteById(id);
    }

    @Override
    public HostResponseDTO getById(Long id) {
        return toResponse(ensureExists(id));
    }

    @Override
    public PageResult<HostResponseDTO> list(int page, int size, String keyword) {
        // 读取前先按心跳时间校正在线状态：超过阈值未上报的主机置为离线，阈值内的置为在线。
        hostMapper.reconcileStatusByHeartbeat(ONLINE_THRESHOLD_SECONDS);
        int offset = (page - 1) * size;
        String normalizedKeyword = emptyToNull(keyword);
        long total = hostMapper.countAll(normalizedKeyword);
        List<HostResponseDTO> list = hostMapper.selectPage(offset, size, normalizedKeyword)
                .stream()
                .map(this::toResponse)
                .toList();
        return new PageResult<>(total, list);
    }

    @Override
    public void saveOrUpdateFromMessage(HostEntity entity) {
        if (entity.getStatus() == null) {
            entity.setStatus(1);
        }
        hostMapper.upsertByMac(entity);
    }

    @Override
    public void heartbeat(String macAddress) {
        hostMapper.heartbeatByMac(macAddress);
    }

    @Override
    public int markOfflineHosts(int offlineThresholdSeconds) {
        return hostMapper.markOffline(offlineThresholdSeconds);
    }

    @Override
    public void sendAssetProbe(AssetProbeDTO dto) {
        String macAddress = dto.getMacAddress();
        if (!StringUtils.hasText(macAddress)) {
            throw new IllegalArgumentException("MAC地址不能为空");
        }

        // 校验一：主机在线。以服务端当前时间与 hosts.updated_at 比较，超过阈值视为离线。
        HostEntity host = hostMapper.selectByMac(macAddress);
        LocalDateTime updatedAt = host == null ? null : host.getUpdatedAt();
        if (updatedAt == null
                || Duration.between(updatedAt, LocalDateTime.now()).getSeconds() > PROBE_ONLINE_THRESHOLD_SECONDS) {
            throw new IllegalArgumentException("主机已下线，无法执行资产探测");
        }

        // 校验二：客户端专属队列存在。队列名与绑定时一致：agent_{标准化mac}_queue。
        String queueName = RabbitMQConfig.AGENT_QUEUE_PREFIX + normalizeMac(macAddress) + RabbitMQConfig.AGENT_QUEUE_SUFFIX;
        if (amqpAdmin.getQueueProperties(queueName) == null) {
            throw new IllegalArgumentException("客户端队列不存在，无法发送探测指令");
        }

        // 严格按约定的字段名与顺序组装消息：布尔转 1/0，type 固定为 assets。
        Map<String, Object> message = new LinkedHashMap<>();
        message.put("account", dto.isAccount() ? 1 : 0);
        message.put("service", dto.isService() ? 1 : 0);
        message.put("process", dto.isProcess() ? 1 : 0);
        message.put("app", dto.isApp() ? 1 : 0);
        message.put("macAddress", macAddress);
        message.put("type", PROBE_TYPE_ASSETS);

        String payload;
        try {
            payload = objectMapper.writeValueAsString(message);
        } catch (Exception e) {
            throw new IllegalStateException("组装资产探测消息失败", e);
        }

        // 路由键为 MAC 地址，与客户端专属队列绑定时使用的 MAC 保持一致。
        rabbitTemplate.convertAndSend(RabbitMQConfig.AGENT_EXCHANGE, macAddress, payload);
        log.info("资产探测任务已下发: routingKey={}, payload={}", macAddress, payload);
    }

    /** 标准化 MAC：转小写并去除冒号、横杠、空格等分隔符，与队列声明时保持一致。 */
    private String normalizeMac(String mac) {
        if (mac == null) {
            return "";
        }
        return mac.toLowerCase().replaceAll("[^0-9a-f]", "");
    }

    private HostEntity ensureExists(Long id) {
        HostEntity entity = hostMapper.selectById(id);
        if (entity == null) {
            throw new ResourceNotFoundException("记录不存在: id=" + id);
        }
        return entity;
    }

    private void validateMacUnique(Long id, String macAddress) {
        if (!StringUtils.hasText(macAddress)) {
            return;
        }
        HostEntity hostByMac = hostMapper.selectByMac(macAddress);
        if (hostByMac != null && !hostByMac.getId().equals(id)) {
            throw new IllegalArgumentException("MAC地址已存在");
        }
    }

    private String emptyToNull(String value) {
        return StringUtils.hasText(value) ? value : null;
    }

    private HostResponseDTO toResponse(HostEntity entity) {
        HostResponseDTO dto = new HostResponseDTO();
        dto.setId(entity.getId());
        dto.setHostname(entity.getHostname());
        dto.setIpv4(entity.getIpv4());
        dto.setMacAddress(entity.getMacAddress());
        dto.setOsName(entity.getOsName());
        dto.setOsVersion(entity.getOsVersion());
        dto.setOsArch(entity.getOsArch());
        dto.setOsRelease(entity.getOsRelease());
        dto.setCpuModel(entity.getCpuModel());
        dto.setCpuPhysicalCores(entity.getCpuPhysicalCores());
        dto.setCpuLogicalCores(entity.getCpuLogicalCores());
        dto.setMemTotal(entity.getMemTotal());
        dto.setMemUsed(entity.getMemUsed());
        dto.setMemAvailable(entity.getMemAvailable());
        dto.setMemUsage(entity.getMemUsage());
        dto.setStatus(entity.getStatus());
        dto.setCreatedAt(entity.getCreatedAt());
        dto.setUpdatedAt(entity.getUpdatedAt());
        return dto;
    }
}
