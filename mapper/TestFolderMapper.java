package com.alsystem.casemanager.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.alsystem.casemanager.entity.TestFolder;
import org.apache.ibatis.annotations.Mapper;

/**
 * 文件夹数据访问层。
 */
@Mapper
public interface TestFolderMapper extends BaseMapper<TestFolder> {}
