package com.alsystem.casemanager.vo;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
/**
 * XMind 上传接口的响应对象。
 *
 * <p>该 VO 的目标是让前端在“上传后”立即获得：
 * - OSS 上的可访问 URL（用于下载/预览源文件）
 * - 落库后的用例标识（caseId/caseCode/caseName），用于刷新目录树/跳转详情
 * - 上传目标（projectId/folderId/folderPath），用于在页面展示用户选择结果
 *
 * <p>注意：
 * - 当前系统用例主表 `test_case` 没有专门的 xmindUrl 字段，因此源文件 URL 也会被写入
 *   `mindMapJson` 的扩展字段（source/ossUrl）中，便于后续追溯。
 */
public class XmindUploadVO {
  /** 上传成功后 OSS 的公网/可访问 URL。 */
  private String url;
  /** 归属项目 ID（字符串形式，便于与前端类型保持一致）。 */
  private String projectId;
  /** 归属文件夹 ID（可空；为空表示根目录）。 */
  private String folderId;
  /** 归属文件夹路径（以 / 开头；根目录为 "/"）。 */
  private String folderPath;
  /** 用户本地上传的原始文件名（包含扩展名）。 */
  private String originalFileName;
  /** 用例落库后的主键 ID。 */
  private String caseId;
  /** 用例编号（项目内唯一）。 */
  private String caseCode;
  /** 用例名称（通常取上传文件名去掉 .xmind）。 */
  private String caseName;
}

