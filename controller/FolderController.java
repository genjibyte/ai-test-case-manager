package com.alsystem.casemanager.controller;

import com.alsystem.casemanager.common.Result;
import com.alsystem.casemanager.dto.CreateFolderDTO;
import com.alsystem.casemanager.dto.RenameFolderDTO;
import com.alsystem.casemanager.service.FolderService;
import com.alsystem.casemanager.vo.TreeNodeVO;
import java.util.List;
import javax.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/folder")
public class FolderController {

  private final FolderService folderService;

  /**
   * 构造函数：注入文件夹服务。
   */
  public FolderController(FolderService folderService) {
    this.folderService = folderService;
  }

  /**
   * 获取项目下目录树（包含文件夹与用例文件节点）。
   */
  @GetMapping("/tree")
  public Result<List<TreeNodeVO>> tree(@RequestParam String projectId) {
    // 将 projectId 转换为 long，并由服务层负责项目访问校验。
    return Result.ok(folderService.tree(Long.parseLong(projectId.trim())));
  }

  /**
   * 创建文件夹。
   */
  @PostMapping("/create")
  public Result<String> create(@Valid @RequestBody CreateFolderDTO dto) {
    // 返回新建文件夹 ID，供前端刷新树结构定位。
    Long id = folderService.create(dto);
    return Result.ok(String.valueOf(id));
  }

  /**
   * 重命名文件夹。
   */
  @PutMapping("/{folderId}")
  public Result<Void> rename(@PathVariable String folderId, @Valid @RequestBody RenameFolderDTO dto) {
    // 将路径 ID 交给服务层做重名校验后更新。
    folderService.rename(Long.parseLong(folderId.trim()), dto);
    return Result.ok(null);
  }

  /**
   * 删除文件夹。
   */
  @DeleteMapping("/{folderId}")
  public Result<Void> delete(@PathVariable String folderId) {
    // 删除前会在服务层校验子目录与目录下用例是否为空。
    folderService.delete(Long.parseLong(folderId.trim()));
    return Result.ok(null);
  }
}
