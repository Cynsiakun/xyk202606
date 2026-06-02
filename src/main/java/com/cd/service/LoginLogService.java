package com.cd.service;

import com.cd.common.PageResult;
import com.cd.dto.LoginLogResponseDTO;

public interface LoginLogService {

    void record(Long userId, String userName, String ipAddress, Integer status, String message);

    PageResult<LoginLogResponseDTO> list(int page, int size, String userName, Integer status);

    long countTodaySuccess();

    long countWeekActiveUsers();

    long countTotalLogs();
}
