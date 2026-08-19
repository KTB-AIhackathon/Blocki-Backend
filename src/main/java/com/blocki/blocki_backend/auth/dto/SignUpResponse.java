package com.blocki.blocki_backend.auth.dto;

import java.time.Instant;
import java.util.UUID;

import com.blocki.blocki_backend.user.entity.User;

public record SignUpResponse(
		UUID id,
		String name,
		String email,
		Instant createdAt
) {
	public static SignUpResponse from(User user) {
		return new SignUpResponse(
				user.getId(),
				user.getName(),
				user.getEmail(),
				user.getCreatedAt()
		);
	}
}
