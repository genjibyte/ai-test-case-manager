package com.alsystem.casemanager.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.alsystem.casemanager.entity.TestCase;
import org.apache.ibatis.annotations.Mapper;

/**
 * 用例数据访问层。
 */
@Mapper
public interface TestCaseMapper extends BaseMapper<TestCase> {}
