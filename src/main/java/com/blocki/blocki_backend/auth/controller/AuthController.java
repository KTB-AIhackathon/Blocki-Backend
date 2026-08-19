package com.blocki.blocki_backend.auth.controller;

import java.net.URI;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.blocki.blocki_backend.auth.dto.LoginRequest;
import com.blocki.blocki_backend.auth.dto.LoginResponse;
import com.blocki.blocki_backend.auth.dto.SignUpRequest;
import com.blocki.blocki_backend.auth.dto.SignUpResponse;
import com.blocki.blocki_backend.auth.service.AuthService;
import com.blocki.blocki_backend.common.response.ApiResponse;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

	private final AuthService authService;

	public AuthController(AuthService authService) {
		this.authService = authService;
	}

	@PostMapping("/sign-up")
	public ResponseEntity<ApiResponse<SignUpResponse>> signUp(@Valid @RequestBody SignUpRequest request) {
		SignUpResponse response = authService.signUp(request);
		return ResponseEntity
				.created(URI.create("/api/v1/users/" + response.id()))
				.body(ApiResponse.of(response));
	}

	@PostMapping("/login")
	public ResponseEntity<ApiResponse<LoginResponse>> login(@Valid @RequestBody LoginRequest request) {
		LoginResponse response = authService.login(request);
		return ResponseEntity.ok(ApiResponse.of(response));
	}
}
