package com.cd.service;

import com.cd.common.PageResult;
import com.cd.dto.TestCreateDTO;
import com.cd.dto.TestResponseDTO;
import com.cd.dto.TestUpdateDTO;

public interface TestService {

    TestResponseDTO create(TestCreateDTO dto);

    void deleteById(Integer id);

    TestResponseDTO update(Integer id, TestUpdateDTO dto);

    TestResponseDTO getById(Integer id);

    PageResult<TestResponseDTO> list(int page, int size);
}
