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

/**
 * 单个交易对处理器，负责读取最近 K 线并根据规则生成告警。
 */
@Service
public class AlertSymbolProcessor {

    private static final long BACK_COOLDOWN_PERIOD = 2 * 60 * 60 * 1000L;
    private static final String TITLE_CONTINUOUS_THREE = "连续 3 根K线拉升";
    private static final String TITLE_BACKTRACK = "回踩交易对";
    private static final String TITLE_CONTINUOUS_TWO = "连续 2 根K线拉升";
    private static final String TITLE_TWO_OF_THREE = "3根K线中2根满足规则";

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
     * 处理单个交易对，加载最近已收盘 K 线并执行告警判定。
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
            alertNotificationService.send(new AlertSignal(TITLE_CONTINUOUS_THREE, latestClosedKline, "1"));
            backRecords.put(symbolDTO.getSymbol(), System.currentTimeMillis());
        }

        if (recentThreeClosedKlines.size() == 3
                && countMatches(recentThreeClosedKlines, alertRuleEvaluator::isContinuousThreeMatch) == 2) {
            alertNotificationService.send(new AlertSignal(TITLE_TWO_OF_THREE, latestClosedKline, "4"));
        }

        // 回踩信号只有在此前出现过三连涨的前提下才有意义。
        if (backRecords.containsKey(symbolDTO.getSymbol())
                && alertRuleEvaluator.isBacktrackMatch(latestClosedKline)) {
            alertNotificationService.send(new AlertSignal(TITLE_BACKTRACK, latestClosedKline, "2"));
        }

        List<BinanceKlineDTO> recentTwoClosedKlines = takeLast(closedKlines, 2);
        if (recentTwoClosedKlines.size() == 2
                && allMatch(recentTwoClosedKlines, alertRuleEvaluator::isContinuousTwoMatch)) {
            alertNotificationService.send(new AlertSignal(TITLE_CONTINUOUS_TWO, latestClosedKline, "3"));
        }
    }

    /**
     * 定时清理回踩判断使用的历史记录。
     */
    @Scheduled(fixedDelay = 5 * 60 * 1000L)
    public void cleanupExpiredBackRecords() {
        long currentTime = System.currentTimeMillis();
        backRecords.entrySet().removeIf(entry -> currentTime - entry.getValue() > BACK_COOLDOWN_PERIOD);
    }

    /**
     * 判断当前交易对是否应当跳过处理。
     */
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

    /**
     * 判断交易对是否在手工排除名单中。
     */
    private boolean isExcluded(String symbol) {
        if (!StringUtils.hasText(excludeSymbol)) {
            return false;
        }
        return Arrays.stream(excludeSymbol.split(","))
                .map(String::trim)
                .filter(StringUtils::hasText)
                .anyMatch(symbol::equals);
    }

    /**
     * 加载最近已收盘的 1 分钟 K 线。
     */
    private List<BinanceKlineDTO> loadRecentClosedKlines(String symbol) {
        return binanceMarketDataService.loadRecentClosedKlines(symbol, "1m", 5);
    }

    /**
     * 取列表最后 N 条数据。
     */
    private List<BinanceKlineDTO> takeLast(List<BinanceKlineDTO> klines, int count) {
        if (CollectionUtils.isEmpty(klines) || count <= 0) {
            return List.of();
        }
        int fromIndex = Math.max(0, klines.size() - count);
        return klines.subList(fromIndex, klines.size());
    }

    /**
     * 判断列表中的每一条 K 线都满足指定条件。
     */
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

    /**
     * 统计列表中满足条件的 K 线数量。
     */
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
}
