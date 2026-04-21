package com.alsystem.casemanager.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.alsystem.casemanager.entity.AiChatSession;
import org.apache.ibatis.annotations.Mapper;

/**
 * AI 会话数据访问层。
 */
@Mapper
public interface AiChatSessionMapper extends BaseMapper<AiChatSession> {}
