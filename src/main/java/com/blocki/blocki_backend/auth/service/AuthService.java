package com.blocki.blocki_backend.auth.service;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.blocki.blocki_backend.auth.dto.LoginRequest;
import com.blocki.blocki_backend.auth.dto.LoginResponse;
import com.blocki.blocki_backend.auth.dto.SignUpRequest;
import com.blocki.blocki_backend.auth.dto.SignUpResponse;
import com.blocki.blocki_backend.auth.dto.UserSummary;
import com.blocki.blocki_backend.auth.security.JwtTokenProvider;
import com.blocki.blocki_backend.common.exception.BusinessException;
import com.blocki.blocki_backend.common.exception.ErrorCode;
import com.blocki.blocki_backend.user.entity.User;
import com.blocki.blocki_backend.user.repository.UserRepository;

@Service
public class AuthService {

	private final UserRepository userRepository;
	private final PasswordEncoder passwordEncoder;
	private final JwtTokenProvider jwtTokenProvider;

	public AuthService(
			UserRepository userRepository,
			PasswordEncoder passwordEncoder,
			JwtTokenProvider jwtTokenProvider
	) {
		this.userRepository = userRepository;
		this.passwordEncoder = passwordEncoder;
		this.jwtTokenProvider = jwtTokenProvider;
	}

	@Transactional
	public SignUpResponse signUp(SignUpRequest request) {
		if (userRepository.existsByEmail(request.email())) {
			throw new BusinessException(ErrorCode.EMAIL_ALREADY_EXISTS);
		}

		User user = User.register(
				passwordEncoder.encode(request.password()),
				request.name(),
				request.email()
		);

		User savedUser = userRepository.save(user);
		return SignUpResponse.from(savedUser);
	}

	@Transactional(readOnly = true)
	public LoginResponse login(LoginRequest request) {
		User user = userRepository.findByEmail(request.email())
				.orElseThrow(() -> new BusinessException(ErrorCode.INVALID_CREDENTIALS));

		if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
			throw new BusinessException(ErrorCode.INVALID_CREDENTIALS);
		}

		JwtTokenProvider.IssuedAccessToken issuedAccessToken =
				jwtTokenProvider.issueAccessToken(user.getId(), user.getEmail());

		return LoginResponse.of(
				issuedAccessToken.token(),
				issuedAccessToken.expiresAt(),
				UserSummary.from(user)
		);
	}
}
