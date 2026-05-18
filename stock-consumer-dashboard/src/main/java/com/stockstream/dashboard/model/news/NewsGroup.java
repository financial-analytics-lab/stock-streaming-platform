package com.stockstream.dashboard.model.news;

import java.util.List;

public record NewsGroup(
        String symbol,
        String company,
        List<Article> articles
) {}
