package com.mobai.alert.service;

import com.mobai.alert.dto.BinanceKlineDTO;
import com.mobai.alert.dto.BinanceSymbolsDetailDTO;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;

@Service
public class AlertSymbolProcessor {

    private static final long BACK_COOLDOWN_PERIOD = 2 * 60 * 60 * 1000L;

    @Value("${monitoring.exclude.symbol:}")
    private String excludeSymbol;

    private final BinanceMarketDataService binanceMarketDataService;
    private final AlertRuleEvaluator alertRuleEvaluator;
    private final AlertNotificationService alertNotificationService;
    private final Map<String, Long> backRecords = new ConcurrentHashMap<>();

    public AlertSymbolProcessor(BinanceMarketDataService binanceMarketDataService,
                                AlertRuleEvaluator alertRuleEvaluator,
                                AlertNotificationService alertNotificationService) {
        this.binanceMarketDataService = binanceMarketDataService;
        this.alertRuleEvaluator = alertRuleEvaluator;
        this.alertNotificationService = alertNotificationService;
    }

    /**
     * Process a single symbol by loading recent closed klines, evaluating rules, and sending alerts.
     */
    public void process(BinanceSymbolsDetailDTO symbolDTO) {
        if (shouldSkip(symbolDTO)) {
            return;
        }

        List<BinanceKlineDTO> closedKlines = loadRecentClosedKlines(symbolDTO.getSymbol());
        if (CollectionUtils.isEmpty(closedKlines)) {
            return;
        }

        BinanceKlineDTO latestClosedKline = closedKlines.get(closedKlines.size() - 1);

        List<BinanceKlineDTO> recentThreeClosedKlines = takeLast(closedKlines, 3);
        if (recentThreeClosedKlines.size() == 3
                && allMatch(recentThreeClosedKlines, alertRuleEvaluator::isContinuousThreeMatch)) {
            alertNotificationService.send(new AlertSignal("\u8FDE\u7EED 3 \u6839K\u7EBF\u62C9\u5347", latestClosedKline, "1"));
            backRecords.put(symbolDTO.getSymbol(), System.currentTimeMillis());
        }

        // Backtrack signals only make sense after a prior three-candle rise signal.
        if (backRecords.containsKey(symbolDTO.getSymbol())
                && alertRuleEvaluator.isBacktrackMatch(latestClosedKline)) {
            alertNotificationService.send(new AlertSignal("\u56DE\u8E29\u4EA4\u6613\u5BF9", latestClosedKline, "2"));
        }

        List<BinanceKlineDTO> recentTwoClosedKlines = takeLast(closedKlines, 2);
        if (recentTwoClosedKlines.size() == 2
                && allMatch(recentTwoClosedKlines, alertRuleEvaluator::isContinuousTwoMatch)) {
            alertNotificationService.send(new AlertSignal("\u8FDE\u7EED 2 \u6839K\u7EBF\u62C9\u5347", latestClosedKline, "3"));
        }
    }

    @Scheduled(fixedDelay = 5 * 60 * 1000L)
    public void cleanupExpiredBackRecords() {
        long currentTime = System.currentTimeMillis();
        backRecords.entrySet().removeIf(entry -> currentTime - entry.getValue() > BACK_COOLDOWN_PERIOD);
    }

    private boolean shouldSkip(BinanceSymbolsDetailDTO symbolDTO) {
        // Only process tradable USDT symbols that are not manually excluded.
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

    private List<BinanceKlineDTO> loadRecentClosedKlines(String symbol) {
        return binanceMarketDataService.loadRecentClosedKlines(symbol, "1m", 5);
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
}
