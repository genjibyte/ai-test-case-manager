package com.alsystem.casemanager.aspect;

import com.alsystem.casemanager.annotation.OperationAuditLog;
import com.alsystem.casemanager.common.Result;
import com.alsystem.casemanager.dto.AiChatMessageDTO;
import com.alsystem.casemanager.dto.AiChatRequestDTO;
import com.alsystem.casemanager.dto.UpdateCaseDTO;
import com.alsystem.casemanager.dto.UpdateProjectDTO;
import com.alsystem.casemanager.entity.SysOperationLog;
import com.alsystem.casemanager.entity.SysUser;
import com.alsystem.casemanager.mapper.SysOperationLogMapper;
import com.alsystem.casemanager.util.SecurityUtil;
import com.alsystem.casemanager.vo.XmindUploadVO;
import java.net.URI;
import java.time.LocalDateTime;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Aspect
@Component
public class OperationAuditLogAspect {
  private static final Logger log = LoggerFactory.getLogger(OperationAuditLogAspect.class);
  public static final String REQUEST_LOG_WRITTEN_ATTR = "operation_log_written";

  private final SysOperationLogMapper operationLogMapper;
  private final SecurityUtil securityUtil;

  public OperationAuditLogAspect(SysOperationLogMapper operationLogMapper, SecurityUtil securityUtil) {
    this.operationLogMapper = operationLogMapper;
    this.securityUtil = securityUtil;
  }

  @Around("@annotation(com.alsystem.casemanager.annotation.OperationAuditLog)")
  public Object around(ProceedingJoinPoint joinPoint) throws Throwable {
    MethodSignature signature = (MethodSignature) joinPoint.getSignature();
    OperationAuditLog annotation = signature.getMethod().getAnnotation(OperationAuditLog.class);
    Object result = null;
    Throwable throwable = null;
    try {
      result = joinPoint.proceed();
      return result;
    } catch (Throwable ex) {
      throwable = ex;
      throw ex;
    } finally {
      saveLogSafely(annotation, joinPoint.getArgs(), result, throwable);
    }
  }

  private void saveLogSafely(
      OperationAuditLog annotation, Object[] args, Object result, Throwable throwable) {
    try {
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
      log.setOperationType(annotation.operationType());
      log.setApiPurpose(annotation.apiPurpose());
      log.setStatus(throwable == null ? "SUCCESS" : "FAIL");
      if (throwable != null) {
        String message =
            StringUtils.hasText(throwable.getMessage()) ? throwable.getMessage() : throwable.toString();
        log.setErrorMessage(shorten(message, 500));
      }

      ServletRequestAttributes attrs =
          (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
      if (attrs != null) {
        log.setApiPath(attrs.getRequest().getRequestURI());
        log.setHttpMethod(attrs.getRequest().getMethod());
      }

      fillBusinessFields(log, args, result);
      operationLogMapper.insert(log);
      if (attrs != null) {
        attrs.getRequest().setAttribute(REQUEST_LOG_WRITTEN_ATTR, Boolean.TRUE);
      }
    } catch (Exception e) {
      log.warn("保存操作日志失败: {}", e.getMessage(), e);
    }
  }

  private void fillBusinessFields(SysOperationLog log, Object[] args, Object result) {
    if (args == null) {
      return;
    }
    for (Object arg : args) {
      if (arg instanceof UpdateProjectDTO) {
        UpdateProjectDTO dto = (UpdateProjectDTO) arg;
        if (StringUtils.hasText(dto.getProjectId())) {
          log.setProjectId(parseLong(dto.getProjectId()));
        }
      } else if (arg instanceof UpdateCaseDTO) {
        UpdateCaseDTO dto = (UpdateCaseDTO) arg;
        if (StringUtils.hasText(dto.getCaseName())) {
          log.setFileName(shorten(dto.getCaseName(), 255));
        }
      } else if (arg instanceof AiChatRequestDTO) {
        AiChatRequestDTO dto = (AiChatRequestDTO) arg;
        if (StringUtils.hasText(dto.getProjectId())) {
          log.setProjectId(parseLong(dto.getProjectId()));
        }
        log.setCaseName(shorten(extractRequirementName(dto.getMessages()), 255));
      } else if (arg instanceof MultipartFile) {
        MultipartFile file = (MultipartFile) arg;
        log.setCaseName(shorten(file.getOriginalFilename(), 255));
      } else if (arg instanceof String) {
        String raw = ((String) arg).trim();
        if (raw.matches("^\\d+$") && log.getProjectId() == null) {
          log.setProjectId(parseLong(raw));
        }
      }
    }

    if (result instanceof Result) {
      Object data = ((Result<?>) result).getData();
      if (data instanceof XmindUploadVO) {
        XmindUploadVO vo = (XmindUploadVO) data;
        if (StringUtils.hasText(vo.getProjectId()) && log.getProjectId() == null) {
          log.setProjectId(parseLong(vo.getProjectId()));
        }
        if (StringUtils.hasText(vo.getCaseName())) {
          log.setCaseName(shorten(vo.getCaseName(), 255));
        }
        if (StringUtils.hasText(vo.getUrl())) {
          log.setUploadPath(shorten(extractObjectKey(vo.getUrl()), 500));
        }
      }
    } else if (result instanceof SseEmitter) {
      // chat/stream 已在参数中提取需求名称，这里无需额外字段处理。
    }
  }

  private String extractRequirementName(List<AiChatMessageDTO> messages) {
    if (messages == null || messages.isEmpty()) {
      return null;
    }
    for (AiChatMessageDTO message : messages) {
      if (message == null || !StringUtils.hasText(message.getContent())) {
        continue;
      }
      if ("user".equalsIgnoreCase(message.getRole())) {
        return message.getContent().trim();
      }
    }
    AiChatMessageDTO first = messages.get(0);
    return first == null ? null : first.getContent();
  }

  private Long parseLong(String value) {
    try {
      return Long.parseLong(value.trim());
    } catch (Exception e) {
      return null;
    }
  }

  private String extractObjectKey(String url) {
    try {
      URI uri = new URI(url);
      String path = uri.getPath();
      if (!StringUtils.hasText(path)) {
        return url;
      }
      return path.startsWith("/") ? path.substring(1) : path;
    } catch (Exception e) {
      return url;
    }
  }

  private String shorten(String value, int maxLen) {
    if (!StringUtils.hasText(value)) {
      return value;
    }
    String v = value.trim();
    return v.length() <= maxLen ? v : v.substring(0, maxLen);
  }
}

