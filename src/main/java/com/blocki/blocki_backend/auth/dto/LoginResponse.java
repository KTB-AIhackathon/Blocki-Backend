package com.blocki.blocki_backend.auth.dto;

import java.time.Instant;

public record LoginResponse(
		String accessToken,
		String tokenType,
		Instant expiresAt,
		UserSummary user
) {
	public static LoginResponse of(String accessToken, Instant expiresAt, UserSummary user) {
		return new LoginResponse(accessToken, "Bearer", expiresAt, user);
	}
}
