package com.cd.service.impl;

import com.cd.dto.DashboardStatisticsDTO;
import com.cd.mapper.UserMapper;
import com.cd.service.DashboardService;
import com.cd.service.LoginLogService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class DashboardServiceImpl implements DashboardService {

    private final UserMapper userMapper;
    private final LoginLogService loginLogService;

    @Override
    public DashboardStatisticsDTO statistics() {
        DashboardStatisticsDTO dto = new DashboardStatisticsDTO();
        dto.setTotalUsers(userMapper.countAll(null));
        dto.setTodayLoginCount(loginLogService.countTodaySuccess());
        dto.setTodayNewUsers(userMapper.countCreatedToday());
        dto.setWeekActiveUsers(loginLogService.countWeekActiveUsers());
        dto.setTotalLogs(loginLogService.countTotalLogs());
        return dto;
    }
}
