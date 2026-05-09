package com.stockstream.dashboard.api;

import com.stockstream.core.model.NewsEvent;
import com.stockstream.dashboard.store.NewsStore;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/news")
public class NewsController {

    private final NewsStore newsStore;

    public NewsController(NewsStore newsStore) {
        this.newsStore = newsStore;
    }

    @GetMapping
    public List<NewsEvent> getNews(@RequestParam(defaultValue = "50") int limit) {
        return newsStore.getRecent(Math.min(limit, 100));
    }
}
