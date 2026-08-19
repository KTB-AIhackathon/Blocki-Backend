package com.blocki.blocki_backend.auth.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import com.blocki.blocki_backend.common.exception.BusinessException;
import com.blocki.blocki_backend.common.exception.ErrorCode;

@Component
public class CurrentUserProvider {

	public AuthenticatedUser getCurrentUser() {
		Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
		Object principal = authentication != null ? authentication.getPrincipal() : null;

		if (!(principal instanceof AuthenticatedUser authenticatedUser)) {
			throw new BusinessException(ErrorCode.UNAUTHENTICATED);
		}

		return authenticatedUser;
	}
}
