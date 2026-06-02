package com.cd.service.impl;

import com.cd.common.PageResult;
import com.cd.common.exception.ResourceNotFoundException;
import com.cd.dto.TestCreateDTO;
import com.cd.dto.TestResponseDTO;
import com.cd.dto.TestUpdateDTO;
import com.cd.entity.TestEntity;
import com.cd.mapper.TestMapper;
import com.cd.service.TestService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class TestServiceImpl implements TestService {

    private final TestMapper testMapper;

    @Override
    public TestResponseDTO create(TestCreateDTO dto) {
        TestEntity entity = new TestEntity();
        entity.setName(dto.getName());
        entity.setStatus(dto.getStatus() == null ? 0 : dto.getStatus());
        testMapper.insert(entity);
        return toResponse(testMapper.selectById(entity.getId()));
    }

    @Override
    public void deleteById(Integer id) {
        ensureExists(id);
        testMapper.deleteById(id);
    }

    @Override
    public TestResponseDTO update(Integer id, TestUpdateDTO dto) {
        TestEntity existing = ensureExists(id);
        existing.setName(dto.getName());
        existing.setStatus(dto.getStatus());
        testMapper.updateById(existing);
        return toResponse(testMapper.selectById(id));
    }

    @Override
    public TestResponseDTO getById(Integer id) {
        return toResponse(ensureExists(id));
    }

    @Override
    public PageResult<TestResponseDTO> list(int page, int size) {
        int offset = (page - 1) * size;
        long total = testMapper.countAll();
        List<TestResponseDTO> list = testMapper.selectPage(offset, size)
                .stream()
                .map(this::toResponse)
                .toList();
        return new PageResult<>(total, list);
    }

    private TestEntity ensureExists(Integer id) {
        TestEntity entity = testMapper.selectById(id);
        if (entity == null) {
            throw new ResourceNotFoundException("Record not found: id=" + id);
        }
        return entity;
    }

    private TestResponseDTO toResponse(TestEntity entity) {
        TestResponseDTO dto = new TestResponseDTO();
        dto.setId(entity.getId());
        dto.setName(entity.getName());
        dto.setStatus(entity.getStatus());
        dto.setCreatedAt(entity.getCreatedAt());
        return dto;
    }
}
