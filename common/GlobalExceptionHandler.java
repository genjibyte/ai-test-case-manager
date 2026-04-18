package com.alsystem.casemanager.common;

import com.alsystem.casemanager.aspect.OperationAuditLogAspect;
import com.alsystem.casemanager.entity.SysOperationLog;
import com.alsystem.casemanager.entity.SysUser;
import com.alsystem.casemanager.mapper.SysOperationLogMapper;
import com.alsystem.casemanager.util.SecurityUtil;
import java.time.LocalDateTime;
import javax.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.BindException;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
/**
 * 全局异常处理器：统一转换为 Result 错误响应。
 */
public class GlobalExceptionHandler {
  private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);
  private final SysOperationLogMapper operationLogMapper;
  private final SecurityUtil securityUtil;

  public GlobalExceptionHandler(SysOperationLogMapper operationLogMapper, SecurityUtil securityUtil) {
    this.operationLogMapper = operationLogMapper;
    this.securityUtil = securityUtil;
  }

  @ExceptionHandler({MethodArgumentNotValidException.class, BindException.class})
  /**
   * 参数校验异常处理。
   */
  public Result<Void> valid(Exception ex, HttpServletRequest request) {
    String msg = "参数错误";
    // 优先返回字段级错误消息，提升前端提示友好度。
    if (ex instanceof MethodArgumentNotValidException) {
      MethodArgumentNotValidException e = (MethodArgumentNotValidException) ex;
      if (e.getBindingResult().getFieldError() != null) {
        msg = e.getBindingResult().getFieldError().getDefaultMessage();
      }
    } else if (ex instanceof BindException) {
      BindException e = (BindException) ex;
      if (e.getBindingResult().getFieldError() != null) {
        msg = e.getBindingResult().getFieldError().getDefaultMessage();
      }
    }
    writeFailLogIfNeeded(request, msg);
    return Result.fail(400, msg);
  }

  @ExceptionHandler(IllegalArgumentException.class)
  /**
   * 业务参数异常处理。
   */
  public Result<Void> illegalArg(IllegalArgumentException ex, HttpServletRequest request) {
    writeFailLogIfNeeded(request, ex.getMessage());
    return Result.fail(400, ex.getMessage());
  }

  @ExceptionHandler(AccessDeniedException.class)
  /**
   * 权限不足异常处理。
   */
  public Result<Void> forbidden(HttpServletRequest request) {
    writeFailLogIfNeeded(request, "权限不足");
    return Result.fail(403, "权限不足");
  }

  @ExceptionHandler(Exception.class)
  /**
   * 兜底异常处理。
   */
  public Result<Void> other(Exception ex, HttpServletRequest request) {
    writeFailLogIfNeeded(request, ex.getMessage());
    return Result.fail(500, "服务器内部错误，请稍后重试");
  }

  private void writeFailLogIfNeeded(HttpServletRequest request, String errorMessage) {
    try {
      if (request == null) {
        return;
      }
      Object written = request.getAttribute(OperationAuditLogAspect.REQUEST_LOG_WRITTEN_ATTR);
      if (Boolean.TRUE.equals(written)) {
        return;
      }
      String path = request.getRequestURI();
      if (!isTrackedPath(path)) {
        return;
      }
      SysOperationLog log = new SysOperationLog();
      SysUser me = null;
      try {
        me = securityUtil.currentUser();
      } catch (Exception ignored) {
      }
      if (me != null) {
        log.setOperatorId(me.getUserId());
        log.setOperatorName(
            StringUtils.hasText(me.getRealName()) ? me.getRealName() : me.getUsername());
      }
      log.setOperationTime(LocalDateTime.now());
      log.setApiPath(path);
      log.setHttpMethod(request.getMethod());
      log.setStatus("FAIL");
      log.setErrorMessage(shorten(errorMessage, 500));
      fillMetaByPath(log, path);
      operationLogMapper.insert(log);
      request.setAttribute(OperationAuditLogAspect.REQUEST_LOG_WRITTEN_ATTR, Boolean.TRUE);
    } catch (Exception e) {
      log.warn("异常兜底日志写入失败: {}", e.getMessage(), e);
    }
  }

  private boolean isTrackedPath(String path) {
    return "/api/project/create".equals(path)
        || "/api/project/update".equals(path)
        || path != null && path.startsWith("/api/project/delete/")
        || "/api/case/update".equals(path)
        || "/api/case/upload/xmind".equals(path)
        || "/api/case/ai/chat".equals(path)
        || "/api/case/ai/chat/stream".equals(path);
  }

  private void fillMetaByPath(SysOperationLog log, String path) {
    if ("/api/project/create".equals(path)) {
      log.setOperationType("新增");
      log.setApiPurpose("创建测试项目");
    } else if ("/api/project/update".equals(path)) {
      log.setOperationType("修改");
      log.setApiPurpose("修改项目基础信息");
    } else if (path != null && path.startsWith("/api/project/delete/")) {
      log.setOperationType("删除");
      log.setApiPurpose("删除指定项目");
    } else if ("/api/case/update".equals(path)) {
      log.setOperationType("修改");
      log.setApiPurpose("编辑项目文件内容");
    } else if ("/api/case/upload/xmind".equals(path)) {
      log.setOperationType("上传");
      log.setApiPurpose("上传XMind用例并落库");
    } else if ("/api/case/ai/chat".equals(path)) {
      log.setOperationType("AI生成");
      log.setApiPurpose("工作流生成测试用例");
    } else if ("/api/case/ai/chat/stream".equals(path)) {
      log.setOperationType("AI生成");
      log.setApiPurpose("Agent修改并生成测试用例");
    }
  }

  private String shorten(String value, int maxLen) {
    if (!StringUtils.hasText(value)) {
      return null;
    }
    String v = value.trim();
    return v.length() <= maxLen ? v : v.substring(0, maxLen);
  }
}
