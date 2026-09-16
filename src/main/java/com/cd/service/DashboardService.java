package com.cd.service;

import com.cd.dto.DashboardOverviewDTO;
import com.cd.dto.DashboardStatisticsDTO;

public interface DashboardService {

    DashboardStatisticsDTO statistics();

    DashboardOverviewDTO overview();
}
