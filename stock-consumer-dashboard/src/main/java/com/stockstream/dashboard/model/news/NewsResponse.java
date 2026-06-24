package com.stockstream.dashboard.model.news;

import java.time.Instant;
import java.util.List;

public record NewsResponse(
        Instant asOf,
        List<NewsGroup> groups
) {}
