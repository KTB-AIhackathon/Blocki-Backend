package com.blocki.blocki_backend.common.response;

import java.util.List;
import java.util.UUID;

import com.blocki.blocki_backend.common.exception.ErrorCode;
import com.fasterxml.jackson.annotation.JsonInclude;

public record ErrorResponse(ErrorBody error) {

	public static ErrorResponse of(ErrorCode errorCode, String message) {
		return of(errorCode, message, null, null);
	}

	public static ErrorResponse of(
			ErrorCode errorCode,
			String message,
			List<FieldErrorDetail> fieldErrors,
			List<String> missingSources
	) {
		ErrorBody body = new ErrorBody(
				errorCode.name(),
				message,
				UUID.randomUUID().toString(),
				fieldErrors,
				missingSources
		);
		return new ErrorResponse(body);
	}

	@JsonInclude(JsonInclude.Include.NON_NULL)
	public record ErrorBody(
			String code,
			String message,
			String traceId,
			List<FieldErrorDetail> fieldErrors,
			List<String> missingSources
	) {
	}
}
