package com.mobai.alert.service;

import com.mobai.alert.api.FeishuCardTemplate;
import com.mobai.alert.dto.BinanceKlineDTO;
import com.mobai.alert.dto.BinanceSymbolsDetailDTO;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;

@Service
public class AlertSymbolProcessor {

    private static final int PRIMARY_SIGNAL_LOOKBACK = 5;
    private static final int MA20_SIGNAL_LOOKBACK = 20;
    private static final DateTimeFormatter MESSAGE_TIME_FORMATTER = DateTimeFormatter.ofPattern("MM-dd HH:mm:ss");
    private static final String TITLE_TWO_OF_THREE_MOMENTUM = "低均量MA20 3根K线中2根放量大振幅";
    private static final String TITLE_LOW_VOLUME_MA20 = "低均量多周期MA20放量";
    private static final String TYPE_TWO_OF_THREE_MOMENTUM = "1";
    private static final String TYPE_LOW_VOLUME_MA20 = "2";

    @Value("${monitoring.exclude.symbol:}")
    private String excludeSymbol;

    private final BinanceMarketDataService binanceMarketDataService;
    private final DailyMa20SnapshotService dailyMa20SnapshotService;
    private final AlertRuleEvaluator alertRuleEvaluator;
    private final AlertNotificationService alertNotificationService;

    public AlertSymbolProcessor(BinanceMarketDataService binanceMarketDataService,
                                DailyMa20SnapshotService dailyMa20SnapshotService,
                                AlertRuleEvaluator alertRuleEvaluator,
                                AlertNotificationService alertNotificationService) {
        this.binanceMarketDataService = binanceMarketDataService;
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
        sendPrimarySignal(symbolDTO.getSymbol(), closedKlines, latestClosedKline);
    }

    private void sendPrimarySignal(String symbol,
                                   List<BinanceKlineDTO> closedKlines,
                                   BinanceKlineDTO latestClosedKline) {
        DailyMa20Snapshot dailyMa20Snapshot = dailyMa20SnapshotService.getSnapshot(symbol);
        List<BinanceKlineDTO> recentThreeClosedKlines = takeLast(closedKlines, 3);
        BinanceKlineDTO previousClosedKline = getPreviousKline(closedKlines);
        if (sendTwoOfThreeMomentumSignal(symbol, dailyMa20Snapshot, latestClosedKline, recentThreeClosedKlines)) {
            return;
        }

        sendLowVolumeMa20Signal(symbol, latestClosedKline, previousClosedKline, dailyMa20Snapshot);
    }

    private boolean sendTwoOfThreeMomentumSignal(String symbol,
                                                 DailyMa20Snapshot dailyMa20Snapshot,
                                                 BinanceKlineDTO latestClosedKline,
                                                 List<BinanceKlineDTO> recentThreeClosedKlines) {
        if (!alertRuleEvaluator.shouldEvaluateTwoOfThreeMomentumSignal(
                dailyMa20Snapshot,
                latestClosedKline,
                recentThreeClosedKlines
        )) {
            return false;
        }

        List<BinanceKlineDTO> fifteenMinuteKlines = loadRecentClosedKlines(symbol, "15m", MA20_SIGNAL_LOOKBACK);
        List<BinanceKlineDTO> oneHourKlines = loadRecentClosedKlines(symbol, "1h", MA20_SIGNAL_LOOKBACK);
        List<BinanceKlineDTO> fourHourKlines = loadRecentClosedKlines(symbol, "4h", MA20_SIGNAL_LOOKBACK);
        TwoOfThreeMomentumSignalContext context = alertRuleEvaluator.evaluateTwoOfThreeMomentumSignal(
                dailyMa20Snapshot,
                latestClosedKline,
                recentThreeClosedKlines,
                fifteenMinuteKlines,
                oneHourKlines,
                fourHourKlines
        );
        if (context == null) {
            return false;
        }

        alertNotificationService.send(new AlertSignal(
                TITLE_TWO_OF_THREE_MOMENTUM,
                latestClosedKline,
                TYPE_TWO_OF_THREE_MOMENTUM,
                AlertCooldownCategory.LEGACY,
                buildTwoOfThreeMomentumBody(symbol, context),
                null
        ));
        return true;
    }

    private boolean sendLowVolumeMa20Signal(String symbol,
                                            BinanceKlineDTO latestClosedKline,
                                            BinanceKlineDTO previousClosedKline,
                                            DailyMa20Snapshot dailyMa20Snapshot) {
        if (!alertRuleEvaluator.shouldEvaluateLowVolumeMa20Signal(
                dailyMa20Snapshot,
                latestClosedKline,
                previousClosedKline
        )) {
            return false;
        }

        List<BinanceKlineDTO> fifteenMinuteKlines = loadRecentClosedKlines(symbol, "15m", MA20_SIGNAL_LOOKBACK);
        List<BinanceKlineDTO> oneHourKlines = loadRecentClosedKlines(symbol, "1h", MA20_SIGNAL_LOOKBACK);
        List<BinanceKlineDTO> fourHourKlines = loadRecentClosedKlines(symbol, "4h", MA20_SIGNAL_LOOKBACK);
        LowVolumeMa20SignalContext context = alertRuleEvaluator.evaluateLowVolumeMa20Signal(
                dailyMa20Snapshot,
                latestClosedKline,
                previousClosedKline,
                fifteenMinuteKlines,
                oneHourKlines,
                fourHourKlines
        );
        if (context == null) {
            return false;
        }

        alertNotificationService.send(new AlertSignal(
                TITLE_LOW_VOLUME_MA20,
                context.getLatestOneMinuteKline(),
                TYPE_LOW_VOLUME_MA20,
                AlertCooldownCategory.LOW_VOLUME_MA20,
                buildLowVolumeMa20Body(symbol, context),
                FeishuCardTemplate.YELLOW
        ));
        return true;
    }

    private boolean shouldSkip(BinanceSymbolsDetailDTO symbolDTO) {
        if (!AlertSymbolFilter.isMonitorCandidate(symbolDTO)) {
            return true;
        }
        return isExcluded(symbolDTO.getSymbol());
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

    private List<BinanceKlineDTO> loadRecentClosedKlines(String symbol, int limit) {
        return binanceMarketDataService.loadRecentClosedKlines(symbol, "1m", limit);
    }

    private List<BinanceKlineDTO> loadRecentClosedKlines(String symbol, String interval, int limit) {
        return binanceMarketDataService.loadRecentClosedKlines(symbol, interval, limit);
    }

    private String buildTwoOfThreeMomentumBody(String symbol, TwoOfThreeMomentumSignalContext context) {
        return "当前价格：" + formatDecimal(context.getCurrentPrice(), 4) + " USDT\n"
                + "5日平均成交额：" + formatWan(context.getAverageVolume5d()) + " 万USDT\n"
                + "日K MA20：" + formatDecimal(context.getDailyMa20(), 4) + "\n"
                + "4h MA20：" + formatDecimal(context.getFourHourMa20(), 4) + "\n"
                + "1h MA20：" + formatDecimal(context.getOneHourMa20(), 4) + "\n"
                + "15m MA20：" + formatDecimal(context.getFifteenMinuteMa20(), 4) + "\n"
                + "近3根1m命中：" + context.getMatchedOneMinuteCount() + "/3"
                + "（成交额>8万USDT，振幅>10%）\n"
                + "1m时间：" + formatTime(context.getLatestOneMinuteKline().getEndTime()) + "\n"
                + "[实时K线图](https://www.binance.com/en/futures/"
                + symbol
                + "?type=spot&layout=pro&interval=1m)";
    }

    private String buildLowVolumeMa20Body(String symbol, LowVolumeMa20SignalContext context) {
        return "当前价格：" + formatDecimal(context.getCurrentPrice(), 4) + " USDT\n"
                + "7日平均成交额：" + formatWan(context.getAverageVolume7d()) + " 万USDT\n"
                + "日K MA20：" + formatDecimal(context.getDailyMa20(), 4) + "\n"
                + "4h MA20：" + formatDecimal(context.getFourHourMa20(), 4) + "\n"
                + "1h MA20：" + formatDecimal(context.getOneHourMa20(), 4) + "\n"
                + "15m MA20：" + formatDecimal(context.getFifteenMinuteMa20(), 4) + "\n"
                + "1m成交额：" + formatWan(context.getOneMinuteVolume()) + " 万USDT\n"
                + "前1根1m成交额：" + formatWan(context.getPreviousOneMinuteVolume()) + " 万USDT\n"
                + "1m振幅：" + formatPercent(context.getOneMinuteAmplitude()) + "\n"
                + "1m时间：" + formatTime(context.getLatestOneMinuteKline().getEndTime()) + "\n"
                + "[实时K线图](https://www.binance.com/en/futures/"
                + symbol
                + "?type=spot&layout=pro&interval=1m)";
    }

    private List<BinanceKlineDTO> takeLast(List<BinanceKlineDTO> klines, int count) {
        if (CollectionUtils.isEmpty(klines) || count <= 0) {
            return List.of();
        }
        int fromIndex = Math.max(0, klines.size() - count);
        return klines.subList(fromIndex, klines.size());
    }

    private BinanceKlineDTO getPreviousKline(List<BinanceKlineDTO> klines) {
        if (CollectionUtils.isEmpty(klines) || klines.size() < 2) {
            return null;
        }
        return klines.get(klines.size() - 2);
    }

    private String formatPercent(BigDecimal value) {
        return value.multiply(new BigDecimal("100")).setScale(2, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString() + "%";
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
