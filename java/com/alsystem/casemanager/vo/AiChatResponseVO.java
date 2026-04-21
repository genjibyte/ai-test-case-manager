package com.alsystem.casemanager.vo;

import java.util.ArrayList;
import java.util.List;
import lombok.Data;

@Data
/** AI 对话响应 VO。 */
public class AiChatResponseVO {

  private String reply;
  private List<AiGeneratedCaseVO> cases = new ArrayList<>();

  /** 落库后的会话 ID，前端后续请求携带以实现多轮与历史 */
  private String sessionId;
}
