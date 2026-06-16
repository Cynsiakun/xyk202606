package com.cd.service;

import com.cd.dto.BaselineTaskCreateRequestDTO;
import com.cd.dto.BaselineTaskDispatchResponseDTO;

public interface BaselineTaskService {

    BaselineTaskDispatchResponseDTO createAndDispatch(BaselineTaskCreateRequestDTO request);

    BaselineTaskDispatchResponseDTO createAndDispatchForTenant(BaselineTaskCreateRequestDTO request, Long tenantId);
}
