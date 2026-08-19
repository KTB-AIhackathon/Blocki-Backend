package com.blocki.blocki_backend.auth.security;

import java.util.UUID;

public record AuthenticatedUser(UUID userId, String email) {
}
