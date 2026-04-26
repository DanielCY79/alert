package com.mobai.alert.service;

import com.alibaba.fastjson.JSON;
import com.mobai.alert.api.FeishuCardTemplate;
import com.mobai.alert.api.FeishuBotApi;
import com.mobai.alert.dto.BinanceKlineDTO;
import jakarta.annotation.PostConstruct;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 告警通知服务，负责自然日冷却控制和飞书消息发送。
 */
@Service
public class AlertNotificationService {

    private static final int MAX_DAILY_SEND_COUNT_PER_SYMBOL = 3;
    private static final long HOURLY_COOLDOWN_MILLIS_PER_SYMBOL = 60 * 60 * 1000L;
    private static final DateTimeFormatter MESSAGE_TIME_FORMATTER = DateTimeFormatter.ofPattern("MM-dd HH:mm:ss");

    private final FeishuBotApi feishuBotApi;
    private final MarketSentimentService marketSentimentService;
    private final Path sentRecordFilePath = Paths.get(System.getProperty("user.dir"), "feishuHighlightRecords.json");
    private final Map<String, String> sentRecords = new ConcurrentHashMap<>();

    public AlertNotificationService(FeishuBotApi feishuBotApi,
                                    MarketSentimentService marketSentimentService) {
        this.feishuBotApi = feishuBotApi;
        this.marketSentimentService = marketSentimentService;
    }

    /**
     * 应用启动时恢复当天已发送记录，避免重启后同一自然日重复发送。
     */
    @PostConstruct
    public void initSentRecords() {
        loadSentRecords();
    }

    /**
     * 发送告警通知。
     *
     * @param signal 告警信号
     */
    public void send(AlertSignal signal) {
        String recordKey = buildRecordKey(signal);
        if (!allowSend(recordKey)) {
            System.out.println("[SKIP] " + recordKey + " has reached alert rate limit.");
            return;
        }

        if (signal.getTemplate() != null) {
            FeishuCardTemplate template = signal.getTemplate();
            feishuBotApi.sendGroupMessage(buildTitle(signal), buildBody(signal), template);
            return;
        }
        feishuBotApi.sendGroupMessage(buildTitle(signal), buildBody(signal), true);
    }

    /**
     * 定时清理跨自然日的历史发送记录。
     */
    @Scheduled(fixedDelay = 5 * 60 * 1000L)
    public synchronized void cleanupExpiredRecords() {
        LocalDate today = AppTime.today();
        sentRecords.entrySet().removeIf(entry -> isExpiredSentRecord(entry.getValue(), today));
        persistSentRecords();
    }

    /**
     * 构造同一交易对的唯一键。
     */
    private String buildRecordKey(AlertSignal signal) {
        return signal.getKline().getSymbol();
    }

    /**
     * 判断当前告警是否允许发送，同一交易对 1 小时最多 1 次，且同一自然日最多 3 次。
     */
    private synchronized boolean allowSend(String recordKey) {
        LocalDate today = AppTime.today();
        long now = System.currentTimeMillis();
        SentRecord sentRecord = parseSentRecord(sentRecords.get(recordKey));
        int sentCount = sentRecord != null && today.equals(sentRecord.date) ? sentRecord.count : 0;
        if (sentCount >= MAX_DAILY_SEND_COUNT_PER_SYMBOL) {
            return false;
        }
        if (sentRecord != null
                && today.equals(sentRecord.date)
                && sentRecord.lastSentMillis != null
                && now - sentRecord.lastSentMillis < HOURLY_COOLDOWN_MILLIS_PER_SYMBOL) {
            return false;
        }
        sentRecords.put(recordKey, formatSentRecord(today, sentCount + 1, now));
        persistSentRecords();
        return true;
    }

    /**
     * 从本地文件恢复已发送记录。
     */
    private void loadSentRecords() {
        if (!Files.exists(sentRecordFilePath)) {
            return;
        }

        try {
            String content = Files.readString(sentRecordFilePath, StandardCharsets.UTF_8);
            if (!StringUtils.hasText(content) || "{}".equals(content.trim())) {
                return;
            }

            Map<String, Object> persistedRecords = JSON.parseObject(content, Map.class);
            if (persistedRecords == null) {
                return;
            }

            LocalDate today = AppTime.today();
            sentRecords.clear();
            for (Map.Entry<String, Object> entry : persistedRecords.entrySet()) {
                String recordKey = normalizeRecordKey(entry.getKey());
                SentRecord sentRecord = parseSentRecord(String.valueOf(entry.getValue()));
                if (recordKey != null && sentRecord != null && today.equals(sentRecord.date)) {
                    SentRecord existingRecord = parseSentRecord(sentRecords.get(recordKey));
                    int count = sentRecord.count;
                    if (existingRecord != null && today.equals(existingRecord.date)) {
                        count += existingRecord.count;
                    }
                    Long lastSentMillis = latestLastSentMillis(sentRecord, existingRecord);
                    sentRecords.put(
                            recordKey,
                            formatSentRecord(today, Math.min(count, MAX_DAILY_SEND_COUNT_PER_SYMBOL), lastSentMillis)
                    );
                }
            }
        } catch (IOException e) {
            System.out.println("Failed to read sent record file: " + e.getMessage());
        } catch (Exception e) {
            System.out.println("Failed to parse sent record file: " + e.getMessage());
        }
    }

    /**
     * 判断发送记录是否已跨天过期。
     */
    private boolean isExpiredSentRecord(String sentDate, LocalDate today) {
        SentRecord sentRecord = parseSentRecord(sentDate);
        return sentRecord == null || sentRecord.date.isBefore(today);
    }

    private String normalizeRecordKey(String recordKey) {
        if (!StringUtils.hasText(recordKey)) {
            return null;
        }
        int separatorIndex = recordKey.lastIndexOf(':');
        if (separatorIndex < 0 || separatorIndex == recordKey.length() - 1) {
            return recordKey;
        }
        return recordKey.substring(separatorIndex + 1);
    }

    private SentRecord parseSentRecord(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            String[] parts = value.split(":", 3);
            LocalDate date = LocalDate.parse(parts[0]);
            int count = parts.length > 1 ? Integer.parseInt(parts[1]) : 1;
            Long lastSentMillis = parts.length > 2 ? Long.parseLong(parts[2]) : null;
            return new SentRecord(date, Math.max(1, count), lastSentMillis);
        } catch (Exception e) {
            return null;
        }
    }

    private Long latestLastSentMillis(SentRecord first, SentRecord second) {
        Long firstMillis = first == null ? null : first.lastSentMillis;
        Long secondMillis = second == null ? null : second.lastSentMillis;
        if (firstMillis == null) {
            return secondMillis;
        }
        if (secondMillis == null) {
            return firstMillis;
        }
        return Math.max(firstMillis, secondMillis);
    }

    private String formatSentRecord(LocalDate date, int count, Long lastSentMillis) {
        if (lastSentMillis == null) {
            return date + ":" + count;
        }
        return date + ":" + count + ":" + lastSentMillis;
    }

    /**
     * 持久化当天发送记录，保证重启后行为一致。
     */
    private synchronized void persistSentRecords() {
        try {
            Files.writeString(
                    sentRecordFilePath,
                    JSON.toJSONString(new HashMap<>(sentRecords)),
                    StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING
            );
        } catch (IOException e) {
            System.out.println("Failed to persist sent records: " + e.getMessage());
        }
    }

    /**
     * 构造通知标题。
     */
    private String buildTitle(AlertSignal signal) {
        return signal.getKline().getSymbol() + "：" + signal.getTitle();
    }

    /**
     * 构造通知正文。
     */
    private String buildBody(AlertSignal signal) {
        if (StringUtils.hasText(signal.getBody())) {
            return marketSentimentService.enrichBody(signal.getKline().getSymbol(), signal.getBody());
        }

        BinanceKlineDTO kline = signal.getKline();
        BigDecimal closePrice = new BigDecimal(kline.getClose()).setScale(4, RoundingMode.HALF_DOWN);
        BigDecimal amplitude = calculateAmplitude(kline).multiply(new BigDecimal("100")).setScale(2, RoundingMode.HALF_UP);
        BigDecimal volume = convertToWan(new BigDecimal(kline.getVolume()).setScale(0, RoundingMode.HALF_DOWN));
        LocalDateTime now = AppTime.now();
        LocalDateTime klineTime = AppTime.toLocalDateTime(kline.getEndTime());

        String body = "收盘价：" + closePrice + " USDT\n"
                + "振幅：" + amplitude + "%，成交额：" + volume + " 万USDT\n"
                + "当前时间：" + MESSAGE_TIME_FORMATTER.format(now)
                + "，K线时间：" + MESSAGE_TIME_FORMATTER.format(klineTime) + "\n"
                + "[实时K线图](https://www.binance.com/en/futures/" + kline.getSymbol() + "?type=spot&layout=pro&interval=1m)";
        return marketSentimentService.enrichBody(kline.getSymbol(), body);
    }

    /**
     * 计算 K 线振幅，返回小数值。
     */
    private BigDecimal calculateAmplitude(BinanceKlineDTO kline) {
        BigDecimal high = new BigDecimal(kline.getHigh());
        BigDecimal low = new BigDecimal(kline.getLow());
        return high.subtract(low).abs().divide(low, 6, RoundingMode.HALF_UP);
    }

    /**
     * 将成交额转换为“万”。
     */
    private BigDecimal convertToWan(BigDecimal amount) {
        return amount.divide(new BigDecimal("10000"), 2, RoundingMode.HALF_UP);
    }

    private static class SentRecord {

        private final LocalDate date;
        private final int count;
        private final Long lastSentMillis;

        private SentRecord(LocalDate date, int count, Long lastSentMillis) {
            this.date = date;
            this.count = count;
            this.lastSentMillis = lastSentMillis;
        }
    }
}
