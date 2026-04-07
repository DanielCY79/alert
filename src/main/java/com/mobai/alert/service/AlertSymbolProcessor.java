package com.mobai.alert.service;

import com.mobai.alert.api.BinanceApi;
import com.mobai.alert.dto.BinanceKlineDTO;
import com.mobai.alert.dto.BinanceSymbolsDetailDTO;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class AlertSymbolProcessor {

    private static final String SIGNAL_RISK_EXIT = "risk-exit";

    @Value("${monitoring.exclude.symbol:}")
    private String excludeSymbol;

    @Value("${monitoring.strategy.kline.interval:1h}")
    private String strategyInterval;

    @Value("${monitoring.strategy.kline.limit:80}")
    private int strategyKlineLimit;

    @Value("${monitoring.strategy.track.hours:48}")
    private long trackHours;

    private final BinanceApi binanceApi;
    private final AlertRuleEvaluator alertRuleEvaluator;
    private final AlertNotificationService alertNotificationService;
    private final Map<String, BreakoutRecord> breakoutRecords = new ConcurrentHashMap<>();

    public AlertSymbolProcessor(BinanceApi binanceApi,
                                AlertRuleEvaluator alertRuleEvaluator,
                                AlertNotificationService alertNotificationService) {
        this.binanceApi = binanceApi;
        this.alertRuleEvaluator = alertRuleEvaluator;
        this.alertNotificationService = alertNotificationService;
    }

    /**
     * 单个交易对处理入口，按《股票魔法师》的思路扫描趋势、收缩和突破。
     */
    public void process(BinanceSymbolsDetailDTO symbolDTO) {
        if (shouldSkip(symbolDTO)) {
            return;
        }

        List<BinanceKlineDTO> klines = loadRecentKlines(symbolDTO.getSymbol());
        if (CollectionUtils.isEmpty(klines) || klines.size() < 3) {
            return;
        }

        BinanceKlineDTO latestClosedKline = klines.get(klines.size() - 2);
        processRiskExit(symbolDTO.getSymbol(), latestClosedKline);

        alertRuleEvaluator.evaluateStageTwoBreakout(klines).ifPresent(signal -> {
            alertNotificationService.send(signal);
            breakoutRecords.put(symbolDTO.getSymbol(),
                    new BreakoutRecord(signal.getTriggerPrice(), signal.getStopPrice(), latestClosedKline.getEndTime()));
        });
    }

    @Scheduled(fixedDelay = 5 * 60 * 1000L)
    public void cleanupExpiredBreakoutRecords() {
        long expireBefore = System.currentTimeMillis() - trackHours * 60 * 60 * 1000L;
        breakoutRecords.entrySet().removeIf(entry -> entry.getValue().signalEndTime < expireBefore);
    }

    private void processRiskExit(String symbol, BinanceKlineDTO latestClosedKline) {
        BreakoutRecord record = breakoutRecords.get(symbol);
        if (record == null || latestClosedKline.getEndTime() <= record.signalEndTime) {
            return;
        }

        BigDecimal close = new BigDecimal(latestClosedKline.getClose());
        if (close.compareTo(record.stopPrice) > 0) {
            return;
        }

        alertNotificationService.send(new AlertSignal(
                "SEPA风控退出",
                latestClosedKline,
                SIGNAL_RISK_EXIT,
                "价格跌破前次突破的预设止损位，按小亏离场原则撤退，避免小错拖成大错",
                record.entryPrice,
                record.stopPrice
        ));
        breakoutRecords.remove(symbol);
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

    private List<BinanceKlineDTO> loadRecentKlines(String symbol) {
        BinanceKlineDTO reqDTO = new BinanceKlineDTO();
        reqDTO.setSymbol(symbol);
        reqDTO.setInterval(strategyInterval);
        reqDTO.setLimit(strategyKlineLimit);
        return binanceApi.listKline(reqDTO);
    }

    private static class BreakoutRecord {
        private final BigDecimal entryPrice;
        private final BigDecimal stopPrice;
        private final long signalEndTime;

        private BreakoutRecord(BigDecimal entryPrice, BigDecimal stopPrice, long signalEndTime) {
            this.entryPrice = entryPrice;
            this.stopPrice = stopPrice;
            this.signalEndTime = signalEndTime;
        }
    }
}
