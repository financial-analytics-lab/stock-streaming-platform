package com.stockstream.core.model;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.util.List;

public record NewsEvent(
        @JsonProperty("id")         String id,
        @JsonProperty("datetime")   Instant datetime,
        @JsonProperty("title")      String title,
        @JsonProperty("body")       String body,
        @JsonProperty("teaser")     String teaser,
        @JsonProperty("section")    String section,
        @JsonProperty("source")     String source,
        @JsonProperty("categories") List<String> categories,
        @JsonProperty("country")    String country,
        @JsonProperty("reads")      long reads,
        @JsonProperty("url")        String url,
        @JsonProperty("image_url")  String imageUrl,
        @JsonProperty("date_raw")   String dateRaw
) {
    public NewsEvent {
        if (title == null || title.isBlank()) {
            throw new IllegalArgumentException("title must not be null or blank");
        }
        if (categories == null) categories = List.of();
    }
}
