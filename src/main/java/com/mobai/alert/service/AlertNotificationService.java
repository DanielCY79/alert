package com.mobai.alert.service;

import com.mobai.alert.api.EnterpriseWechatApi;
import com.mobai.alert.dto.BinanceKlineDTO;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

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
    private static final DateTimeFormatter MESSAGE_TIME_FORMATTER = DateTimeFormatter.ofPattern("MM-dd HH:mm:ss");

    private final EnterpriseWechatApi enterpriseWechatApi;
    private final Map<String, Long> sentRecords = new ConcurrentHashMap<>();

    public AlertNotificationService(EnterpriseWechatApi enterpriseWechatApi) {
        this.enterpriseWechatApi = enterpriseWechatApi;
    }

    /**
     * 统一负责消息冷却和企业微信发送。
     */
    public void send(AlertSignal signal) {
        String recordKey = signal.getKline().getSymbol() + signal.getType();
        if (!allowSend(recordKey)) {
            System.out.println("[拒绝] " + recordKey + " 冷却时间内已发送过通知");
            return;
        }
        enterpriseWechatApi.sendGroupMessage(buildMessage(signal));
    }

    @Scheduled(fixedDelay = 5 * 60 * 1000L)
    public void cleanupExpiredRecords() {
        long currentTime = System.currentTimeMillis();
        sentRecords.entrySet().removeIf(entry -> currentTime - entry.getValue() > COOLDOWN_PERIOD);
    }

    private boolean allowSend(String recordKey) {
        long currentTime = System.currentTimeMillis();
        Long lastSentTime = sentRecords.get(recordKey);
        if (lastSentTime == null || currentTime - lastSentTime > COOLDOWN_PERIOD) {
            sentRecords.put(recordKey, currentTime);
            return true;
        }
        return false;
    }

    private String buildMessage(AlertSignal signal) {
        BinanceKlineDTO kline = signal.getKline();
        BigDecimal closePrice = new BigDecimal(kline.getClose()).setScale(4, RoundingMode.HALF_DOWN);
        BigDecimal amplitude = calculateAmplitude(kline).multiply(new BigDecimal("100")).setScale(2, RoundingMode.HALF_UP);
        BigDecimal volume = convertToWan(new BigDecimal(kline.getVolume()).setScale(0, RoundingMode.HALF_DOWN));
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime klineTime = LocalDateTime.ofInstant(Instant.ofEpochMilli(kline.getEndTime()), ZoneId.systemDefault());

        StringBuilder builder = new StringBuilder();
        builder.append(signal.getTitle()).append("：**").append(kline.getSymbol()).append("**\n")
                .append(" 周期：").append(StringUtils.hasText(kline.getInterval()) ? kline.getInterval() : "1h").append("\n")
                .append(" 收盘价：").append(closePrice).append(" USDT\n")
                .append(" 振幅：").append(amplitude).append("%\n")
                .append(" 成交额：").append(volume).append(" 万USDT\n");

        if (signal.getTriggerPrice() != null) {
            builder.append(" 触发价：").append(signal.getTriggerPrice().setScale(4, RoundingMode.HALF_UP)).append(" USDT\n");
        }
        if (signal.getStopPrice() != null) {
            builder.append(" 止损价：").append(signal.getStopPrice().setScale(4, RoundingMode.HALF_UP)).append(" USDT\n");
            if (signal.getTriggerPrice() != null && signal.getTriggerPrice().compareTo(BigDecimal.ZERO) > 0) {
                BigDecimal riskPct = signal.getTriggerPrice()
                        .subtract(signal.getStopPrice())
                        .divide(signal.getTriggerPrice(), 6, RoundingMode.HALF_UP)
                        .multiply(new BigDecimal("100"))
                        .setScale(2, RoundingMode.HALF_UP);
                builder.append(" 预估风险：").append(riskPct).append("%\n");
            }
        }
        if (StringUtils.hasText(signal.getDetail())) {
            builder.append(" 策略说明：").append(signal.getDetail()).append("\n");
        }

        builder.append(" 当前时间：").append(MESSAGE_TIME_FORMATTER.format(now)).append("\n")
                .append(" K线时间：").append(MESSAGE_TIME_FORMATTER.format(klineTime)).append("\n")
                .append(" [点击查看实时K线图](https://www.binance.com/en/futures/")
                .append(kline.getSymbol())
                .append("?type=spot&layout=pro&interval=")
                .append(StringUtils.hasText(kline.getInterval()) ? kline.getInterval() : "1h")
                .append(")");
        return builder.toString();
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
