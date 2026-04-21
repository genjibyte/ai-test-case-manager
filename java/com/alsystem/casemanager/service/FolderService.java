package com.alsystem.casemanager.service;

import com.alsystem.casemanager.dto.CreateFolderDTO;
import com.alsystem.casemanager.dto.RenameFolderDTO;
import com.alsystem.casemanager.vo.TreeNodeVO;
import java.util.List;

public interface FolderService {

  /**
   * 查询项目目录树。
   */
  List<TreeNodeVO> tree(Long projectId);

  /**
   * 创建文件夹。
   */
  Long create(CreateFolderDTO dto);

  /**
   * 重命名文件夹。
   */
  void rename(Long folderId, RenameFolderDTO dto);

  /**
   * 删除文件夹。
   */
  void delete(Long folderId);
}
