package com.blocki.blocki_backend.integration.controller;

import com.blocki.blocki_backend.auth.security.CurrentUserProvider;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class SecurityContextCurrentUserIdResolver implements CurrentUserIdResolver {

    private final CurrentUserProvider currentUserProvider;

    public SecurityContextCurrentUserIdResolver(CurrentUserProvider currentUserProvider) {
        this.currentUserProvider = currentUserProvider;
    }

    @Override
    public UUID resolve() {
        return currentUserProvider.getCurrentUser().userId();
    }
}
