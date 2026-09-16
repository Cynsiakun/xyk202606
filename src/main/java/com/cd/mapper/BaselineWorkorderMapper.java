package com.cd.mapper;

import com.cd.entity.BaselineWorkorderEntity;
import com.cd.dto.BaselineOperatorOptionDTO;
import com.cd.dto.BaselineWorkorderDetailDTO;
import com.cd.dto.BaselineWorkorderListItemDTO;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface BaselineWorkorderMapper {

    int insert(BaselineWorkorderEntity entity);

    List<BaselineWorkorderListItemDTO> selectPage(@Param("keyword") String keyword,
                                                  @Param("status") String status,
                                                  @Param("priority") String priority,
                                                  @Param("assigneeId") Long assigneeId,
                                                  @Param("tenantId") Long tenantId,
                                                  @Param("offset") int offset,
                                                  @Param("limit") int limit);

    long countPage(@Param("keyword") String keyword,
                   @Param("status") String status,
                   @Param("priority") String priority,
                   @Param("assigneeId") Long assigneeId,
                   @Param("tenantId") Long tenantId);

    BaselineWorkorderDetailDTO selectDetail(@Param("id") Long id,
                                            @Param("tenantId") Long tenantId);

    BaselineWorkorderEntity selectEntityById(@Param("id") Long id,
                                             @Param("tenantId") Long tenantId);

    int markProcessing(@Param("id") Long id,
                       @Param("tenantId") Long tenantId);

    int markDone(@Param("id") Long id,
                 @Param("closeRemark") String closeRemark,
                 @Param("tenantId") Long tenantId);

    boolean userHasRole(@Param("userId") Long userId,
                        @Param("roleCode") String roleCode,
                        @Param("tenantId") Long tenantId);

    List<BaselineOperatorOptionDTO> selectOperatorOptions(@Param("tenantId") Long tenantId);
}
