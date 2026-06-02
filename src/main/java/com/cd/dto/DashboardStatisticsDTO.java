package com.cd.dto;

import lombok.Data;

@Data
public class DashboardStatisticsDTO {

    private Long totalUsers;
    private Long todayLoginCount;
    private Long todayNewUsers;
    private Long weekActiveUsers;
    private Long totalLogs;
}
