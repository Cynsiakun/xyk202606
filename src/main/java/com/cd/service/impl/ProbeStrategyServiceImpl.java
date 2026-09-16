package com.cd.service.impl;

import com.cd.dto.ProbeStrategyDTO;
import com.cd.entity.ProbeStrategyEntity;
import com.cd.mapper.ProbeStrategyMapper;
import com.cd.service.ProbeStrategyService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class ProbeStrategyServiceImpl implements ProbeStrategyService {

    private static final Set<Integer> ALLOWED_PERIODS = Set.of(1, 4, 8, 12, 24);

    private final ProbeStrategyMapper probeStrategyMapper;
    private final AgentCommandCleanupService agentCommandCleanupService;

    @Override
    public ProbeStrategyDTO getStrategy() {
        return toDto(getStrategyEntity());
    }

    @Override
    public ProbeStrategyDTO updateStrategy(ProbeStrategyDTO dto) {
        if (dto.getPeriodHours() == null || !ALLOWED_PERIODS.contains(dto.getPeriodHours())) {
            throw new IllegalArgumentException("探测周期仅支持 1/4/8/12/24 小时");
        }
        ProbeStrategyEntity entity = new ProbeStrategyEntity();
        entity.setEnabled(Boolean.TRUE.equals(dto.getEnabled()) ? 1 : 0);
        entity.setPeriodHours(dto.getPeriodHours());
        entity.setProbeAccount(dto.isAccount() ? 1 : 0);
        entity.setProbeService(dto.isService() ? 1 : 0);
        entity.setProbeProcess(dto.isProcess() ? 1 : 0);
        entity.setProbeApp(dto.isApp() ? 1 : 0);
        entity.setProbePortScan(dto.isPortScan() ? 1 : 0);
        entity.setProbeFingerprint(dto.isFingerprint() ? 1 : 0);
        entity.setPortScanRange(dto.getPortScanRange());
        entity.setPortScanCustomPorts(dto.getPortScanCustomPorts());
        probeStrategyMapper.updateStrategy(entity);
        if (!Boolean.TRUE.equals(dto.getEnabled()) || !dto.isPortScan()) {
            agentCommandCleanupService.clearPendingPortScanCommandsForAllHosts();
        }
        return getStrategy();
    }

    @Override
    public ProbeStrategyEntity getStrategyEntity() {
        return probeStrategyMapper.selectStrategy();
    }

    @Override
    public void markRun(LocalDateTime runAt) {
        probeStrategyMapper.updateLastRunAt(runAt);
    }

    @Override
    public void markPortScanRun(LocalDateTime runAt) {
        probeStrategyMapper.updateLastPortScanAt(runAt);
    }

    private ProbeStrategyDTO toDto(ProbeStrategyEntity entity) {
        ProbeStrategyDTO dto = new ProbeStrategyDTO();
        if (entity == null) {
            dto.setEnabled(false);
            dto.setPeriodHours(8);
            dto.setAccount(true);
            dto.setService(true);
            dto.setProcess(true);
            dto.setApp(true);
            return dto;
        }
        dto.setEnabled(entity.getEnabled() != null && entity.getEnabled() == 1);
        dto.setPeriodHours(entity.getPeriodHours() == null ? 8 : entity.getPeriodHours());
        dto.setAccount(entity.getProbeAccount() != null && entity.getProbeAccount() == 1);
        dto.setService(entity.getProbeService() != null && entity.getProbeService() == 1);
        dto.setProcess(entity.getProbeProcess() != null && entity.getProbeProcess() == 1);
        dto.setApp(entity.getProbeApp() != null && entity.getProbeApp() == 1);
        dto.setPortScan(entity.getProbePortScan() != null && entity.getProbePortScan() == 1);
        dto.setFingerprint(entity.getProbeFingerprint() != null && entity.getProbeFingerprint() == 1);
        dto.setPortScanRange(entity.getPortScanRange());
        dto.setPortScanCustomPorts(entity.getPortScanCustomPorts());
        dto.setLastRunAt(entity.getLastRunAt());
        return dto;
    }
}
