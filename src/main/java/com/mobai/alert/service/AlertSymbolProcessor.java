package com.mobai.alert.service;

import com.mobai.alert.api.FeishuCardTemplate;
import com.mobai.alert.dto.BinanceKlineDTO;
import com.mobai.alert.dto.BinanceSymbolsDetailDTO;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;

@Service
public class AlertSymbolProcessor {

    private static final long BACK_COOLDOWN_PERIOD = 2 * 60 * 60 * 1000L;
    private static final int PRIMARY_SIGNAL_LOOKBACK = 5;
    private static final BigDecimal MA20_VOLUME_SPIKE_THRESHOLD = new BigDecimal("3");
    private static final DateTimeFormatter MESSAGE_TIME_FORMATTER = DateTimeFormatter.ofPattern("MM-dd HH:mm:ss");
    private static final String TITLE_CONTINUOUS_THREE = "连续 3 根K线拉升";
    private static final String TITLE_BACKTRACK = "回踩交易对";
    private static final String TITLE_CONTINUOUS_TWO = "连续 2 根K线拉升";
    private static final String TITLE_TWO_OF_THREE = "3根K线中2根满足规则";
    private static final String TITLE_DAILY_MA20_VOLUME_SPIKE = "日K站上MA20且1m成交额放大";
    private static final String TYPE_DAILY_MA20_VOLUME_SPIKE = "5";

    @Value("${monitoring.exclude.symbol:}")
    private String excludeSymbol;

    private final BinanceMarketDataService binanceMarketDataService;
    private final BinanceTickerStatsService binanceTickerStatsService;
    private final DailyMa20SnapshotService dailyMa20SnapshotService;
    private final AlertRuleEvaluator alertRuleEvaluator;
    private final AlertNotificationService alertNotificationService;
    private final Map<String, Long> backRecords = new ConcurrentHashMap<>();

    public AlertSymbolProcessor(BinanceMarketDataService binanceMarketDataService,
                                BinanceTickerStatsService binanceTickerStatsService,
                                DailyMa20SnapshotService dailyMa20SnapshotService,
                                AlertRuleEvaluator alertRuleEvaluator,
                                AlertNotificationService alertNotificationService) {
        this.binanceMarketDataService = binanceMarketDataService;
        this.binanceTickerStatsService = binanceTickerStatsService;
        this.dailyMa20SnapshotService = dailyMa20SnapshotService;
        this.alertRuleEvaluator = alertRuleEvaluator;
        this.alertNotificationService = alertNotificationService;
    }

    public void process(BinanceSymbolsDetailDTO symbolDTO) {
        if (shouldSkip(symbolDTO)) {
            return;
        }

        List<BinanceKlineDTO> closedKlines = loadRecentClosedKlines(symbolDTO.getSymbol(), PRIMARY_SIGNAL_LOOKBACK);
        if (CollectionUtils.isEmpty(closedKlines)) {
            return;
        }

        BinanceKlineDTO latestClosedKline = closedKlines.get(closedKlines.size() - 1);

        sendPrimaryMomentumSignal(symbolDTO.getSymbol(), closedKlines, latestClosedKline);

        if (backRecords.containsKey(symbolDTO.getSymbol())
                && alertRuleEvaluator.isBacktrackMatch(latestClosedKline)) {
            alertNotificationService.send(new AlertSignal(TITLE_BACKTRACK, latestClosedKline, "2"));
        }

        sendDailyMa20VolumeSpikeSignal(symbolDTO.getSymbol(), latestClosedKline);
    }

    private void sendPrimaryMomentumSignal(String symbol,
                                           List<BinanceKlineDTO> closedKlines,
                                           BinanceKlineDTO latestClosedKline) {
        List<BinanceKlineDTO> recentThreeClosedKlines = takeLast(closedKlines, 3);
        if (recentThreeClosedKlines.size() == 3
                && allMatch(recentThreeClosedKlines, alertRuleEvaluator::isContinuousThreeMatch)) {
            alertNotificationService.send(new AlertSignal(TITLE_CONTINUOUS_THREE, latestClosedKline, "1"));
            backRecords.put(symbol, System.currentTimeMillis());
            return;
        }

        if (recentThreeClosedKlines.size() == 3
                && countMatches(recentThreeClosedKlines, alertRuleEvaluator::isContinuousThreeMatch) == 2) {
            alertNotificationService.send(new AlertSignal(TITLE_TWO_OF_THREE, latestClosedKline, "4"));
            return;
        }

        List<BinanceKlineDTO> recentTwoClosedKlines = takeLast(closedKlines, 2);
        if (recentTwoClosedKlines.size() == 2
                && allMatch(recentTwoClosedKlines, alertRuleEvaluator::isContinuousTwoMatch)) {
            alertNotificationService.send(new AlertSignal(TITLE_CONTINUOUS_TWO, latestClosedKline, "3"));
        }
    }

    @Scheduled(fixedDelay = 5 * 60 * 1000L)
    public void cleanupExpiredBackRecords() {
        long currentTime = System.currentTimeMillis();
        backRecords.entrySet().removeIf(entry -> currentTime - entry.getValue() > BACK_COOLDOWN_PERIOD);
    }

    private boolean shouldSkip(BinanceSymbolsDetailDTO symbolDTO) {
        String symbol = symbolDTO.getSymbol();
        if (!StringUtils.hasText(symbol) || !symbol.contains("USDT")) {
            return true;
        }
        if (isExcluded(symbol)) {
            return true;
        }
        return !Objects.equals(symbolDTO.getStatus(), "TRADING");
    }

    private boolean isExcluded(String symbol) {
        if (!StringUtils.hasText(excludeSymbol)) {
            return false;
        }
        return Arrays.stream(excludeSymbol.split(","))
                .map(String::trim)
                .filter(StringUtils::hasText)
                .anyMatch(symbol::equals);
    }

    private void sendDailyMa20VolumeSpikeSignal(String symbol, BinanceKlineDTO latestOneMinuteKline) {
        if (latestOneMinuteKline == null) {
            return;
        }

        BigDecimal averageOneMinuteVolume = binanceTickerStatsService.getAverageOneMinuteVolume(symbol);
        if (averageOneMinuteVolume == null || averageOneMinuteVolume.signum() <= 0) {
            return;
        }

        BigDecimal currentOneMinuteVolume = new BigDecimal(latestOneMinuteKline.getVolume());
        if (currentOneMinuteVolume.compareTo(averageOneMinuteVolume.multiply(MA20_VOLUME_SPIKE_THRESHOLD)) < 0) {
            return;
        }

        DailyMa20Snapshot dailyMa20Snapshot = dailyMa20SnapshotService.getSnapshot(symbol);
        if (dailyMa20Snapshot == null) {
            return;
        }

        Ma20VolumeSpikeContext context = alertRuleEvaluator.evaluateMa20VolumeSpike(
                dailyMa20Snapshot,
                latestOneMinuteKline,
                averageOneMinuteVolume
        );
        if (context == null) {
            return;
        }

        alertNotificationService.send(new AlertSignal(
                TITLE_DAILY_MA20_VOLUME_SPIKE,
                context.getLatestOneMinuteKline(),
                TYPE_DAILY_MA20_VOLUME_SPIKE,
                AlertCooldownCategory.DAILY_MA20_VOLUME_SPIKE,
                buildDailyMa20VolumeSpikeBody(symbol, context),
                FeishuCardTemplate.PURPLE
        ));
    }

    private List<BinanceKlineDTO> loadRecentClosedKlines(String symbol, int limit) {
        return binanceMarketDataService.loadRecentClosedKlines(symbol, "1m", limit);
    }

    private String buildDailyMa20VolumeSpikeBody(String symbol, Ma20VolumeSpikeContext context) {
        return "日K收盘价：" + formatPrice(context.getLatestDailyKline().getClose()) + " USDT\n"
                + "MA20：" + formatDecimal(context.getMa20(), 4) + "\n"
                + "当前1m成交额：" + formatWan(context.getCurrentOneMinuteVolume()) + " 万USDT\n"
                + "过去24小时1m平均成交额：" + formatWan(context.getAverageOneMinuteVolume()) + " 万USDT\n"
                + "放量倍数：" + formatDecimal(context.getVolumeRatio(), 2) + "x\n"
                + "1m时间：" + formatTime(context.getLatestOneMinuteKline().getEndTime()) + "\n"
                + "日K时间：" + formatTime(context.getLatestDailyKline().getEndTime()) + "\n"
                + "[查看 Binance 日K图](https://www.binance.com/en/futures/"
                + symbol
                + "?type=spot&layout=pro&interval=1d)";
    }

    private List<BinanceKlineDTO> takeLast(List<BinanceKlineDTO> klines, int count) {
        if (CollectionUtils.isEmpty(klines) || count <= 0) {
            return List.of();
        }
        int fromIndex = Math.max(0, klines.size() - count);
        return klines.subList(fromIndex, klines.size());
    }

    private boolean allMatch(List<BinanceKlineDTO> klines, Predicate<BinanceKlineDTO> predicate) {
        if (CollectionUtils.isEmpty(klines)) {
            return false;
        }
        for (BinanceKlineDTO kline : klines) {
            if (!predicate.test(kline)) {
                return false;
            }
        }
        return true;
    }

    private int countMatches(List<BinanceKlineDTO> klines, Predicate<BinanceKlineDTO> predicate) {
        if (CollectionUtils.isEmpty(klines)) {
            return 0;
        }
        int count = 0;
        for (BinanceKlineDTO kline : klines) {
            if (predicate.test(kline)) {
                count++;
            }
        }
        return count;
    }

    private String formatPrice(String value) {
        return new BigDecimal(value).setScale(4, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString();
    }

    private String formatDecimal(BigDecimal value, int scale) {
        return value.setScale(scale, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString();
    }

    private String formatWan(BigDecimal value) {
        return value.divide(new BigDecimal("10000"), 2, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString();
    }

    private String formatTime(Long timestamp) {
        return MESSAGE_TIME_FORMATTER.format(AppTime.toLocalDateTime(timestamp));
    }
}
