package com.blocki.blocki_backend.integration.service;

import java.util.Optional;
import java.util.UUID;

/**
 * Remembers which Notion page the AI worker may write under.
 *
 * <p>The worker keeps no state, so the page id has to live here. Without it every
 * job would fall back to searching the workspace by title, and on workspaces where
 * search is unavailable that search silently returns nothing — leaving a fresh
 * dashboard behind on each run.
 */
public interface NotionDashboardStore {

    Optional<String> findNotionDashboardPageId(UUID userId);

    void rememberNotionDashboardPageId(UUID userId, String pageId);
}
