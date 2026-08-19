package com.blocki.blocki_backend.auth.dto;

import java.util.UUID;

import com.blocki.blocki_backend.user.entity.User;

public record UserSummary(
		UUID id,
		String name,
		String email
) {
	public static UserSummary from(User user) {
		return new UserSummary(user.getId(), user.getName(), user.getEmail());
	}
}
