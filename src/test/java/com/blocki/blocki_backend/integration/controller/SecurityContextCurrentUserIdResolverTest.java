package com.blocki.blocki_backend.integration.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.blocki.blocki_backend.auth.security.AuthenticatedUser;
import com.blocki.blocki_backend.auth.security.CurrentUserProvider;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class SecurityContextCurrentUserIdResolverTest {

    @Test
    void resolves_the_authenticated_users_id_from_the_auth_domain() {
        CurrentUserProvider currentUserProvider = Mockito.mock(CurrentUserProvider.class);
        UUID userId = UUID.randomUUID();
        when(currentUserProvider.getCurrentUser()).thenReturn(new AuthenticatedUser(userId, "user@example.com"));

        CurrentUserIdResolver resolver = new SecurityContextCurrentUserIdResolver(currentUserProvider);

        assertThat(resolver.resolve()).isEqualTo(userId);
    }
}
