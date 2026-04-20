package com.alsystem.casemanager.dto;

import java.util.ArrayList;
import java.util.List;
import javax.validation.Valid;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;
import lombok.Data;

@Data
/** AI 对话请求 DTO。 */
public class AiChatRequestDTO {

  @NotBlank(message = "项目ID不能为空")
  private String projectId;

  /** workflow：工作流网关；agent：Agent 网关 */
  @NotBlank(message = "模式不能为空")
  private String mode;

  @NotEmpty(message = "消息列表不能为空")
  @Valid
  private List<AiChatMessageDTO> messages = new ArrayList<>();

  /** 需求文档 Base64（不含 data: 前缀）；与 documentName 成对可选 */
  private String documentBase64;

  private String documentName;

  /** 工作流 parameters.input：文档 OSS 地址（与 Base64 二选一或并存，由 Coze 工作流节点决定） */
  private String documentUrl;

  /**
   * Agent 修改用例时携带：首次工作流全量结果的 JSON（含 reply 与 cases），写入 Coze parameters.workflow_context
   */
  private String workflowContextJson;

  /** 场景：1-功能 2-接口 3-性能 4-安全 */
  private List<Integer> sceneType = new ArrayList<>();

  @NotNull(message = "用例等级不能为空")
  private Integer caseLevel;

  /** 会话 ID（UUID）；不传则首轮自动创建，响应中带回 */
  private String sessionId;
}
