package egx.newspublisher.model;

import java.time.Instant;
import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class NewsEvent implements Comparable<NewsEvent>, ReplayableEvent<NewsEvent> {

    private String symbol;
    private String company;
    private String isin;
    private String sector;
    private String id;
    private String date;
    private String time;
    private String datetime;
    private String title;
    private String body;
    private String teaser;
    private String section;
    private String source;
    private String categories;
    private String country;
    private long reads;
    private String url;
    private String imageUrl;
    private String dateRaw;
    private String sourceFeed;
    private Instant publishedAt;
    private long lagMs;

    @JsonIgnore
    private Instant replayDueTime;

    public String getSymbol() {
        return symbol;
    }

    public void setSymbol(String symbol) {
        this.symbol = symbol;
    }

    public String getCompany() {
        return company;
    }

    public void setCompany(String company) {
        this.company = company;
    }

    public String getIsin() {
        return isin;
    }

    public void setIsin(String isin) {
        this.isin = isin;
    }

    public String getSector() {
        return sector;
    }

    public void setSector(String sector) {
        this.sector = sector;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getDate() {
        return date;
    }

    public void setDate(String date) {
        this.date = date;
    }

    public String getTime() {
        return time;
    }

    public void setTime(String time) {
        this.time = time;
    }

    public String getDatetime() {
        return datetime;
    }

    public void setDatetime(String datetime) {
        this.datetime = datetime;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getBody() {
        return body;
    }

    public void setBody(String body) {
        this.body = body;
    }

    public String getTeaser() {
        return teaser;
    }

    public void setTeaser(String teaser) {
        this.teaser = teaser;
    }

    public String getSection() {
        return section;
    }

    public void setSection(String section) {
        this.section = section;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public String getCategories() {
        return categories;
    }

    public void setCategories(String categories) {
        this.categories = categories;
    }

    public String getCountry() {
        return country;
    }

    public void setCountry(String country) {
        this.country = country;
    }

    public long getReads() {
        return reads;
    }

    public void setReads(long reads) {
        this.reads = reads;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    @JsonProperty("image_url")
    public String getImageUrl() {
        return imageUrl;
    }

    @JsonProperty("image_url")
    public void setImageUrl(String imageUrl) {
        this.imageUrl = imageUrl;
    }

    @JsonProperty("date_raw")
    public String getDateRaw() {
        return dateRaw;
    }

    @JsonProperty("date_raw")
    public void setDateRaw(String dateRaw) {
        this.dateRaw = dateRaw;
    }

    @JsonProperty("_source")
    public String getSourceFeed() {
        return sourceFeed;
    }

    @JsonProperty("_source")
    public void setSourceFeed(String sourceFeed) {
        this.sourceFeed = sourceFeed;
    }

    @JsonProperty("published_at")
    public Instant getPublishedAt() {
        return publishedAt;
    }

    @JsonProperty("published_at")
    public void setPublishedAt(Instant publishedAt) {
        this.publishedAt = publishedAt;
    }

    @JsonProperty("lag_ms")
    public long getLagMs() {
        return lagMs;
    }

    @JsonProperty("lag_ms")
    public void setLagMs(long lagMs) {
        this.lagMs = lagMs;
    }

    @JsonIgnore
    @Override
    public Instant getReplayDueTime() {
        return replayDueTime;
    }

    public void setReplayDueTime(Instant replayDueTime) {
        this.replayDueTime = replayDueTime;
    }

    @JsonIgnore
    @Override
    public String getPartitionKey() {
        return symbol;
    }

    @Override
    public NewsEvent toKafkaValue(Instant publishedAt) {
        this.publishedAt = publishedAt;
        return this;
    }

    @Override
    public int compareTo(NewsEvent other) {
        if (this.replayDueTime == null && other.replayDueTime == null) {
            return 0;
        }
        if (this.replayDueTime == null) {
            return -1;
        }
        if (other.replayDueTime == null) {
            return 1;
        }
        return this.replayDueTime.compareTo(other.replayDueTime);
    }

    @Override
    public String toString() {
        return "NewsEvent{" +
                "symbol='" + symbol + '\'' +
                ", company='" + company + '\'' +
                ", datetime='" + datetime + '\'' +
                ", title='" + title + '\'' +
                ", replayDueTime=" + replayDueTime +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        NewsEvent newsEvent = (NewsEvent) o;
        return Objects.equals(id, newsEvent.id)
                && Objects.equals(datetime, newsEvent.datetime)
                && Objects.equals(title, newsEvent.title);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, datetime, title);
    }
}
