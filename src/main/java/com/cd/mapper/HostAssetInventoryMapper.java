package com.cd.mapper;

import com.cd.entity.HostAssetInventoryEntity;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface HostAssetInventoryMapper {

    int deleteByHostIdAndTaskId(@Param("hostId") Long hostId, @Param("taskId") String taskId);

    int insertBatch(@Param("items") List<HostAssetInventoryEntity> items);

    List<HostAssetInventoryEntity> selectTaskPageByTenant(@Param("offset") int offset,
                                                          @Param("size") int size,
                                                          @Param("keyword") String keyword,
                                                          @Param("macAddress") String macAddress,
                                                          @Param("tenantId") Long tenantId);

    long countTaskFilteredByTenant(@Param("keyword") String keyword,
                                   @Param("macAddress") String macAddress,
                                   @Param("tenantId") Long tenantId);

    List<HostAssetInventoryEntity> selectByHostIdAndTaskIdAndTenant(@Param("hostId") Long hostId,
                                                                    @Param("taskId") String taskId,
                                                                    @Param("tenantId") Long tenantId);

    List<HostAssetInventoryEntity> selectPageByTenant(@Param("offset") int offset,
                                                      @Param("size") int size,
                                                      @Param("keyword") String keyword,
                                                      @Param("macAddress") String macAddress,
                                                      @Param("tenantId") Long tenantId);

    long countFilteredByTenant(@Param("keyword") String keyword,
                               @Param("macAddress") String macAddress,
                               @Param("tenantId") Long tenantId);

    HostAssetInventoryEntity selectByIdAndTenant(@Param("id") Long id, @Param("tenantId") Long tenantId);

    HostAssetInventoryEntity selectLatestByMacAndTenant(@Param("macAddress") String macAddress,
                                                        @Param("tenantId") Long tenantId);

    int deleteByIdAndTenant(@Param("id") Long id, @Param("tenantId") Long tenantId);

    HostAssetInventoryEntity selectTaskByIdAndTenant(@Param("id") Long id, @Param("tenantId") Long tenantId);
}
