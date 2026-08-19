package com.blocki.blocki_backend.auth.security;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

import javax.crypto.SecretKey;

import org.springframework.stereotype.Component;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

@Component
public class JwtTokenProvider {

	private static final String CLAIM_EMAIL = "email";

	// HS256 서명에 필요한 최소 키 길이(256비트 = 32바이트). jjwt Keys.hmacShaKeyFor는
	// 키 길이가 짧으면 WeakKeyException을 던지므로, 기동 시점에 더 명확한 메시지로 먼저 걸러낸다.
	private static final int MIN_SECRET_BYTES = 32;

	private final JwtProperties jwtProperties;
	private final SecretKey signingKey;

	public JwtTokenProvider(JwtProperties jwtProperties) {
		this.jwtProperties = jwtProperties;

		byte[] secretBytes = jwtProperties.secret() == null
				? new byte[0]
				: jwtProperties.secret().getBytes(StandardCharsets.UTF_8);

		if (secretBytes.length < MIN_SECRET_BYTES) {
			throw new IllegalStateException(
					"jwt.secret은 HS256 서명을 위해 최소 " + MIN_SECRET_BYTES + "바이트(256비트) 이상이어야 합니다. "
							+ "현재 길이: " + secretBytes.length + "바이트. JWT_SECRET 환경변수 값을 확인하세요."
			);
		}

		this.signingKey = Keys.hmacShaKeyFor(secretBytes);
	}

	public IssuedAccessToken issueAccessToken(UUID userId, String email) {
		Instant now = Instant.now();
		Instant expiresAt = now.plus(jwtProperties.accessTokenExpiration());

		String token = Jwts.builder()
				.subject(userId.toString())
				.claim(CLAIM_EMAIL, email)
				.issuedAt(Date.from(now))
				.expiration(Date.from(expiresAt))
				.signWith(signingKey, Jwts.SIG.HS256)
				.compact();

		return new IssuedAccessToken(token, expiresAt);
	}

	public AuthenticatedUser parse(String token) {
		try {
			Claims claims = Jwts.parser()
					.verifyWith(signingKey)
					.build()
					.parseSignedClaims(token)
					.getPayload();

			UUID userId = UUID.fromString(claims.getSubject());
			String email = claims.get(CLAIM_EMAIL, String.class);
			return new AuthenticatedUser(userId, email);
		} catch (JwtException | IllegalArgumentException e) {
			throw new InvalidAccessTokenException(e);
		}
	}

	public record IssuedAccessToken(String token, Instant expiresAt) {
	}

	public static class InvalidAccessTokenException extends RuntimeException {
		public InvalidAccessTokenException(Throwable cause) {
			super("유효하지 않은 access token입니다.", cause);
		}
	}
}
