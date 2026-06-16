package com.cd.mapper;

import com.cd.entity.TenantEntity;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface TenantMapper {

    int insert(TenantEntity entity);

    int updateById(TenantEntity entity);

    int updateStatusById(@Param("id") Long id, @Param("status") Integer status);

    int deleteById(@Param("id") Long id);

    TenantEntity selectById(@Param("id") Long id);

    List<TenantEntity> selectAll();

    List<TenantEntity> selectOptions(@Param("status") Integer status);

    List<TenantEntity> selectPage(@Param("offset") int offset,
                                  @Param("size") int size,
                                  @Param("keyword") String keyword,
                                  @Param("status") Integer status);

    List<TenantEntity> selectPageWithLicense(@Param("offset") int offset,
                                             @Param("size") int size,
                                             @Param("keyword") String keyword,
                                             @Param("status") Integer status);

    long countAll(@Param("keyword") String keyword, @Param("status") Integer status);
}
