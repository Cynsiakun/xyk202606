package com.cd.service;

import com.cd.common.PageResult;
import com.cd.dto.AccountChangeLogDetailDTO;
import com.cd.dto.AccountChangeLogItemDTO;
import com.cd.dto.AccountChangeLogQueryDTO;
import com.cd.dto.AccountChangeLogStatDTO;
import com.cd.dto.HostOptionDTO;

import java.util.List;

public interface AccountChangeLogViewService {

    PageResult<AccountChangeLogItemDTO> page(AccountChangeLogQueryDTO query, int page, int size, String sortField, String sortOrder);

    AccountChangeLogStatDTO stats();

    AccountChangeLogDetailDTO detail(Long id);

    List<HostOptionDTO> hostOptions();

    byte[] exportCsv(AccountChangeLogQueryDTO query, String sortField, String sortOrder);
}
