package com.cd.service;

import com.cd.common.PageResult;
import com.cd.dto.HostOptionDTO;
import com.cd.dto.LoginSecurityLogDetailDTO;
import com.cd.dto.LoginSecurityLogItemDTO;
import com.cd.dto.LoginSecurityLogQueryDTO;
import com.cd.dto.LoginSecurityLogStatDTO;

import java.util.List;

public interface LoginSecurityLogViewService {

    PageResult<LoginSecurityLogItemDTO> page(LoginSecurityLogQueryDTO query, int page, int size, String sortField, String sortOrder);

    LoginSecurityLogStatDTO stats();

    LoginSecurityLogDetailDTO detail(Long id);

    List<HostOptionDTO> hostOptions();

    byte[] exportCsv(LoginSecurityLogQueryDTO query, String sortField, String sortOrder);
}
