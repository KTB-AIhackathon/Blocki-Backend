package com.blocki.blocki_backend.auth.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

	private static final String[] PUBLIC_ENDPOINTS = {
			"/api/v1/auth/sign-up",
			"/api/v1/auth/login",
			"/api/v1/integrations/*/callback"
	};

	private final JwtTokenProvider jwtTokenProvider;
	private final RestAuthenticationEntryPoint restAuthenticationEntryPoint;
	private final RestAccessDeniedHandler restAccessDeniedHandler;

	public SecurityConfig(
			JwtTokenProvider jwtTokenProvider,
			RestAuthenticationEntryPoint restAuthenticationEntryPoint,
			RestAccessDeniedHandler restAccessDeniedHandler
	) {
		this.jwtTokenProvider = jwtTokenProvider;
		this.restAuthenticationEntryPoint = restAuthenticationEntryPoint;
		this.restAccessDeniedHandler = restAccessDeniedHandler;
	}

	@Bean
	public PasswordEncoder passwordEncoder() {
		return new BCryptPasswordEncoder();
	}

	@Bean
	public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
		http
				.csrf(csrf -> csrf.disable())
				.formLogin(formLogin -> formLogin.disable())
				.httpBasic(httpBasic -> httpBasic.disable())
				.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
				.exceptionHandling(exceptionHandling -> exceptionHandling
						.authenticationEntryPoint(restAuthenticationEntryPoint)
						.accessDeniedHandler(restAccessDeniedHandler))
				.authorizeHttpRequests(authorize -> authorize
						.requestMatchers(PUBLIC_ENDPOINTS).permitAll()
						.anyRequest().authenticated())
				.addFilterBefore(
						new JwtAuthenticationFilter(jwtTokenProvider),
						UsernamePasswordAuthenticationFilter.class
				);

		return http.build();
	}
}
