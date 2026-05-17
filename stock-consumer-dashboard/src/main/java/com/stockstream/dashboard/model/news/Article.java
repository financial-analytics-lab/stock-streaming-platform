package com.stockstream.dashboard.model.news;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record Article(
        String id,
        String symbol,
        String source,
        Instant publishedAt,
        String title,
        String teaser,
        String body,
        String url,
        String imageUrl,
        String section,
        Map<String, Object> raw
) {}
