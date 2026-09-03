package com.sua.reqbridge.common.api;

import java.util.Comparator;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import org.springframework.web.multipart.MultipartException;

import com.sua.reqbridge.contract.ResourceNotFoundException;
import com.sua.reqbridge.contract.StateConflictException;

import jakarta.validation.ConstraintViolationException;

@RestControllerAdvice
public class GlobalApiExceptionHandler {

	private static final Logger log = LoggerFactory.getLogger(GlobalApiExceptionHandler.class);

	@ExceptionHandler(MultipartException.class)
	public ResponseEntity<ApiErrorResponse> handleMultipart(MultipartException exception) {
		return error(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR",
				"multipart 요청을 확인해주세요. PDF 한 개, 최대 10MB까지 허용합니다.", List.of());
	}

	@ExceptionHandler(ResourceNotFoundException.class)
	public ResponseEntity<ApiErrorResponse> handleNotFound(ResourceNotFoundException exception) {
		return error(HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND", exception.getMessage(), List.of());
	}

	@ExceptionHandler(StateConflictException.class)
	public ResponseEntity<ApiErrorResponse> handleStateConflict(StateConflictException exception) {
		String code = exception.getCode() == null ? "STATE_CONFLICT" : exception.getCode();
		return error(HttpStatus.CONFLICT, code, exception.getMessage(), List.of());
	}

	@ExceptionHandler(MethodArgumentNotValidException.class)
	public ResponseEntity<ApiErrorResponse> handleMethodArgumentNotValid(
			MethodArgumentNotValidException exception) {
		List<FieldErrorDetail> fieldErrors = exception.getBindingResult().getFieldErrors().stream()
				.sorted(Comparator.comparing(FieldError::getField))
				.map(fieldError -> new FieldErrorDetail(
						fieldError.getField(),
						fieldError.getDefaultMessage() == null
								? "Invalid value"
								: fieldError.getDefaultMessage()))
				.toList();
		return error(
				HttpStatus.BAD_REQUEST,
				"VALIDATION_ERROR",
				"요청 값을 확인해주세요.",
				fieldErrors);
	}

	@ExceptionHandler({
			IllegalArgumentException.class,
			HttpMessageNotReadableException.class
	})
	public ResponseEntity<ApiErrorResponse> handleBadRequest(Exception exception) {
		String message = exception instanceof HttpMessageNotReadableException
				? "요청 JSON 형식 또는 값이 올바르지 않습니다."
				: exception.getMessage();
		return error(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", message, List.of());
	}

	@ExceptionHandler(ConstraintViolationException.class)
	public ResponseEntity<ApiErrorResponse> handleConstraintViolation(
			ConstraintViolationException exception) {
		List<FieldErrorDetail> fieldErrors = exception.getConstraintViolations().stream()
				.map(violation -> new FieldErrorDetail(
						lastPathSegment(violation.getPropertyPath().toString()),
						violation.getMessage()))
				.sorted(Comparator.comparing(FieldErrorDetail::field))
				.toList();
		return error(
				HttpStatus.BAD_REQUEST,
				"VALIDATION_ERROR",
				"요청 값을 확인해주세요.",
				fieldErrors);
	}

	@ExceptionHandler(MethodArgumentTypeMismatchException.class)
	public ResponseEntity<ApiErrorResponse> handleTypeMismatch(
			MethodArgumentTypeMismatchException exception) {
		return error(
				HttpStatus.BAD_REQUEST,
				"VALIDATION_ERROR",
				"요청 값을 확인해주세요.",
				List.of(new FieldErrorDetail(exception.getName(), "올바른 형식의 값을 입력해주세요.")));
	}

	@ExceptionHandler(DataIntegrityViolationException.class)
	public ResponseEntity<ApiErrorResponse> handleDataIntegrityViolation(
			DataIntegrityViolationException exception) {
		log.warn("Database integrity constraint rejected an API operation", exception);
		return error(
				HttpStatus.CONFLICT,
				"STATE_CONFLICT",
				"현재 데이터 상태에서는 요청을 처리할 수 없습니다.",
				List.of());
	}

	@ExceptionHandler(NoResourceFoundException.class)
	public ResponseEntity<ApiErrorResponse> handleNoResourceFound(NoResourceFoundException exception) {
		return error(
				HttpStatus.NOT_FOUND,
				"RESOURCE_NOT_FOUND",
				"요청한 API를 찾을 수 없습니다.",
				List.of());
	}

	@ExceptionHandler(HttpRequestMethodNotSupportedException.class)
	public ResponseEntity<ApiErrorResponse> handleMethodNotSupported(
			HttpRequestMethodNotSupportedException exception) {
		return error(
				HttpStatus.METHOD_NOT_ALLOWED,
				"METHOD_NOT_ALLOWED",
				"지원하지 않는 HTTP 메서드입니다.",
				List.of());
	}

	@ExceptionHandler(HttpMediaTypeNotSupportedException.class)
	public ResponseEntity<ApiErrorResponse> handleMediaTypeNotSupported(
			HttpMediaTypeNotSupportedException exception) {
		return error(
				HttpStatus.UNSUPPORTED_MEDIA_TYPE,
				"UNSUPPORTED_MEDIA_TYPE",
				"지원하지 않는 Content-Type입니다.",
				List.of());
	}

	@ExceptionHandler(Exception.class)
	public ResponseEntity<ApiErrorResponse> handleUnexpected(Exception exception) {
		log.error("Unexpected API error", exception);
		return error(
				HttpStatus.INTERNAL_SERVER_ERROR,
				"INTERNAL_ERROR",
				"요청 처리 중 오류가 발생했습니다.",
				List.of());
	}

	private String lastPathSegment(String path) {
		int lastDot = path.lastIndexOf('.');
		return lastDot < 0 ? path : path.substring(lastDot + 1);
	}

	private ResponseEntity<ApiErrorResponse> error(
			HttpStatus status,
			String code,
			String message,
			List<FieldErrorDetail> fieldErrors) {
		return ResponseEntity.status(status)
				.body(new ApiErrorResponse(new ErrorDetail(code, message, fieldErrors)));
	}
}
