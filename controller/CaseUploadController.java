package com.alsystem.casemanager.controller;

import com.alsystem.casemanager.annotation.OperationAuditLog;
import com.alsystem.casemanager.common.Result;
import com.alsystem.casemanager.entity.TestCase;
import com.alsystem.casemanager.entity.TestFolder;
import com.alsystem.casemanager.mapper.TestCaseMapper;
import com.alsystem.casemanager.mapper.TestFolderMapper;
import com.alsystem.casemanager.service.OssService;
import com.alsystem.casemanager.service.ProjectService;
import com.alsystem.casemanager.util.SecurityUtil;
import com.alsystem.casemanager.util.XmindParser;
import com.alsystem.casemanager.vo.XmindUploadVO;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import java.util.UUID;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.util.StringUtils;

@RestController
@RequestMapping("/api/case")
public class CaseUploadController {

  /** OSS 上传服务：负责把源文件写入阿里云 OSS 并返回可访问 URL。 */
  private final OssService ossService;
  /** 项目服务：用于项目存在性与权限校验（无权限/不存在会抛异常）。 */
  private final ProjectService projectService;
  /** 文件夹 Mapper：用于校验 folderId 是否存在且归属当前项目，并构建 folderPath。 */
  private final TestFolderMapper folderMapper;
  /** 用例 Mapper：用于插入用例记录与 caseCode 唯一性校验。 */
  private final TestCaseMapper caseMapper;
  /** 安全工具：用于获取当前登录用户 ID（作为 create_by）。 */
  private final SecurityUtil securityUtil;
  /** XMind 解析器：负责把 .xmind 的 content.json 转成 MindElixir 可渲染的 nodeData 结构。 */
  private final XmindParser xmindParser;

  /**
   * 构造函数：注入本控制器所需依赖。
   *
   * <p>注意：该类主要做“上传编排”（校验 → 解析 → OSS → 落库 → 响应），不把复杂解析逻辑塞在控制器中，
   * 因此解析独立放在 {@link XmindParser}。
   */
  public CaseUploadController(
      OssService ossService,
      ProjectService projectService,
      TestFolderMapper folderMapper,
      TestCaseMapper caseMapper,
      SecurityUtil securityUtil,
      XmindParser xmindParser) {
    this.ossService = ossService;
    this.projectService = projectService;
    this.folderMapper = folderMapper;
    this.caseMapper = caseMapper;
    this.securityUtil = securityUtil;
    this.xmindParser = xmindParser;
  }


  // todo 重构接口——新建一个Service 处理业务逻辑
  /**
   * XMind 上传并落库接口（仅支持 .xmind）。
   *
   * <p>职责拆解：
   * - 参数校验：file、projectId、folderId
   * - 权限校验：projectService.detail(projectId)
   * - 目录校验：folderId 存在且属于该项目；同时构建 folderPath
   * - 内容解析：解析 content.json → MindElixir nodeData
   * - OSS 上传：写入阿里云 OSS 并返回 URL
   * - DB 落库：插入 test_case（mind_map_json 存解析结果，并附带 ossUrl/source 方便追溯）
   *
   * <p>事务策略：
   * - DB 插入失败会回滚事务
   * - OSS 上传属于外部副作用，无法随 DB 回滚自动撤销；若需要严格一致性可进一步补偿（失败时删除 OSS 对象）。
   */
  @PostMapping("/upload/xmind")
  @Transactional(rollbackFor = Exception.class)
  @OperationAuditLog(operationType = "上传", apiPurpose = "上传XMind用例并落库")
  public Result<XmindUploadVO> uploadXmind(
      @RequestParam("file") MultipartFile file,
      @RequestParam("projectId") String projectId,
      @RequestParam(value = "folderId", required = false) String folderId) {
    // ========= 1) 基础参数校验 =========
    // 校验：是否上传了文件
    if (file == null || file.isEmpty()) {
      throw new IllegalArgumentException("文件为空");
    }
    // 调用：读取原始文件名（用于后缀校验/用例命名）
    String original = file.getOriginalFilename();
    // 校验：仅允许 .xmind，避免上传任意文件导致解析失败或安全风险
    if (!StringUtils.hasText(original) || !original.toLowerCase().endsWith(".xmind")) {
      throw new IllegalArgumentException("仅支持 .xmind 文件上传");
    }

    // ========= 2) 项目校验（存在性 + 权限） =========
    // 调用：将字符串 projectId 转为 long（与数据库类型一致）
    long pid = Long.parseLong(projectId.trim());
    // 调用：项目详情查询（在现有代码中同时承担“项目存在性 + 当前用户是否有访问权限”的校验）
    projectService.detail(pid);

    // ========= 3) 文件夹校验 + 构建 folderPath =========
    Long fid = null;
    String folderPath = "/";
    // 校验：如果传了 folderId，则要求其属于当前项目
    if (StringUtils.hasText(folderId)) {
      // 调用：去掉前端 Tree 的前缀（兼容 f-xxx）
      String raw = folderId.trim().replaceFirst("^f-", "");
      // 调用：字符串转 long
      fid = Long.parseLong(raw);
      // 调用：按主键查询文件夹
      TestFolder folder = folderMapper.selectById(fid);
      // 校验：文件夹必须存在，且 project_id 必须一致（防止越权写入别的项目目录）
      if (folder == null || !folder.getProjectId().equals(pid)) {
        throw new IllegalArgumentException("文件夹不存在");
      }
      // 调用：递归构建完整路径（用于 test_case.folder_path 与 OSS 归档前缀）
      folderPath = buildFolderFullPath(folder);
    }

    // ========= 4) 解析 XMind -> MindElixir 数据 =========
    // 调用：根据文件名推导用例名称（通常去掉扩展名）
    String caseName = deriveCaseName(original);
    // 先解析 XMind 内容为 MindElixir 数据结构（用于前端预览/编辑）
    // 调用：解析 .xmind 内 content.json，输出 nodeData（MindElixir 可直接渲染）
    ObjectNode mindElixirData = xmindParser.parseToMindElixirData(file, caseName);

    // ========= 5) 上传 OSS =========
    // 再上传 OSS（按项目/文件夹归档）
    // 计算：OSS 归档路径前缀（由 OssService 统一拼接 object-prefix/date/uuid 等）
    String keyPrefix =
        "project/" + pid + "/folder" + ("/".equals(folderPath) ? "" : folderPath);
    // 调用：上传 OSS，得到可访问 URL
    String url = ossService.upload(file, keyPrefix);

    // ========= 6) 写入数据库 test_case =========
    // 写入用例表
    // 调用：生成项目内唯一的 caseCode
    String caseCode = generateUniqueCaseCode(pid);
    // 调用：获取当前登录用户 ID，用于写入 create_by
    Long uid = securityUtil.currentUserId();

    // mind_map_json 扩展字段：保留来源与 ossUrl，同时包含 nodeData（MindElixir 可直接渲染）
    // 调用：写入来源标记（方便后续统计/追溯）
    mindElixirData.put("source", "xmind");
    // 调用：写入 OSS URL（方便后续下载源文件/重新解析）
    mindElixirData.put("ossUrl", url);

    // 组装：test_case 实体（字段含义详见 schema.sql）
    TestCase c = new TestCase();
    // 调用：设置 project_id
    c.setProjectId(pid);
    // 调用：设置项目内唯一 case_code
    c.setCaseCode(caseCode);
    // 调用：设置 case_name（通常与根节点 topic 同步）
    c.setCaseName(caseName);
    // 调用：设置 case_type（当前系统默认 1：功能）
    c.setCaseType(1);
    // 调用：默认等级
    c.setCaseLevel(3);
    // 调用：默认状态（1：草稿）
    c.setCaseStatus(1);
    // 调用：写入目录路径与 folder_id（用于树形展示与筛选）
    c.setFolderPath(folderPath);
    c.setFolderId(fid);
    // 调用：把解析后的 MindElixir JSON 存入 mind_map_json（前端可直接打开）
    c.setMindMapJson(mindElixirData.toString());
    // 调用：设置创建人
    c.setCreateBy(uid);
    // 调用：插入数据库（MyBatis-Plus 会回填主键 case_id）
    caseMapper.insert(c);

    // ========= 7) 构造响应 =========
    XmindUploadVO vo =
        new XmindUploadVO(
            url,
            String.valueOf(pid),
            fid == null ? null : String.valueOf(fid),
            folderPath,
            original,
            String.valueOf(c.getCaseId()),
            caseCode,
            caseName);
    // 调用：统一包装成 Result.ok 返回
    return Result.ok(vo);
  }

  /**
   * 从上传文件名推导用例名称。
   *
   * <p>策略：
   * - 去掉最后一个 '.' 之后的扩展名
   * - 压缩连续空白
   * - 若最终为空，则返回默认名称
   *
   * @param original 原始文件名（含扩展名）
   * @return 用例名称
   */
  private String deriveCaseName(String original) {
    // 调用：去掉首尾空白
    String name = original.trim();
    // 调用：定位最后一个 '.'，用于移除扩展名
    int idx = name.lastIndexOf('.');
    if (idx > 0) {
      // 调用：截取扩展名之前的部分
      name = name.substring(0, idx);
    }
    // 调用：把多个空白压缩成一个空格
    name = name.replaceAll("\\s+", " ").trim();
    // 返回：若为空则给固定兜底名称
    return name.isEmpty() ? "XMind用例" : name;
  }

  /**
   * 生成项目内唯一的 caseCode。
   *
   * <p>策略：
   * - 优先生成短随机码：XMIND-XXXXXXXX（8位十六进制风格）
   * - 若发生极小概率冲突（数据库已存在相同 code），则重试
   * - 多次失败后用时间戳兜底
   *
   * @param projectId 项目 ID（用于项目内唯一校验）
   * @return 项目内唯一 caseCode
   */
  private String generateUniqueCaseCode(long projectId) {
    // 最多尝试 5 次随机生成，平衡唯一性与性能
    for (int i = 0; i < 5; i++) {
      // 调用：生成 UUID，并截取 8 位作为短码（转大写便于展示）
      String code =
          "XMIND-"
              + UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase();
      // 调用：数据库计数校验（同一项目下 caseCode 必须唯一）
      Long cnt =
          caseMapper.selectCount(
              Wrappers.<TestCase>lambdaQuery()
                  .eq(TestCase::getProjectId, projectId)
                  .eq(TestCase::getCaseCode, code));
      // 返回：未命中则直接返回
      if (cnt == null || cnt == 0) {
        return code;
      }
    }
    // 兜底：极端情况下返回时间戳版本（保证几乎不可能重复）
    return "XMIND-" + System.currentTimeMillis();
  }

  /**
   * 递归构建文件夹完整路径（以 / 开头）。
   *
   * <p>示例：
   * - parent=null, name=模块A → /模块A
   * - parent=模块A, name=登录 → /模块A/登录
   *
   * @param f 当前文件夹实体
   * @return 完整路径
   */
  private String buildFolderFullPath(TestFolder f) {
    // 校验：到达根节点（parent_id=null）则路径为 /{name}
    if (f.getParentId() == null) {
      return "/" + f.getFolderName();
    }
    // 调用：查询父文件夹
    TestFolder p = folderMapper.selectById(f.getParentId());
    if (p == null) {
      // 兜底：父节点丢失时，退化为当前节点路径
      return "/" + f.getFolderName();
    }
    // 递归：父路径 + 当前节点
    return buildFolderFullPath(p) + "/" + f.getFolderName();
  }
}

