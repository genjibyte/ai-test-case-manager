package com.alsystem.casemanager.controller;

import com.alsystem.casemanager.common.Result;
import com.alsystem.casemanager.service.OssService;
import com.alsystem.casemanager.vo.OssUploadVO;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/oss")
public class OssController {

  private final OssService ossService;

  /**
   * 构造函数：注入 OSS 服务。
   */
  public OssController(OssService ossService) {
    this.ossService = ossService;
  }

  /**
   * 上传文件到 OSS。
   */
  @PostMapping("/upload")
  public Result<OssUploadVO> upload(@RequestParam("file") MultipartFile file) {
    // 委托服务上传并返回可访问 URL。
    String url = ossService.upload(file);
    return Result.ok(new OssUploadVO(url));
  }

  /**
   * 按公网 URL 删除 OSS 对象。
   */
  @DeleteMapping("/delete")
  public Result<Void> delete(@RequestParam("url") String url) {
    // 服务层会做前缀安全校验，防止越权删库内无关对象。
    ossService.deleteByPublicUrl(url);
    return Result.<Void>ok(null);
  }
}
