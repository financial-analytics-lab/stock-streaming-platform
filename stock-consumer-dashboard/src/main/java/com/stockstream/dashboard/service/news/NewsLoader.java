package com.stockstream.dashboard.service.news;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.stockstream.dashboard.model.news.Article;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

@Component
public class NewsLoader {

    private static final Logger LOG = LoggerFactory.getLogger(NewsLoader.class);
    private static final DateTimeFormatter DATETIME_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final Set<String> SKIP_FILES = Set.of("all_news_by_date.json", "combined_news.md");

    private final ObjectMapper mapper;
    private final Path dataPath;

    private final ConcurrentHashMap<String, SymbolBundle> bundles = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Article> byId = new ConcurrentHashMap<>();
    private final Map<Path, Long> lastSeenMtimes = new HashMap<>();
    private volatile boolean loadedOnce = false;
    private final Object reloadLock = new Object();

    public NewsLoader(ObjectMapper mapper,
                      @Value("${news.scraping.data-path}") String dataPathProp) {
        this.mapper = mapper;
        this.dataPath = Paths.get(dataPathProp).toAbsolutePath().normalize();
    }

    /** Returns all cached symbol bundles. Triggers a reload-if-changed scan. */
    public Collection<SymbolBundle> getBundles() {
        reloadIfChanged();
        return bundles.values();
    }

    /** Look up a single article by its stable id (full content, including body + raw). */
    public Optional<Article> findById(String id) {
        reloadIfChanged();
        return Optional.ofNullable(byId.get(id));
    }

    /** Re-scan the data directory if any file's mtime changed (or first call). Safe to invoke per-request. */
    public void reloadIfChanged() {
        synchronized (reloadLock) {
            if (!Files.isDirectory(dataPath)) {
                if (!loadedOnce) {
                    LOG.warn("news.scraping.data-path does not exist: {}", dataPath);
                    loadedOnce = true;
                }
                return;
            }

            Map<Path, Long> current = new HashMap<>();
            try (Stream<Path> stream = Files.list(dataPath)) {
                stream.filter(this::isCandidateFile).forEach(p -> {
                    try {
                        current.put(p, Files.readAttributes(p, BasicFileAttributes.class).lastModifiedTime().toMillis());
                    } catch (IOException e) {
                        LOG.warn("Cannot stat {}: {}", p, e.toString());
                    }
                });
            } catch (IOException e) {
                LOG.warn("Cannot list {}: {}", dataPath, e.toString());
                return;
            }

            if (loadedOnce && current.equals(lastSeenMtimes)) return;

            ConcurrentHashMap<String, SymbolBundle> nextBundles = new ConcurrentHashMap<>();
            ConcurrentHashMap<String, Article> nextById = new ConcurrentHashMap<>();
            for (Path file : current.keySet()) {
                try {
                    SymbolBundle bundle = parseFile(file);
                    if (bundle != null) {
                        nextBundles.put(bundle.symbol(), bundle);
                        for (Article a : bundle.articles()) nextById.put(a.id(), a);
                    }
                } catch (IOException e) {
                    LOG.warn("Failed to parse {}: {}", file, e.toString());
                }
            }
            bundles.clear();
            bundles.putAll(nextBundles);
            byId.clear();
            byId.putAll(nextById);
            lastSeenMtimes.clear();
            lastSeenMtimes.putAll(current);
            loadedOnce = true;
            LOG.info("Loaded {} articles across {} symbols from {}",
                    byId.size(), bundles.size(), dataPath);
        }
    }

    private boolean isCandidateFile(Path p) {
        if (!Files.isRegularFile(p)) return false;
        String name = p.getFileName().toString();
        if (SKIP_FILES.contains(name)) return false;
        return name.endsWith(".json");
    }

    private SymbolBundle parseFile(Path file) throws IOException {
        JsonNode root = mapper.readTree(file.toFile());
        JsonNode meta = root.path("metadata");
        String symbol = meta.path("symbol").asText(file.getFileName().toString().replace(".json", ""));
        String company = meta.path("company").asText("");

        JsonNode articlesNode = root.path("articles");
        if (!articlesNode.isArray()) return null;

        List<Article> articles = new ArrayList<>(articlesNode.size());
        for (JsonNode a : articlesNode) {
            Article article = toArticle(symbol, a);
            if (article != null) articles.add(article);
        }
        articles.sort(Comparator.comparing(Article::publishedAt));
        return new SymbolBundle(symbol.toUpperCase(), company, List.copyOf(articles));
    }

    private Article toArticle(String symbol, JsonNode a) {
        Instant publishedAt = parsePublishedAt(a);
        if (publishedAt == null) return null;

        String source = a.path("_source").asText("");
        String title = a.path("title").asText("");
        String rawId = a.path("id").asText("");
        String id = buildId(source, rawId, symbol, a.path("datetime").asText(""), title);

        Map<String, Object> raw = mapper.convertValue(a, new com.fasterxml.jackson.core.type.TypeReference<>() {});

        return new Article(
                id,
                symbol.toUpperCase(),
                source,
                publishedAt,
                title,
                a.path("teaser").asText(""),
                a.path("body").asText(""),
                a.path("url").asText(""),
                a.path("image_url").asText(""),
                a.path("section").asText(""),
                raw
        );
    }

    private Instant parsePublishedAt(JsonNode a) {
        String datetime = a.path("datetime").asText("");
        if (!datetime.isBlank()) {
            try {
                return LocalDateTime.parse(datetime, DATETIME_FMT).toInstant(ZoneOffset.UTC);
            } catch (Exception ignored) {}
        }
        String date = a.path("date").asText("");
        if (!date.isBlank()) {
            try {
                return LocalDate.parse(date).atStartOfDay().toInstant(ZoneOffset.UTC);
            } catch (Exception ignored) {}
        }
        return null;
    }

    private String buildId(String source, String rawId, String symbol, String datetime, String title) {
        String prefix = source.isBlank() ? "news" : source;
        if (!rawId.isBlank()) return prefix + ":" + rawId;
        return prefix + ":" + sha1Short(symbol + "|" + datetime + "|" + title);
    }

    private static String sha1Short(String s) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-1");
            byte[] digest = md.digest(s.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(40);
            for (byte b : digest) sb.append(String.format("%02x", b));
            return sb.substring(0, 16);
        } catch (NoSuchAlgorithmException e) {
            return Integer.toHexString(s.hashCode());
        }
    }

    /** Immutable per-symbol cached bundle. Articles are sorted ascending by publishedAt. */
    public record SymbolBundle(String symbol, String company, List<Article> articles) {

        /** Returns the prefix of articles with publishedAt <= asOf, newest-first. */
        public List<Article> sliceAsOf(Instant asOf) {
            int idx = upperBound(articles, asOf);
            if (idx == 0) return List.of();
            List<Article> slice = new ArrayList<>(articles.subList(0, idx));
            Collections.reverse(slice);
            return slice;
        }

        /** Returns the count of articles with publishedAt <= asOf, i.e. the size of `sliceAsOf`. */
        private static int upperBound(List<Article> sorted, Instant asOf) {
            int lo = 0, hi = sorted.size();
            while (lo < hi) {
                int mid = (lo + hi) >>> 1;
                if (sorted.get(mid).publishedAt().isAfter(asOf)) hi = mid;
                else lo = mid + 1;
            }
            return lo;
        }
    }
}
