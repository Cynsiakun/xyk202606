package com.cd.mapper;

import com.cd.entity.AgentResultOperationEntity;
import org.apache.ibatis.annotations.Param;

public interface AgentResultOperationMapper {

    int insertIgnore(AgentResultOperationEntity entity);

    AgentResultOperationEntity selectByOperationId(@Param("operationId") String operationId);
}
