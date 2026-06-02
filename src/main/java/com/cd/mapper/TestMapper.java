package com.cd.mapper;

import com.cd.entity.TestEntity;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface TestMapper {

    int insert(TestEntity entity);

    int deleteById(@Param("id") Integer id);

    int updateById(TestEntity entity);

    TestEntity selectById(@Param("id") Integer id);

    List<TestEntity> selectPage(@Param("offset") int offset, @Param("size") int size);

    long countAll();
}
