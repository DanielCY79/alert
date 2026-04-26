package com.mobai.alert.service;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 为通知卡片补充“市场热度”字段。
 *
 * 评分逻辑参考 lanalaoshi 项目的 sentiment heat：
 * - CryptoPanic / Reddit fallback
 * - CoinGecko Trending
 * - Binance 24h Gainers
 * - Fear & Greed
 *
 * 当前 Java 项目没有接 Playwright，因此 Binance Square 只能用公开 hashtag 页的
 * views / discussing 作为 Square mentions proxy，再并入社交热度分。
 */
@Service
public class MarketSentimentService {

    private static final String HASHTAG_URL_TEMPLATE = "https://www.binance.info/en/square/hashtag/%s";
    private static final String CRYPTOPANIC_URL_TEMPLATE = "https://cryptopanic.com/api/free/v1/posts/?public=true&page=%d";
    private static final String REDDIT_HOT_URL = "https://www.reddit.com/r/CryptoCurrency/hot.json?limit=100";
    private static final String FEAR_GREED_URL = "https://api.alternative.me/fng/?limit=1";
    private static final String COINGECKO_TRENDING_URL = "https://api.coingecko.com/api/v3/search/trending";
    private static final String BINANCE_24H_TICKER_URL = "https://fapi.binance.com/fapi/v1/ticker/24hr";

    private static final Pattern VIEWS_PATTERN = Pattern.compile("(?i)([0-9][0-9,]*(?:\\.[0-9]+)?\\s*[MK]?)\\s+views");
    private static final Pattern DISCUSSING_PATTERN = Pattern.compile("(?i)([0-9][0-9,]*(?:\\.[0-9]+)?\\s*[MK]?)\\s+Discussing");
    private static final List<String> QUOTE_ASSETS = List.of("USDT", "USDC", "FDUSD", "BUSD", "BTC", "ETH");

    private static final String USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
            + "(KHTML, like Gecko) Chrome/135.0.0.0 Safari/537.36";
    private static final String REDDIT_USER_AGENT = "alert-bot/1.0";
    private static final String UNAVAILABLE_TEXT = "市场热度：暂不可用";

    private final RestTemplate restTemplate;
    private final ConcurrentMap<String, CachedSentiment> cache = new ConcurrentHashMap<>();

    @Value("${market.sentiment.enabled:${binance.square.heat.enabled:true}}")
    private boolean enabled;

    @Value("${market.sentiment.cache-ms:${binance.square.heat.cache-ms:300000}}")
    private long cacheMs;

    @Value("${market.sentiment.cryptopanic-api-key:}")
    private String cryptopanicApiKey;

    @Value("${market.sentiment.cryptopanic-pages:3}")
    private int cryptopanicPages;

    @Value("${market.sentiment.min-mentions:2}")
    private int minMentions;

    @Value("${market.sentiment.cryptopanic-weight:0.45}")
    private double cryptopanicWeight;

    @Value("${market.sentiment.gainers-weight:0.25}")
    private double gainersWeight;

    @Value("${market.sentiment.coingecko-weight:0.15}")
    private double coingeckoWeight;

    @Value("${market.sentiment.fear-greed-weight:0.15}")
    private double fearGreedWeight;

    @Value("${market.sentiment.binance-min-24h-volume-usdt:50000000}")
    private double min24hVolumeUsdt;

    public MarketSentimentService(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    @Scheduled(fixedDelayString = "${market.sentiment.cleanup-ms:${binance.square.heat.cleanup-ms:300000}}")
    public void cleanupExpiredCache() {
        long now = System.currentTimeMillis();
        cache.entrySet().removeIf(entry -> now - entry.getValue().cachedAt >= cacheMs);
    }

    /**
     * 在正文末尾链接前插入一行市场热度。
     */
    public String enrichBody(String symbol, String body) {
        if (!StringUtils.hasText(body)) {
            return body;
        }

        String sentimentLine = buildSentimentLine(symbol);
        String normalizedBody = body.stripTrailing();
        int lastLineBreak = normalizedBody.lastIndexOf('\n');
        if (lastLineBreak >= 0) {
            String lastLine = normalizedBody.substring(lastLineBreak + 1).trim();
            if (lastLine.startsWith("[") && lastLine.contains("](")) {
                return normalizedBody.substring(0, lastLineBreak + 1)
                        + sentimentLine
                        + "\n"
                        + lastLine;
            }
        }
        return normalizedBody + "\n" + sentimentLine;
    }

    private String buildSentimentLine(String symbol) {
        if (!enabled || !StringUtils.hasText(symbol)) {
            return UNAVAILABLE_TEXT;
        }

        SentimentSnapshot snapshot = loadSnapshot(symbol);
        if (snapshot == null || !snapshot.hasAnySource()) {
            return UNAVAILABLE_TEXT;
        }

        List<String> parts = new ArrayList<>();
        parts.add("市场热度：" + snapshot.scoreLabel());

        if (StringUtils.hasText(snapshot.squareDiscussingText)) {
            parts.add("Square热议 " + snapshot.squareDiscussingText);
        }

        if (snapshot.socialMentions > 0) {
            if (snapshot.usingRedditFallback) {
                parts.add("Reddit提及 " + snapshot.socialMentions);
            } else if (snapshot.bullishVotes > 0 || snapshot.bearishVotes > 0) {
                parts.add("新闻提及 " + snapshot.socialMentions
                        + "(+" + snapshot.bullishVotes
                        + "/-" + snapshot.bearishVotes + ")");
            } else {
                parts.add("新闻提及 " + snapshot.socialMentions);
            }
        }

        if (snapshot.coingeckoTrendingRank != null) {
            parts.add("CoinGecko #" + snapshot.coingeckoTrendingRank);
        }

        if (snapshot.gainersRank != null) {
            parts.add("涨幅榜 #" + snapshot.gainersRank);
        }

        if (snapshot.fearGreedValue != null) {
            parts.add("F&G " + snapshot.fearGreedValue);
        }

        return String.join(" | ", parts);
    }

    private SentimentSnapshot loadSnapshot(String symbol) {
        String normalizedSymbol = symbol.trim().toUpperCase(Locale.ROOT);
        long now = System.currentTimeMillis();
        CachedSentiment cachedSentiment = cache.get(normalizedSymbol);
        if (cachedSentiment != null && now - cachedSentiment.cachedAt < cacheMs) {
            return cachedSentiment.snapshot;
        }

        SentimentSnapshot snapshot = fetchSnapshot(normalizedSymbol);
        if (snapshot != null) {
            cache.put(normalizedSymbol, new CachedSentiment(snapshot, now));
        }
        return snapshot;
    }

    private SentimentSnapshot fetchSnapshot(String symbol) {
        String baseAsset = extractBaseAsset(symbol);
        SquareMetrics squareMetrics = fetchSquareMetrics(symbol);

        boolean useRedditFallback = !StringUtils.hasText(cryptopanicApiKey);
        SocialMetrics socialMetrics = useRedditFallback
                ? fetchRedditMetrics(baseAsset)
                : fetchCryptoPanicMetrics(baseAsset);

        FearGreedMetrics fearGreedMetrics = fetchFearGreedMetrics();
        Integer coingeckoTrendingRank = fetchCoingeckoTrendingRank(baseAsset);
        Integer gainersRank = fetchBinanceGainersRank(baseAsset);

        int squareMentionsEstimate = estimateSquareMentions(squareMetrics);
        int mergedMentions = socialMetrics.mentions + squareMentionsEstimate;
        double compositeScore = computeCompositeScore(
                mergedMentions,
                socialMetrics.bullishVotes,
                socialMetrics.bearishVotes,
                gainersRank,
                coingeckoTrendingRank,
                fearGreedMetrics
        );

        return new SentimentSnapshot(
                symbol,
                compositeScore,
                squareMetrics.tag,
                squareMetrics.viewsText,
                squareMetrics.discussingText,
                socialMetrics.mentions,
                socialMetrics.bullishVotes,
                socialMetrics.bearishVotes,
                useRedditFallback,
                coingeckoTrendingRank,
                gainersRank,
                fearGreedMetrics.value,
                fearGreedMetrics.label
        );
    }

    private SquareMetrics fetchSquareMetrics(String symbol) {
        for (String tag : buildCandidateTags(symbol)) {
            SquareMetrics metrics = fetchSquareTagMetrics(tag);
            if (metrics.hasMetrics()) {
                return metrics;
            }
        }
        return SquareMetrics.unavailable();
    }

    private SquareMetrics fetchSquareTagMetrics(String tag) {
        try {
            ResponseEntity<String> response = restTemplate.exchange(
                    buildHashtagUrl(tag),
                    HttpMethod.GET,
                    new HttpEntity<>(buildHtmlHeaders()),
                    String.class
            );
            String html = response.getBody();
            if (!StringUtils.hasText(html)) {
                return SquareMetrics.unavailable();
            }

            Document document = Jsoup.parse(html);
            String pageText = document.text();
            if (!StringUtils.hasText(pageText)
                    || pageText.contains("not currently available in your country or region")) {
                return SquareMetrics.unavailable();
            }

            String viewsText = extractMetric(VIEWS_PATTERN, pageText);
            String discussingText = extractMetric(DISCUSSING_PATTERN, pageText);
            return new SquareMetrics(
                    tag,
                    viewsText,
                    discussingText,
                    parseMetric(viewsText),
                    parseMetric(discussingText)
            );
        } catch (Exception e) {
            return SquareMetrics.unavailable();
        }
    }

    private SocialMetrics fetchCryptoPanicMetrics(String baseAsset) {
        int mentions = 0;
        int bullishVotes = 0;
        int bearishVotes = 0;

        for (int page = 1; page <= cryptopanicPages; page++) {
            try {
                String url = CRYPTOPANIC_URL_TEMPLATE.formatted(page);
                if (StringUtils.hasText(cryptopanicApiKey)) {
                    url += "&auth_token=" + UriUtils.encodeQueryParam(cryptopanicApiKey, StandardCharsets.UTF_8);
                }

                ResponseEntity<String> response = restTemplate.exchange(
                        url,
                        HttpMethod.GET,
                        new HttpEntity<>(buildJsonHeaders()),
                        String.class
                );
                JSONObject payload = JSON.parseObject(response.getBody());
                JSONArray results = payload == null ? null : payload.getJSONArray("results");
                if (results == null || results.isEmpty()) {
                    break;
                }

                for (int i = 0; i < results.size(); i++) {
                    JSONObject post = results.getJSONObject(i);
                    if (!containsTicker(post == null ? null : post.getJSONArray("currencies"), baseAsset)) {
                        continue;
                    }
                    mentions++;
                    JSONObject votes = post.getJSONObject("votes");
                    if (votes != null) {
                        bullishVotes += votes.getIntValue("positive");
                        bearishVotes += votes.getIntValue("negative");
                    }
                }
            } catch (Exception e) {
                break;
            }
        }

        return new SocialMetrics(mentions, bullishVotes, bearishVotes);
    }

    private SocialMetrics fetchRedditMetrics(String baseAsset) {
        try {
            ResponseEntity<String> response = restTemplate.exchange(
                    REDDIT_HOT_URL,
                    HttpMethod.GET,
                    new HttpEntity<>(buildRedditHeaders()),
                    String.class
            );
            JSONObject payload = JSON.parseObject(response.getBody());
            JSONObject data = payload == null ? null : payload.getJSONObject("data");
            JSONArray children = data == null ? null : data.getJSONArray("children");
            if (children == null || children.isEmpty()) {
                return SocialMetrics.empty();
            }

            Pattern mentionPattern = buildTickerMentionPattern(baseAsset);
            int mentions = 0;
            for (int i = 0; i < children.size(); i++) {
                JSONObject child = children.getJSONObject(i);
                JSONObject postData = child == null ? null : child.getJSONObject("data");
                if (postData == null) {
                    continue;
                }
                String text = (postData.getString("title") == null ? "" : postData.getString("title"))
                        + "\n"
                        + (postData.getString("selftext") == null ? "" : postData.getString("selftext"));
                if (mentionPattern.matcher(text.toUpperCase(Locale.ROOT)).find()) {
                    mentions++;
                }
            }
            return new SocialMetrics(mentions, 0, 0);
        } catch (Exception e) {
            return SocialMetrics.empty();
        }
    }

    private FearGreedMetrics fetchFearGreedMetrics() {
        try {
            ResponseEntity<String> response = restTemplate.exchange(
                    FEAR_GREED_URL,
                    HttpMethod.GET,
                    new HttpEntity<>(buildJsonHeaders()),
                    String.class
            );
            JSONObject payload = JSON.parseObject(response.getBody());
            JSONArray data = payload == null ? null : payload.getJSONArray("data");
            JSONObject latest = data == null || data.isEmpty() ? null : data.getJSONObject(0);
            if (latest == null) {
                return FearGreedMetrics.unavailable();
            }

            Integer value = latest.getInteger("value");
            String label = latest.getString("value_classification");
            return new FearGreedMetrics(value, label);
        } catch (Exception e) {
            return FearGreedMetrics.unavailable();
        }
    }

    private Integer fetchCoingeckoTrendingRank(String baseAsset) {
        try {
            ResponseEntity<String> response = restTemplate.exchange(
                    COINGECKO_TRENDING_URL,
                    HttpMethod.GET,
                    new HttpEntity<>(buildJsonHeaders()),
                    String.class
            );
            JSONObject payload = JSON.parseObject(response.getBody());
            JSONArray coins = payload == null ? null : payload.getJSONArray("coins");
            if (coins == null) {
                return null;
            }

            for (int i = 0; i < coins.size(); i++) {
                JSONObject wrapper = coins.getJSONObject(i);
                JSONObject item = wrapper == null ? null : wrapper.getJSONObject("item");
                String symbol = item == null ? null : item.getString("symbol");
                if (baseAsset.equalsIgnoreCase(symbol)) {
                    return i + 1;
                }
            }
            return null;
        } catch (Exception e) {
            return null;
        }
    }

    private Integer fetchBinanceGainersRank(String baseAsset) {
        try {
            ResponseEntity<String> response = restTemplate.exchange(
                    BINANCE_24H_TICKER_URL,
                    HttpMethod.GET,
                    new HttpEntity<>(buildJsonHeaders()),
                    String.class
            );
            JSONArray tickers = JSON.parseArray(response.getBody());
            if (tickers == null || tickers.isEmpty()) {
                return null;
            }

            List<JSONObject> eligible = new ArrayList<>();
            for (int i = 0; i < tickers.size(); i++) {
                JSONObject ticker = tickers.getJSONObject(i);
                if (ticker == null) {
                    continue;
                }
                String symbol = ticker.getString("symbol");
                double quoteVolume = parseDouble(ticker.getString("quoteVolume"));
                if (!StringUtils.hasText(symbol)
                        || !symbol.endsWith("USDT")
                        || quoteVolume < min24hVolumeUsdt) {
                    continue;
                }
                eligible.add(ticker);
            }

            eligible.sort((left, right) -> Double.compare(
                    parseDouble(right.getString("priceChangePercent")),
                    parseDouble(left.getString("priceChangePercent"))
            ));

            int topN = Math.min(20, eligible.size());
            for (int i = 0; i < topN; i++) {
                JSONObject ticker = eligible.get(i);
                String symbol = ticker.getString("symbol");
                if (extractBaseAsset(symbol).equalsIgnoreCase(baseAsset)) {
                    return i + 1;
                }
            }
            return null;
        } catch (Exception e) {
            return null;
        }
    }

    private double computeCompositeScore(int mentions,
                                         int bullishVotes,
                                         int bearishVotes,
                                         Integer gainersRank,
                                         Integer trendingRank,
                                         FearGreedMetrics fearGreedMetrics) {
        double score = 0.0;

        if (mentions >= minMentions) {
            double mentionScore = Math.min(Math.log(mentions + 1.0) * 20.0, 100.0);
            double totalVotes = bullishVotes + bearishVotes;
            double sentimentRatio = totalVotes > 0 ? bullishVotes / totalVotes : 0.5;
            double sentimentBoost = (sentimentRatio - 0.5) * 40.0;
            double socialScore = clamp(mentionScore + sentimentBoost, 0.0, 100.0);
            score += socialScore * cryptopanicWeight;
        }

        if (gainersRank != null) {
            double gainersScore = Math.max(0.0, 100.0 - gainersRank * 5.0);
            score += gainersScore * gainersWeight;
        }

        if (trendingRank != null) {
            double trendingScore = Math.max(0.0, 100.0 - trendingRank * 10.0);
            score += trendingScore * coingeckoWeight;
        }

        if (fearGreedMetrics.value != null) {
            double fearGreedScore;
            if (fearGreedMetrics.value <= 25) {
                fearGreedScore = 70.0;
            } else if (fearGreedMetrics.value >= 75) {
                fearGreedScore = 30.0;
            } else {
                fearGreedScore = 50.0;
            }
            score += fearGreedScore * fearGreedWeight;
        }

        return new BigDecimal(score).setScale(2, RoundingMode.HALF_UP).doubleValue();
    }

    /**
     * 将 hashtag 页的大盘热度压缩到与“mentions”相近的量级，便于复用原项目的打分公式。
     */
    private int estimateSquareMentions(SquareMetrics squareMetrics) {
        if (squareMetrics == null || !squareMetrics.hasMetrics()) {
            return 0;
        }

        double estimate = 0.0;
        if (squareMetrics.discussing.signum() > 0) {
            estimate += Math.min(Math.log10(squareMetrics.discussing.doubleValue() + 1.0) * 4.0, 15.0);
        }
        if (squareMetrics.views.signum() > 0) {
            estimate += Math.min(Math.log10(squareMetrics.views.doubleValue() + 1.0) * 2.0, 8.0);
        }
        return Math.max(0, (int) Math.round(estimate));
    }

    private HttpHeaders buildHtmlHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.USER_AGENT, USER_AGENT);
        headers.set(HttpHeaders.ACCEPT_LANGUAGE, "en-US,en;q=0.9");
        headers.setAccept(List.of(MediaType.TEXT_HTML, MediaType.ALL));
        return headers;
    }

    private HttpHeaders buildJsonHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.USER_AGENT, USER_AGENT);
        headers.set(HttpHeaders.ACCEPT_LANGUAGE, "en-US,en;q=0.9");
        headers.setAccept(List.of(MediaType.APPLICATION_JSON, MediaType.ALL));
        return headers;
    }

    private HttpHeaders buildRedditHeaders() {
        HttpHeaders headers = buildJsonHeaders();
        headers.set(HttpHeaders.USER_AGENT, REDDIT_USER_AGENT);
        return headers;
    }

    private String buildHashtagUrl(String tag) {
        return HASHTAG_URL_TEMPLATE.formatted(UriUtils.encodePathSegment(tag, StandardCharsets.UTF_8));
    }

    private List<String> buildCandidateTags(String symbol) {
        String normalizedSymbol = symbol.trim().toLowerCase(Locale.ROOT);
        Set<String> candidates = new LinkedHashSet<>();
        candidates.add(normalizedSymbol);

        String baseAsset = extractBaseAsset(symbol).toLowerCase(Locale.ROOT);
        if (!baseAsset.equals(normalizedSymbol)) {
            candidates.add(baseAsset);
        }
        return new ArrayList<>(candidates);
    }

    private String extractBaseAsset(String symbol) {
        String normalized = symbol.trim().toUpperCase(Locale.ROOT);
        for (String quoteAsset : QUOTE_ASSETS) {
            if (normalized.endsWith(quoteAsset) && normalized.length() > quoteAsset.length()) {
                return normalized.substring(0, normalized.length() - quoteAsset.length());
            }
        }
        return normalized;
    }

    private boolean containsTicker(JSONArray currencies, String baseAsset) {
        if (currencies == null || currencies.isEmpty()) {
            return false;
        }
        for (int i = 0; i < currencies.size(); i++) {
            JSONObject currency = currencies.getJSONObject(i);
            String code = currency == null ? null : currency.getString("code");
            if (baseAsset.equalsIgnoreCase(code)) {
                return true;
            }
        }
        return false;
    }

    private Pattern buildTickerMentionPattern(String baseAsset) {
        return Pattern.compile("(?:\\$|\\b)" + Pattern.quote(baseAsset.toUpperCase(Locale.ROOT)) + "\\b");
    }

    private String extractMetric(Pattern pattern, String text) {
        Matcher matcher = pattern.matcher(text);
        return matcher.find() ? matcher.group(1).trim() : null;
    }

    private BigDecimal parseMetric(String value) {
        if (!StringUtils.hasText(value)) {
            return BigDecimal.ZERO;
        }

        String normalized = value.trim().toUpperCase(Locale.ROOT).replace(",", "");
        BigDecimal multiplier = BigDecimal.ONE;
        if (normalized.endsWith("M")) {
            multiplier = new BigDecimal("1000000");
            normalized = normalized.substring(0, normalized.length() - 1);
        } else if (normalized.endsWith("K")) {
            multiplier = new BigDecimal("1000");
            normalized = normalized.substring(0, normalized.length() - 1);
        }

        try {
            return new BigDecimal(normalized.trim()).multiply(multiplier);
        } catch (Exception e) {
            return BigDecimal.ZERO;
        }
    }

    private double parseDouble(String value) {
        if (!StringUtils.hasText(value)) {
            return 0.0;
        }
        try {
            return Double.parseDouble(value);
        } catch (Exception e) {
            return 0.0;
        }
    }

    private double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private static final class CachedSentiment {
        private final SentimentSnapshot snapshot;
        private final long cachedAt;

        private CachedSentiment(SentimentSnapshot snapshot, long cachedAt) {
            this.snapshot = snapshot;
            this.cachedAt = cachedAt;
        }
    }

    private static final class SentimentSnapshot {
        private final String symbol;
        private final double compositeScore;
        private final String squareTag;
        private final String squareViewsText;
        private final String squareDiscussingText;
        private final int socialMentions;
        private final int bullishVotes;
        private final int bearishVotes;
        private final boolean usingRedditFallback;
        private final Integer coingeckoTrendingRank;
        private final Integer gainersRank;
        private final Integer fearGreedValue;
        private final String fearGreedLabel;

        private SentimentSnapshot(String symbol,
                                  double compositeScore,
                                  String squareTag,
                                  String squareViewsText,
                                  String squareDiscussingText,
                                  int socialMentions,
                                  int bullishVotes,
                                  int bearishVotes,
                                  boolean usingRedditFallback,
                                  Integer coingeckoTrendingRank,
                                  Integer gainersRank,
                                  Integer fearGreedValue,
                                  String fearGreedLabel) {
            this.symbol = symbol;
            this.compositeScore = compositeScore;
            this.squareTag = squareTag;
            this.squareViewsText = squareViewsText;
            this.squareDiscussingText = squareDiscussingText;
            this.socialMentions = socialMentions;
            this.bullishVotes = bullishVotes;
            this.bearishVotes = bearishVotes;
            this.usingRedditFallback = usingRedditFallback;
            this.coingeckoTrendingRank = coingeckoTrendingRank;
            this.gainersRank = gainersRank;
            this.fearGreedValue = fearGreedValue;
            this.fearGreedLabel = fearGreedLabel;
        }

        private boolean hasAnySource() {
            return StringUtils.hasText(squareViewsText)
                    || StringUtils.hasText(squareDiscussingText)
                    || socialMentions > 0
                    || coingeckoTrendingRank != null
                    || gainersRank != null
                    || fearGreedValue != null;
        }

        private String scoreLabel() {
            long roundedScore = Math.round(compositeScore);
            return roundedScore + "/100（" + levelLabel() + "）";
        }

        private String levelLabel() {
            if (compositeScore >= 60.0) {
                return "高";
            }
            if (compositeScore >= 30.0) {
                return "中";
            }
            return "低";
        }
    }

    private static final class SquareMetrics {
        private final String tag;
        private final String viewsText;
        private final String discussingText;
        private final BigDecimal views;
        private final BigDecimal discussing;

        private SquareMetrics(String tag,
                              String viewsText,
                              String discussingText,
                              BigDecimal views,
                              BigDecimal discussing) {
            this.tag = tag;
            this.viewsText = viewsText;
            this.discussingText = discussingText;
            this.views = views == null ? BigDecimal.ZERO : views;
            this.discussing = discussing == null ? BigDecimal.ZERO : discussing;
        }

        private static SquareMetrics unavailable() {
            return new SquareMetrics(null, null, null, BigDecimal.ZERO, BigDecimal.ZERO);
        }

        private boolean hasMetrics() {
            return views.signum() > 0 || discussing.signum() > 0;
        }
    }

    private static final class SocialMetrics {
        private final int mentions;
        private final int bullishVotes;
        private final int bearishVotes;

        private SocialMetrics(int mentions, int bullishVotes, int bearishVotes) {
            this.mentions = mentions;
            this.bullishVotes = bullishVotes;
            this.bearishVotes = bearishVotes;
        }

        private static SocialMetrics empty() {
            return new SocialMetrics(0, 0, 0);
        }
    }

    private static final class FearGreedMetrics {
        private final Integer value;
        private final String label;

        private FearGreedMetrics(Integer value, String label) {
            this.value = value;
            this.label = label;
        }

        private static FearGreedMetrics unavailable() {
            return new FearGreedMetrics(null, null);
        }
    }
}
