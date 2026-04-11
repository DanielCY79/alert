package com.mobai.alert.service;

import com.alibaba.fastjson.JSON;
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
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 告警通知服务，负责冷却控制、标题高亮去重和飞书消息发送。
 */
@Service
public class AlertNotificationService {

    private static final long COOLDOWN_PERIOD = 24 * 60 * 60 * 1000L;
    private static final DateTimeFormatter MESSAGE_TIME_FORMATTER = DateTimeFormatter.ofPattern("MM-dd HH:mm:ss");

    private final FeishuBotApi feishuBotApi;
    private final Path highlightRecordFilePath = Paths.get(System.getProperty("user.dir"), "feishuHighlightRecords.json");
    private final Map<String, Long> sentRecords = new ConcurrentHashMap<>();
    private final Map<String, String> highlightRecords = new ConcurrentHashMap<>();

    public AlertNotificationService(FeishuBotApi feishuBotApi) {
        this.feishuBotApi = feishuBotApi;
    }

    /**
     * 应用启动时加载当天的高亮记录，避免重启后重复高亮。
     */
    @PostConstruct
    public void initHighlightRecords() {
        loadHighlightRecords();
    }

    /**
     * 发送告警通知。
     *
     * @param signal 告警信号
     */
    public void send(AlertSignal signal) {
        String recordKey = buildRecordKey(signal);
        if (!allowSend(recordKey)) {
            System.out.println("[SKIP] " + recordKey + " is still in cooldown.");
            return;
        }
        feishuBotApi.sendGroupMessage(buildTitle(signal), buildBody(signal), shouldHighlightTitle(recordKey));
    }

    /**
     * 定时清理过期冷却记录和历史高亮记录。
     */
    @Scheduled(fixedDelay = 5 * 60 * 1000L)
    public synchronized void cleanupExpiredRecords() {
        long currentTime = System.currentTimeMillis();
        LocalDate today = LocalDate.now();
        sentRecords.entrySet().removeIf(entry -> currentTime - entry.getValue() > COOLDOWN_PERIOD);
        highlightRecords.entrySet().removeIf(entry -> isExpiredHighlightRecord(entry.getValue(), today));
        persistHighlightRecords();
    }

    /**
     * 构造同一类告警的唯一键。
     */
    private String buildRecordKey(AlertSignal signal) {
        return signal.getKline().getSymbol();
    }

    /**
     * 判断当前告警是否允许发送，命中冷却时间则拒绝。
     */
    private synchronized boolean allowSend(String recordKey) {
        long currentTime = System.currentTimeMillis();
        Long lastSentTime = sentRecords.get(recordKey);
        if (lastSentTime == null || currentTime - lastSentTime > COOLDOWN_PERIOD) {
            sentRecords.put(recordKey, currentTime);
            return true;
        }
        return false;
    }

    /**
     * 同一交易对同一类型的告警每天仅第一次使用高亮标题。
     */
    private synchronized boolean shouldHighlightTitle(String recordKey) {
        String today = LocalDate.now().toString();
        String lastHighlightedDate = highlightRecords.get(recordKey);
        if (!today.equals(lastHighlightedDate)) {
            highlightRecords.put(recordKey, today);
            persistHighlightRecords();
            return true;
        }
        return false;
    }

    /**
     * 从本地文件恢复高亮记录。
     */
    private void loadHighlightRecords() {
        if (!Files.exists(highlightRecordFilePath)) {
            return;
        }

        try {
            String content = Files.readString(highlightRecordFilePath, StandardCharsets.UTF_8);
            if (!StringUtils.hasText(content) || "{}".equals(content.trim())) {
                return;
            }

            Map<String, String> persistedRecords = JSON.parseObject(content, Map.class);
            if (persistedRecords == null) {
                return;
            }

            LocalDate today = LocalDate.now();
            highlightRecords.clear();
            for (Map.Entry<String, String> entry : persistedRecords.entrySet()) {
                if (!isExpiredHighlightRecord(entry.getValue(), today)) {
                    highlightRecords.put(entry.getKey(), entry.getValue());
                }
            }
        } catch (IOException e) {
            System.out.println("Failed to read highlight record file: " + e.getMessage());
        } catch (Exception e) {
            System.out.println("Failed to parse highlight record file: " + e.getMessage());
        }
    }

    /**
     * 判断高亮记录是否已跨天过期。
     */
    private boolean isExpiredHighlightRecord(String highlightedDate, LocalDate today) {
        if (!StringUtils.hasText(highlightedDate)) {
            return true;
        }
        try {
            return LocalDate.parse(highlightedDate).isBefore(today);
        } catch (Exception e) {
            return true;
        }
    }

    /**
     * 持久化当天高亮记录，保证重启后行为一致。
     */
    private synchronized void persistHighlightRecords() {
        try {
            Files.writeString(
                    highlightRecordFilePath,
                    Objects.requireNonNull(JSON.toJSONString(new HashMap<>(highlightRecords))),
                    StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING
            );
        } catch (IOException e) {
            System.out.println("Failed to persist highlight records: " + e.getMessage());
        }
    }

    /**
     * 构造通知标题。
     */
    private String buildTitle(AlertSignal signal) {
        return signal.getTitle() + "：" + signal.getKline().getSymbol();
    }

    /**
     * 构造通知正文。
     */
    private String buildBody(AlertSignal signal) {
        BinanceKlineDTO kline = signal.getKline();
        BigDecimal closePrice = new BigDecimal(kline.getClose()).setScale(4, RoundingMode.HALF_DOWN);
        BigDecimal amplitude = calculateAmplitude(kline).multiply(new BigDecimal("100")).setScale(2, RoundingMode.HALF_UP);
        BigDecimal volume = convertToWan(new BigDecimal(kline.getVolume()).setScale(0, RoundingMode.HALF_DOWN));
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime klineTime = LocalDateTime.ofInstant(Instant.ofEpochMilli(kline.getEndTime()), ZoneId.systemDefault());

        return "收盘价：" + closePrice + " USDT\n"
                + "振幅：" + amplitude + "%\n"
                + "成交额：" + volume + " 万USDT\n"
                + "当前时间：" + MESSAGE_TIME_FORMATTER.format(now) + "\n"
                + "K线时间：" + MESSAGE_TIME_FORMATTER.format(klineTime) + "\n"
                + "[实时K线图](https://www.binance.com/en/futures/" + kline.getSymbol() + "?type=spot&layout=pro&interval=1m)";
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
}
