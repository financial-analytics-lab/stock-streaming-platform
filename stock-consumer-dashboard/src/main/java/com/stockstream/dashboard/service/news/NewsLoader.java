package com.stockstream.dashboard.service.news;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.stockstream.dashboard.model.news.Article;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
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

@Component
public class NewsLoader {

    private static final Logger LOG = LoggerFactory.getLogger(NewsLoader.class);
    private static final DateTimeFormatter DATETIME_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final ObjectMapper mapper;
    private final Path dataFile;

    private final ConcurrentHashMap<String, SymbolBundle> bundles = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Article> byId = new ConcurrentHashMap<>();
    private volatile long lastSeenMtime = -1;
    private volatile boolean loadedOnce = false;
    private final Object reloadLock = new Object();

    public NewsLoader(ObjectMapper mapper,
                      @Value("${news.data.file:/mnt/all_news_by_date.json}") String dataFileProp) {
        this.mapper = mapper;
        this.dataFile = Paths.get(dataFileProp).toAbsolutePath().normalize();
    }

    public Collection<SymbolBundle> getBundles() {
        reloadIfChanged();
        return bundles.values();
    }

    public Optional<Article> findById(String id) {
        reloadIfChanged();
        return Optional.ofNullable(byId.get(id));
    }

    public void reloadIfChanged() {
        synchronized (reloadLock) {
            if (!Files.isRegularFile(dataFile)) {
                if (!loadedOnce) {
                    LOG.warn("news.data.file not found: {}", dataFile);
                    loadedOnce = true;
                }
                return;
            }

            long mtime;
            try {
                mtime = Files.readAttributes(dataFile, BasicFileAttributes.class)
                        .lastModifiedTime().toMillis();
            } catch (IOException e) {
                LOG.warn("Cannot stat {}: {}", dataFile, e.toString());
                return;
            }

            if (loadedOnce && mtime == lastSeenMtime) return;

            try {
                load();
                lastSeenMtime = mtime;
                loadedOnce = true;
            } catch (IOException e) {
                LOG.error("Failed to parse {}: {}", dataFile, e.toString());
            }
        }
    }

    private void load() throws IOException {
        JsonNode root = mapper.readTree(dataFile.toFile());
        JsonNode byDate = root.path("by_date");
        if (!byDate.isObject()) {
            LOG.warn("No 'by_date' object in {}", dataFile);
            return;
        }

        Map<String, List<Article>> bySymbol = new LinkedHashMap<>();
        Map<String, String> symbolToCompany = new HashMap<>();
        Map<String, Article> nextById = new HashMap<>();

        Iterator<Map.Entry<String, JsonNode>> dateIter = byDate.fields();
        while (dateIter.hasNext()) {
            JsonNode articles = dateIter.next().getValue();
            if (!articles.isArray()) continue;

            for (JsonNode a : articles) {
                Article article = toArticle(a);
                if (article == null) continue;

                String sym = article.symbol();
                bySymbol.computeIfAbsent(sym, k -> new ArrayList<>()).add(article);
                symbolToCompany.putIfAbsent(sym, a.path("company").asText(""));
                nextById.put(article.id(), article);
            }
        }

        ConcurrentHashMap<String, SymbolBundle> nextBundles = new ConcurrentHashMap<>();
        for (Map.Entry<String, List<Article>> e : bySymbol.entrySet()) {
            List<Article> sorted = e.getValue();
            sorted.sort(Comparator.comparing(Article::publishedAt));
            nextBundles.put(e.getKey(), new SymbolBundle(
                    e.getKey(),
                    symbolToCompany.getOrDefault(e.getKey(), ""),
                    List.copyOf(sorted)
            ));
        }

        bundles.clear();
        bundles.putAll(nextBundles);
        byId.clear();
        byId.putAll(nextById);
        LOG.info("Loaded {} articles across {} symbols from {}",
                nextById.size(), nextBundles.size(), dataFile);
    }

    private Article toArticle(JsonNode a) {
        Instant publishedAt = parsePublishedAt(a);
        if (publishedAt == null) return null;

        String symbol = a.path("symbol").asText("").toUpperCase();
        if (symbol.isBlank()) return null;

        String source = a.path("_source").asText(a.path("source").asText(""));
        String rawId = a.path("id").asText("");
        String title = a.path("title").asText("");
        String id = buildId(source, rawId, symbol, a.path("datetime").asText(""), title);

        Map<String, Object> raw = mapper.convertValue(a, new TypeReference<>() {});

        return new Article(
                id,
                symbol,
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
        String time = a.path("time").asText("00:00:00");
        if (!date.isBlank()) {
            try {
                return LocalDateTime.parse(date + " " + time, DATETIME_FMT).toInstant(ZoneOffset.UTC);
            } catch (Exception ignored) {
                try {
                    return LocalDate.parse(date).atStartOfDay().toInstant(ZoneOffset.UTC);
                } catch (Exception ignored2) {}
            }
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

    public record SymbolBundle(String symbol, String company, List<Article> articles) {

        public List<Article> sliceAsOf(Instant asOf) {
            int idx = upperBound(articles, asOf);
            if (idx == 0) return List.of();
            List<Article> slice = new ArrayList<>(articles.subList(0, idx));
            Collections.reverse(slice);
            return slice;
        }

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
