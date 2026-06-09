package com.cd.service;

import com.cd.dto.VulnOpsDashboardResponseDTO;

import java.time.LocalDate;

public interface VulnOpsDashboardService {

    VulnOpsDashboardResponseDTO overview(String range, LocalDate startDate, LocalDate endDate);
}
