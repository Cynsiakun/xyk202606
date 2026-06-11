package com.cd.service;

import com.cd.common.PageResult;
import com.cd.dto.HostOptionDTO;
import com.cd.dto.PopupAlertDTO;
import com.cd.dto.SecurityEventDetailDTO;
import com.cd.dto.SecurityEventItemDTO;
import com.cd.dto.SecurityEventQueryDTO;
import com.cd.dto.SecurityEventStatDTO;

import java.util.List;

public interface SecurityEventService {

    PageResult<SecurityEventItemDTO> page(SecurityEventQueryDTO query, int page, int size, String sortField, String sortOrder);

    SecurityEventStatDTO stats();

    SecurityEventDetailDTO detail(Long id);

    List<HostOptionDTO> hostOptions();

    int ack(List<Long> ids);

    int resolve(List<Long> ids);

    byte[] exportCsv(SecurityEventQueryDTO query, String sortField, String sortOrder);

    Long currentMaxId();

    List<PopupAlertDTO> newHighCritical(Long afterId);

    List<PopupAlertDTO> recentHighCritical();
}
