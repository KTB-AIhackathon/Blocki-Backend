package com.blocki.blocki_backend.common.exception;

import org.springframework.http.HttpStatus;

public enum ErrorCode {

	INVALID_PARAMETER(HttpStatus.BAD_REQUEST, "요청 값이 올바르지 않습니다."),
	UNSUPPORTED_PROVIDER(HttpStatus.BAD_REQUEST, "지원하지 않는 provider입니다."),
	UNSUPPORTED_DOCUMENT_TYPE(HttpStatus.BAD_REQUEST, "지원하지 않는 문서 유형입니다."),
	OAUTH_STATE_INVALID(HttpStatus.BAD_REQUEST, "OAuth state가 유효하지 않습니다."),

	UNAUTHENTICATED(HttpStatus.UNAUTHORIZED, "인증이 필요하거나 만료되었습니다."),
	INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED, "이메일 또는 비밀번호가 올바르지 않습니다."),

	FORBIDDEN(HttpStatus.FORBIDDEN, "접근 권한이 없습니다."),
	METHOD_NOT_ALLOWED(HttpStatus.METHOD_NOT_ALLOWED, "허용되지 않은 HTTP 메서드입니다."),

	TODO_NOT_FOUND(HttpStatus.NOT_FOUND, "Todo를 찾을 수 없습니다."),
	REFLECTION_NOT_FOUND(HttpStatus.NOT_FOUND, "회고를 찾을 수 없습니다."),
	DOCUMENT_NOT_FOUND(HttpStatus.NOT_FOUND, "문서를 찾을 수 없습니다."),
	VERSION_NOT_FOUND(HttpStatus.NOT_FOUND, "문서 버전을 찾을 수 없습니다."),
	JOB_NOT_FOUND(HttpStatus.NOT_FOUND, "작업을 찾을 수 없습니다."),

	EMAIL_ALREADY_EXISTS(HttpStatus.CONFLICT, "이미 사용 중인 이메일입니다."),
	INTEGRATION_ALREADY_CONNECTED(HttpStatus.CONFLICT, "이미 연결된 제공자입니다."),
	GITHUB_INTEGRATION_REQUIRED(HttpStatus.CONFLICT, "GitHub 연동 후 자동 생성 기능을 켤 수 있습니다."),
	JOB_ALREADY_RUNNING(HttpStatus.CONFLICT, "이미 진행 중인 작업이 있습니다."),
	IDEMPOTENCY_KEY_REUSED(HttpStatus.CONFLICT, "같은 Idempotency-Key에 다른 요청 본문이 재사용되었습니다."),
	REFLECTION_ALREADY_PUBLISHED(HttpStatus.CONFLICT, "이미 발행된 회고입니다."),

	DATA_INSUFFICIENT(HttpStatus.UNPROCESSABLE_ENTITY, "생성·발행에 필요한 데이터가 부족합니다."),

	EXTERNAL_SOURCE_FAILED(HttpStatus.BAD_GATEWAY, "외부 제공자 호출에 실패했습니다."),
	AI_PIPELINE_FAILED(HttpStatus.BAD_GATEWAY, "AI 파이프라인 처리에 실패했습니다."),

	INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "처리하지 못한 서버 오류가 발생했습니다.");

	private final HttpStatus status;
	private final String defaultMessage;

	ErrorCode(HttpStatus status, String defaultMessage) {
		this.status = status;
		this.defaultMessage = defaultMessage;
	}

	public HttpStatus getStatus() {
		return status;
	}

	public String getDefaultMessage() {
		return defaultMessage;
	}
}
