package com.blocki.blocki_backend.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record SignUpRequest(

		@NotBlank
		@Size(min = 8, max = 72, message = "password는 8~72자여야 합니다.")
		String password,

		@NotBlank
		@Size(min = 1, max = 50, message = "name은 1~50자여야 합니다.")
		String name,

		@NotBlank
		@Email(message = "유효한 이메일 형식이 아닙니다.")
		@Size(max = 254, message = "email은 최대 254자여야 합니다.")
		String email
) {
	public SignUpRequest {
		name = name == null ? null : name.trim();
	}
}
