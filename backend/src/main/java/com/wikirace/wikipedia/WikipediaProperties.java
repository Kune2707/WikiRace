package com.wikirace.wikipedia;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("app.wikipedia")
public record WikipediaProperties(String userAgent, Duration connectTimeout, Duration loadTimeout,
                                  int cacheSize, Duration cacheTtl, int maxResponseBytes) {
    public WikipediaProperties {
        if (userAgent == null || userAgent.isBlank() || userAgent.contains("\r") || userAgent.contains("\n") ||
                connectTimeout == null || connectTimeout.isNegative() || connectTimeout.isZero() ||
                loadTimeout == null || loadTimeout.isNegative() || loadTimeout.isZero() ||
                cacheSize < 1 || cacheTtl == null || cacheTtl.isNegative() || cacheTtl.isZero() || maxResponseBytes < 1) {
            throw new IllegalArgumentException("Wikipedia timeouts, cache limits and User-Agent must be valid.");
        }
    }
}
