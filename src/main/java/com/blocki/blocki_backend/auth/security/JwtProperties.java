package com.blocki.blocki_backend.auth.security;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "jwt")
public record JwtProperties(String secret, long accessTokenExpirationSeconds) {

	public Duration accessTokenExpiration() {
		return Duration.ofSeconds(accessTokenExpirationSeconds);
	}
}
