package com.cd.service;

import com.cd.common.PageResult;
import com.cd.dto.HostCreateDTO;
import com.cd.dto.HostResponseDTO;
import com.cd.dto.HostUpdateDTO;
import com.cd.entity.HostEntity;

public interface HostService {

    HostResponseDTO create(HostCreateDTO dto);

    HostResponseDTO update(Long id, HostUpdateDTO dto);

    void deleteById(Long id);

    HostResponseDTO getById(Long id);

    PageResult<HostResponseDTO> list(int page, int size, String keyword);

    /**
     * 供 RabbitMQ 监听器使用：按 MAC 唯一性做存在即更新、不存在即插入。
     */
    void saveOrUpdateFromMessage(HostEntity entity);
}
