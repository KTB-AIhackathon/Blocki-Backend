package com.blocki.blocki_backend.integration.repository;

import com.blocki.blocki_backend.integration.entity.OAuthState;
import com.blocki.blocki_backend.integration.entity.IntegrationProvider;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

public interface OAuthStateRepository extends JpaRepository<OAuthState, UUID> {

    Optional<OAuthState> findByStateHash(String stateHash);

    @Modifying
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @Query("""
            update OAuthState state
            set state.consumedAt = :now
            where state.stateHash = :stateHash
              and state.provider = :provider
              and state.consumedAt is null
              and state.expiresAt > :now
            """)
    int consumeIfValid(
            @Param("stateHash") String stateHash,
            @Param("provider") IntegrationProvider provider,
            @Param("now") Instant now);
}
