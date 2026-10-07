package com.wikirace.dto;

import java.util.UUID;

public record ArticleResponse(String roomCode, UUID playerId, long roomVersion, long movementRevision,
                              String title, String html, String sourceUrl, Long revisionId) {}
