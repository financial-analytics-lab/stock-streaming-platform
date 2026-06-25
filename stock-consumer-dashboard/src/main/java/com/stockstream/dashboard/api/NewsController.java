package com.stockstream.dashboard.api;

import com.stockstream.dashboard.model.news.Article;
import com.stockstream.dashboard.model.news.NewsGroup;
import com.stockstream.dashboard.model.news.NewsResponse;
import com.stockstream.dashboard.service.news.NewsLoader;
import com.stockstream.dashboard.service.news.NewsLoader.SymbolBundle;
import com.stockstream.dashboard.store.TickStore;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/api/news")
public class NewsController {

    private final NewsLoader loader;
    private final TickStore tickStore;

    public NewsController(NewsLoader loader, TickStore tickStore) {
        this.loader = loader;
        this.tickStore = tickStore;
    }

    /**
     * Articles grouped by symbol, filtered to those published at or before the
     * current simulation timestamp. Slim view (no body/raw) — fetch full content
     * per article via {@link #getArticle}.
     */
    @GetMapping
    public NewsResponse getNews(@RequestParam(required = false) String asOf) {
        Instant cutoff = resolveAsOf(asOf);

        List<NewsGroup> groups = new ArrayList<>();
        if (!cutoff.equals(Instant.EPOCH)) {
            for (SymbolBundle bundle : loader.getBundles()) {
                List<Article> articles = bundle.sliceAsOf(cutoff).stream()
                        .map(NewsController::slim)
                        .toList();
                if (!articles.isEmpty()) {
                    groups.add(new NewsGroup(bundle.symbol(), bundle.company(), articles));
                }
            }
            groups.sort(Comparator.comparing(NewsGroup::symbol));
        }

        return new NewsResponse(cutoff, groups);
    }

    @GetMapping("/{id}")
    public ResponseEntity<Article> getArticle(@PathVariable String id) {
        return loader.findById(id)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    private Instant resolveAsOf(String explicit) {
        if (explicit != null && !explicit.isBlank()) {
            try { return Instant.parse(explicit); } catch (Exception ignored) {}
        }
        Optional<Instant> simNow = tickStore.currentSimulationTime();
        return simNow.orElse(Instant.EPOCH);
    }

    private static Article slim(Article a) {
        return new Article(
                a.id(), a.symbol(), a.source(), a.publishedAt(),
                a.title(), a.teaser(), null, a.url(), a.imageUrl(), a.section(), null
        );
    }
}
