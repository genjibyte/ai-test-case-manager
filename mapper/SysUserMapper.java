package com.alsystem.casemanager.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.alsystem.casemanager.entity.SysUser;
import org.apache.ibatis.annotations.Mapper;

/**
 * 系统用户数据访问层。
 */
@Mapper
public interface SysUserMapper extends BaseMapper<SysUser> {}
