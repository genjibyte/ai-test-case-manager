package com.alsystem.casemanager.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.alsystem.casemanager.entity.TestProjectUser;
import org.apache.ibatis.annotations.Mapper;

/**
 * 项目-用户关系数据访问层。
 */
@Mapper
public interface TestProjectUserMapper extends BaseMapper<TestProjectUser> {}
