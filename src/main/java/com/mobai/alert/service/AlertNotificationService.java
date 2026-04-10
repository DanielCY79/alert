package com.mobai.alert.service;

import com.mobai.alert.api.FeishuBotApi;
import com.mobai.alert.dto.BinanceKlineDTO;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class AlertNotificationService {

    private static final long COOLDOWN_PERIOD = 2 * 60 * 60 * 1000L;
    private static final long HIGHLIGHT_PERIOD = 24 * 60 * 60 * 1000L;
    private static final DateTimeFormatter MESSAGE_TIME_FORMATTER = DateTimeFormatter.ofPattern("MM-dd HH:mm:ss");

    private final FeishuBotApi feishuBotApi;
    private final Map<String, Long> sentRecords = new ConcurrentHashMap<>();
    private final Map<String, Long> highlightRecords = new ConcurrentHashMap<>();

    public AlertNotificationService(FeishuBotApi feishuBotApi) {
        this.feishuBotApi = feishuBotApi;
    }

    public void send(AlertSignal signal) {
        String recordKey = buildRecordKey(signal);
        if (!allowSend(recordKey)) {
            System.out.println("[拒绝] " + recordKey + " 冷却时间内已发送过通知");
            return;
        }
        feishuBotApi.sendGroupMessage(buildTitle(signal), buildBody(signal), shouldHighlightTitle(recordKey));
    }

    @Scheduled(fixedDelay = 5 * 60 * 1000L)
    public synchronized void cleanupExpiredRecords() {
        long currentTime = System.currentTimeMillis();
        sentRecords.entrySet().removeIf(entry -> currentTime - entry.getValue() > COOLDOWN_PERIOD);
        highlightRecords.entrySet().removeIf(entry -> currentTime - entry.getValue() > HIGHLIGHT_PERIOD);
    }

    private String buildRecordKey(AlertSignal signal) {
        return signal.getKline().getSymbol() + signal.getType();
    }

    private synchronized boolean allowSend(String recordKey) {
        long currentTime = System.currentTimeMillis();
        Long lastSentTime = sentRecords.get(recordKey);
        if (lastSentTime == null || currentTime - lastSentTime > COOLDOWN_PERIOD) {
            sentRecords.put(recordKey, currentTime);
            return true;
        }
        return false;
    }

    private synchronized boolean shouldHighlightTitle(String recordKey) {
        long currentTime = System.currentTimeMillis();
        Long lastHighlightedTime = highlightRecords.get(recordKey);
        if (lastHighlightedTime == null || currentTime - lastHighlightedTime > HIGHLIGHT_PERIOD) {
            highlightRecords.put(recordKey, currentTime);
            return true;
        }
        return false;
    }

    private String buildTitle(AlertSignal signal) {
        return signal.getTitle() + "：" + signal.getKline().getSymbol();
    }

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

    private BigDecimal calculateAmplitude(BinanceKlineDTO kline) {
        BigDecimal high = new BigDecimal(kline.getHigh());
        BigDecimal low = new BigDecimal(kline.getLow());
        return high.subtract(low).abs().divide(low, 6, RoundingMode.HALF_UP);
    }

    private BigDecimal convertToWan(BigDecimal amount) {
        return amount.divide(new BigDecimal("10000"), 2, RoundingMode.HALF_UP);
    }
}
