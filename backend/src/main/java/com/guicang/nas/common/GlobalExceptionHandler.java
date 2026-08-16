package com.guicang.nas.common;

import jakarta.validation.ConstraintViolationException;
import java.util.Objects;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.MessageSourceResolvable;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * 全局异常处理：所有异常统一转为 {@link Result} 结构，错误信息不泄露内部细节。
 *
 * <p>分层约定：Controller 只负责参数校验与出参；业务异常抛 {@link BizException}，其余异常在本类兜底。
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

  private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);
  private static final String INTERNAL_ERROR_MESSAGE = "系统繁忙，请稍后重试";

  /** 业务异常：HTTP 200 + 业务码，由前端按 code 分支处理。 */
  @ExceptionHandler(BizException.class)
  public Result<Void> handleBizException(BizException e) {
    return Result.fail(e.getCode(), e.getMessage());
  }

  /** 请求体/表单校验失败（@Valid）。 */
  @ExceptionHandler(BindException.class)
  @ResponseStatus(HttpStatus.BAD_REQUEST)
  public Result<Void> handleBindException(BindException e) {
    String message =
        e.getBindingResult().getFieldErrors().stream()
            .map(this::formatFieldError)
            .collect(Collectors.joining("；"));
    return Result.fail(ResultCodes.BAD_REQUEST, message);
  }

  /** 方法级参数校验失败（@Validated）。 */
  @ExceptionHandler(ConstraintViolationException.class)
  @ResponseStatus(HttpStatus.BAD_REQUEST)
  public Result<Void> handleConstraintViolation(ConstraintViolationException e) {
    String message =
        e.getConstraintViolations().stream()
            .map(v -> v.getPropertyPath() + " " + v.getMessage())
            .collect(Collectors.joining("；"));
    return Result.fail(ResultCodes.BAD_REQUEST, message);
  }

  /** Spring 6.1 方法级校验（HandlerMethodValidationException）。 */
  @ExceptionHandler(HandlerMethodValidationException.class)
  @ResponseStatus(HttpStatus.BAD_REQUEST)
  public Result<Void> handleHandlerMethodValidation(HandlerMethodValidationException e) {
    String message =
        e.getAllValidationResults().stream()
            .flatMap(r -> r.getResolvableErrors().stream())
            .map(MessageSourceResolvable::getDefaultMessage)
            .filter(Objects::nonNull)
            .collect(Collectors.joining("；"));
    return Result.fail(ResultCodes.BAD_REQUEST, message.isBlank() ? "参数校验失败" : message);
  }

  /** 请求体不可读（JSON 解析失败等）。 */
  @ExceptionHandler(HttpMessageNotReadableException.class)
  @ResponseStatus(HttpStatus.BAD_REQUEST)
  public Result<Void> handleMessageNotReadable(HttpMessageNotReadableException e) {
    return Result.fail(ResultCodes.BAD_REQUEST, "请求体格式错误");
  }

  /** 缺少必填请求参数。 */
  @ExceptionHandler(MissingServletRequestParameterException.class)
  @ResponseStatus(HttpStatus.BAD_REQUEST)
  public Result<Void> handleMissingParameter(MissingServletRequestParameterException e) {
    return Result.fail(ResultCodes.BAD_REQUEST, "缺少请求参数: " + e.getParameterName());
  }

  /** 请求参数类型不匹配。 */
  @ExceptionHandler(MethodArgumentTypeMismatchException.class)
  @ResponseStatus(HttpStatus.BAD_REQUEST)
  public Result<Void> handleTypeMismatch(MethodArgumentTypeMismatchException e) {
    return Result.fail(ResultCodes.BAD_REQUEST, "参数类型不匹配: " + e.getName());
  }

  /** 请求方法不支持。 */
  @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
  @ResponseStatus(HttpStatus.METHOD_NOT_ALLOWED)
  public Result<Void> handleMethodNotSupported(HttpRequestMethodNotSupportedException e) {
    return Result.fail(ResultCodes.METHOD_NOT_ALLOWED, "请求方法不支持: " + e.getMethod());
  }

  /** 资源不存在。 */
  @ExceptionHandler(NoResourceFoundException.class)
  @ResponseStatus(HttpStatus.NOT_FOUND)
  public Result<Void> handleNoResourceFound(NoResourceFoundException e) {
    return Result.fail(ResultCodes.NOT_FOUND, "资源不存在");
  }

  /** 兜底：未预期异常，记录堆栈但不向客户端泄露细节。 */
  @ExceptionHandler(Exception.class)
  @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
  public Result<Void> handleUnexpected(Exception e) {
    log.error("未处理异常", e);
    return Result.fail(ResultCodes.INTERNAL_ERROR, INTERNAL_ERROR_MESSAGE);
  }

  private String formatFieldError(FieldError error) {
    return error.getField() + " " + error.getDefaultMessage();
  }
}
