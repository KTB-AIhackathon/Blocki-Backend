package com.blocki.blocki_backend.auth.security;

import java.io.IOException;

import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import com.blocki.blocki_backend.common.exception.ErrorCode;
import com.blocki.blocki_backend.common.response.ErrorResponse;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import tools.jackson.databind.json.JsonMapper;

// RestAuthenticationEntryPoint와 동일한 이유로 Jackson 3의 JsonMapper를 사용한다.
@Component
public class RestAccessDeniedHandler implements AccessDeniedHandler {

	private final JsonMapper jsonMapper;

	public RestAccessDeniedHandler(JsonMapper jsonMapper) {
		this.jsonMapper = jsonMapper;
	}

	@Override
	public void handle(
			HttpServletRequest request,
			HttpServletResponse response,
			AccessDeniedException accessDeniedException
	) throws IOException {
		ErrorCode errorCode = ErrorCode.FORBIDDEN;
		response.setStatus(errorCode.getStatus().value());
		response.setContentType(MediaType.APPLICATION_JSON_VALUE);
		response.setCharacterEncoding("UTF-8");
		response.getWriter().write(
				jsonMapper.writeValueAsString(ErrorResponse.of(errorCode, errorCode.getDefaultMessage()))
		);
	}
}
