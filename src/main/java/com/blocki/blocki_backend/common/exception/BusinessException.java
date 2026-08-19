package com.blocki.blocki_backend.common.exception;

import java.util.List;

import com.blocki.blocki_backend.common.response.FieldErrorDetail;

public class BusinessException extends RuntimeException {

	private final ErrorCode errorCode;
	private final List<FieldErrorDetail> fieldErrors;

	public BusinessException(ErrorCode errorCode) {
		this(errorCode, errorCode.getDefaultMessage(), null);
	}

	public BusinessException(ErrorCode errorCode, String message) {
		this(errorCode, message, null);
	}

	public BusinessException(ErrorCode errorCode, String message, List<FieldErrorDetail> fieldErrors) {
		super(message);
		this.errorCode = errorCode;
		this.fieldErrors = fieldErrors;
	}

	public ErrorCode getErrorCode() {
		return errorCode;
	}

	public List<FieldErrorDetail> getFieldErrors() {
		return fieldErrors;
	}
}
