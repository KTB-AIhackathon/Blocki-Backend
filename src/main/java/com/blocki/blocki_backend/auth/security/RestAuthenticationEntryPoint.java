package com.blocki.blocki_backend.auth.security;

import java.io.IOException;

import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import com.blocki.blocki_backend.common.exception.ErrorCode;
import com.blocki.blocki_backend.common.response.ErrorResponse;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import tools.jackson.databind.json.JsonMapper;

// Spring Boot 4의 기본 JSON 스택은 Jackson 3이며, 컨테이너가 자동 구성하는 빈은
// com.fasterxml.jackson.databind.ObjectMapper(Jackson 2)가 아니라
// tools.jackson.databind.json.JsonMapper(Jackson 3)이다. writeValueAsString 등
// 주요 API는 동일하게 유지된다.
@Component
public class RestAuthenticationEntryPoint implements AuthenticationEntryPoint {

	private final JsonMapper jsonMapper;

	public RestAuthenticationEntryPoint(JsonMapper jsonMapper) {
		this.jsonMapper = jsonMapper;
	}

	@Override
	public void commence(
			HttpServletRequest request,
			HttpServletResponse response,
			AuthenticationException authException
	) throws IOException {
		ErrorCode errorCode = ErrorCode.UNAUTHENTICATED;
		response.setStatus(errorCode.getStatus().value());
		response.setContentType(MediaType.APPLICATION_JSON_VALUE);
		response.setCharacterEncoding("UTF-8");
		response.getWriter().write(
				jsonMapper.writeValueAsString(ErrorResponse.of(errorCode, errorCode.getDefaultMessage()))
		);
	}
}
