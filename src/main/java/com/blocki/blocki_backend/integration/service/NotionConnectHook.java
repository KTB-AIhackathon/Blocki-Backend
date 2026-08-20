package com.blocki.blocki_backend.integration.service;

import java.util.UUID;

/**
 * Runs after Notion OAuth succeeds, so the dashboard exists before the first job.
 * Missing or failing here must not roll back the token; the job path retries.
 */
public interface NotionConnectHook {

    void afterNotionConnected(UUID userId, String accessToken);
}
