package com.blocki.blocki_backend.common.exception;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.blocki.blocki_backend.common.response.ErrorResponse;
import com.blocki.blocki_backend.common.response.FieldErrorDetail;

@RestControllerAdvice
public class GlobalExceptionHandler {

	private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

	@ExceptionHandler(BusinessException.class)
	public ResponseEntity<ErrorResponse> handleBusinessException(BusinessException e) {
		ErrorCode errorCode = e.getErrorCode();
		ErrorResponse body = ErrorResponse.of(errorCode, e.getMessage(), e.getFieldErrors(), null);
		return ResponseEntity.status(errorCode.getStatus()).body(body);
	}

	@ExceptionHandler(MethodArgumentNotValidException.class)
	public ResponseEntity<ErrorResponse> handleValidationException(MethodArgumentNotValidException e) {
		List<FieldErrorDetail> fieldErrors = e.getBindingResult().getFieldErrors().stream()
				.map(fieldError -> new FieldErrorDetail(fieldError.getField(), fieldError.getDefaultMessage()))
				.toList();

		ErrorCode errorCode = ErrorCode.INVALID_PARAMETER;
		ErrorResponse body = ErrorResponse.of(errorCode, errorCode.getDefaultMessage(), fieldErrors, null);
		return ResponseEntity.status(errorCode.getStatus()).body(body);
	}

	@ExceptionHandler(AccessDeniedException.class)
	public ResponseEntity<ErrorResponse> handleAccessDeniedException(AccessDeniedException e) {
		ErrorCode errorCode = ErrorCode.FORBIDDEN;
		return ResponseEntity.status(errorCode.getStatus())
				.body(ErrorResponse.of(errorCode, errorCode.getDefaultMessage()));
	}

	@ExceptionHandler(Exception.class)
	public ResponseEntity<ErrorResponse> handleUnexpectedException(Exception e) {
		log.error("Unhandled exception", e);
		ErrorCode errorCode = ErrorCode.INTERNAL_ERROR;
		return ResponseEntity.status(errorCode.getStatus())
				.body(ErrorResponse.of(errorCode, errorCode.getDefaultMessage()));
	}
}
