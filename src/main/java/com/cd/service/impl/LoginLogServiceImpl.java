package com.cd.service.impl;

import com.cd.common.PageResult;
import com.cd.common.security.TenantContextHolder;
import com.cd.dto.LoginLogResponseDTO;
import com.cd.entity.LoginLogEntity;
import com.cd.mapper.LoginLogMapper;
import com.cd.service.LoginLogService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class LoginLogServiceImpl implements LoginLogService {

    private final LoginLogMapper loginLogMapper;

    @Override
    public void record(Long userId, String userName, String ipAddress, Integer status, String message) {
        LoginLogEntity entity = new LoginLogEntity();
        entity.setTenantId(currentTenantId());
        entity.setUserId(userId);
        entity.setUserName(userName);
        entity.setIpAddress(ipAddress);
        entity.setStatus(status);
        entity.setMessage(message);
        loginLogMapper.insert(entity);
    }

    @Override
    public PageResult<LoginLogResponseDTO> list(int page, int size, String userName, Integer status) {
        int offset = (page - 1) * size;
        Long tenantId = currentTenantId();
        long total = loginLogMapper.countAll(userName, status, tenantId);
        List<LoginLogResponseDTO> list = loginLogMapper.selectPage(offset, size, userName, status, tenantId)
                .stream()
                .map(this::toResponse)
                .toList();
        return new PageResult<>(total, list);
    }

    @Override
    public long countTodaySuccess() {
        return loginLogMapper.countTodaySuccess(currentTenantId());
    }

    @Override
    public long countWeekActiveUsers() {
        return loginLogMapper.countWeekActiveUsers(currentTenantId());
    }

    @Override
    public long countTotalLogs() {
        return loginLogMapper.countTotalLogs(currentTenantId());
    }

    private Long currentTenantId() {
        Long tenantId = TenantContextHolder.getTenantId();
        return tenantId == null ? 0L : tenantId;
    }

    private LoginLogResponseDTO toResponse(LoginLogEntity entity) {
        LoginLogResponseDTO dto = new LoginLogResponseDTO();
        dto.setId(entity.getId());
        dto.setUserId(entity.getUserId());
        dto.setUserName(entity.getUserName());
        dto.setLoginTime(entity.getLoginTime());
        dto.setIpAddress(entity.getIpAddress());
        dto.setStatus(entity.getStatus());
        dto.setMessage(entity.getMessage());
        return dto;
    }
}
