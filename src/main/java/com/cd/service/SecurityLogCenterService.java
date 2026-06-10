package com.cd.service;

import com.cd.common.PageResult;
import com.cd.dto.EventLogDetailDTO;
import com.cd.dto.EventLogItemDTO;
import com.cd.dto.EventLogQueryDTO;
import com.cd.dto.EventLogStatDTO;
import com.cd.dto.HostOptionDTO;

import java.util.List;

public interface SecurityLogCenterService {

    PageResult<EventLogItemDTO> page(EventLogQueryDTO query, int page, int size, String sortField, String sortOrder);

    EventLogStatDTO stats();

    EventLogDetailDTO detail(Long id);

    List<HostOptionDTO> hostOptions();

    byte[] exportCsv(EventLogQueryDTO query, String sortField, String sortOrder);
}
