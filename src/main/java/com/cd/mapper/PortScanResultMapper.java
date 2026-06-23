package com.cd.mapper;

import com.cd.entity.PortScanResultEntity;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface PortScanResultMapper {

    int insert(PortScanResultEntity entity);

    PortScanResultEntity selectById(@Param("id") Long id);

    PortScanResultEntity selectByIdAndTenant(@Param("id") Long id, @Param("tenantId") Long tenantId);

    List<PortScanResultEntity> selectPage(@Param("offset") int offset,
                                          @Param("size") int size,
                                          @Param("keyword") String keyword,
                                          @Param("macAddress") String macAddress);

    List<PortScanResultEntity> selectPageByTenant(@Param("offset") int offset,
                                                  @Param("size") int size,
                                                  @Param("keyword") String keyword,
                                                  @Param("macAddress") String macAddress,
                                                  @Param("tenantId") Long tenantId);

    long countFiltered(@Param("keyword") String keyword,
                       @Param("macAddress") String macAddress);

    long countFilteredByTenant(@Param("keyword") String keyword,
                               @Param("macAddress") String macAddress,
                               @Param("tenantId") Long tenantId);

    int softDeleteById(@Param("id") Long id);

    int softDeleteByIdAndTenant(@Param("id") Long id, @Param("tenantId") Long tenantId);

    List<PortScanResultEntity> selectLatestPerMac();

    List<PortScanResultEntity> selectLatestPerMacByTenant(@Param("tenantId") Long tenantId);

    PortScanResultEntity selectLatestByMac(@Param("macAddress") String macAddress);

    PortScanResultEntity selectLatestByMacAndTenant(@Param("macAddress") String macAddress,
                                                     @Param("tenantId") Long tenantId);

    PortScanResultEntity selectLatestByTaskIdAndTenant(@Param("taskId") String taskId,
                                                       @Param("tenantId") Long tenantId);
}
