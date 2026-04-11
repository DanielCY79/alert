package com.mobai.alert.service;

import com.mobai.alert.api.FeishuBotApi;
import com.mobai.alert.api.FeishuCardTemplate;
import com.mobai.alert.dto.BinanceKlineDTO;
import com.mobai.alert.dto.BinanceSymbolsDTO;
import com.mobai.alert.dto.BinanceSymbolsDetailDTO;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Scanner driven by the Excel-fitted BitLanglang trading profile.
 */
@Service
public class BitLanglangScannerService {

    private static final DateTimeFormatter MESSAGE_TIME_FORMATTER = DateTimeFormatter.ofPattern("MM-dd HH:mm:ss");

    private final AlertSymbolCacheService alertSymbolCacheService;
    private final BinanceMarketDataService binanceMarketDataService;
    private final BitLanglangTradeProfileService tradeProfileService;
    private final BitLanglangStyleRuleEvaluator bitLanglangStyleRuleEvaluator;
    private final FeishuBotApi feishuBotApi;
    private final Map<String, Long> sentSignals = new ConcurrentHashMap<>();

    @Value("${scanner.bitlanglang.enabled:true}")
    private boolean enabled;

    @Value("${scanner.bitlanglang.cleanup-ms:43200000}")
    private long cleanupMs;

    public BitLanglangScannerService(AlertSymbolCacheService alertSymbolCacheService,
                                     BinanceMarketDataService binanceMarketDataService,
                                     BitLanglangTradeProfileService tradeProfileService,
                                     BitLanglangStyleRuleEvaluator bitLanglangStyleRuleEvaluator,
                                     FeishuBotApi feishuBotApi) {
        this.alertSymbolCacheService = alertSymbolCacheService;
        this.binanceMarketDataService = binanceMarketDataService;
        this.tradeProfileService = tradeProfileService;
        this.bitLanglangStyleRuleEvaluator = bitLanglangStyleRuleEvaluator;
        this.feishuBotApi = feishuBotApi;
    }

    @Scheduled(
            initialDelayString = "${scanner.bitlanglang.initial-delay-ms:15000}",
            fixedDelayString = "${scanner.bitlanglang.cycle:60000}"
    )
    public void scan() {
        if (!enabled) {
            return;
        }

        Optional<BitLanglangTradeProfile> profileOptional = tradeProfileService.getActiveProfile();
        if (profileOptional.isEmpty()) {
            cleanupExpiredSignals();
            return;
        }
        BitLanglangTradeProfile profile = profileOptional.get();

        BinanceSymbolsDTO symbolsDTO;
        try {
            symbolsDTO = alertSymbolCacheService.loadSymbols();
        } catch (IOException e) {
            System.out.println("BitLanglang scanner failed to load symbols: " + e.getMessage());
            cleanupExpiredSignals();
            return;
        }

        if (symbolsDTO == null || CollectionUtils.isEmpty(symbolsDTO.getSymbols())) {
            cleanupExpiredSignals();
            return;
        }

        BinanceSymbolsDetailDTO targetSymbol = symbolsDTO.getSymbols().stream()
                .filter(symbol -> symbol != null
                        && profile.getPrimarySymbol().equals(symbol.getSymbol())
                        && "TRADING".equals(symbol.getStatus()))
                .findFirst()
                .orElse(null);
        if (targetSymbol == null) {
            cleanupExpiredSignals();
            return;
        }

        binanceMarketDataService.refreshSubscriptions(List.of(targetSymbol));

        List<BinanceKlineDTO> oneMinuteKlines =
                binanceMarketDataService.loadRecentClosedKlines(targetSymbol.getSymbol(), "1m", 2);
        List<BinanceKlineDTO> fiveMinuteKlines =
                binanceMarketDataService.loadRecentClosedKlines(targetSymbol.getSymbol(), "5m", profile.getBreakoutLookback() + 1);
        List<BinanceKlineDTO> fifteenMinuteKlines =
                binanceMarketDataService.loadRecentClosedKlines(targetSymbol.getSymbol(), "15m", 4);

        List<BitLanglangStyleSignal> signals = bitLanglangStyleRuleEvaluator.evaluate(
                targetSymbol.getSymbol(),
                oneMinuteKlines,
                fiveMinuteKlines,
                fifteenMinuteKlines,
                profile
        );

        for (BitLanglangStyleSignal signal : signals) {
            if (shouldSend(signal)) {
                sendSignal(signal, profile);
            }
        }
        cleanupExpiredSignals();
    }

    private boolean shouldSend(BitLanglangStyleSignal signal) {
        return sentSignals.putIfAbsent(signal.buildDedupKey(), System.currentTimeMillis()) == null;
    }

    private void sendSignal(BitLanglangStyleSignal signal, BitLanglangTradeProfile profile) {
        feishuBotApi.sendGroupMessage(
                "Bit浪浪拟合机会 | " + signal.getSymbol() + " | " + sideLabel(signal.getSide()),
                buildMessage(signal, profile),
                FeishuCardTemplate.BLUE
        );
    }

    private String buildMessage(BitLanglangStyleSignal signal, BitLanglangTradeProfile profile) {
        BinanceKlineDTO oneMinuteKline = signal.getOneMinuteKline();
        BinanceKlineDTO fiveMinuteKline = signal.getFiveMinuteKline();
        BinanceKlineDTO fifteenMinuteKline = signal.getFifteenMinuteKline();

        return "历史画像：" + profile.summary() + "\n"
                + "活跃时段：" + profile.getActiveHours() + "\n"
                + "当前方向：`" + sideLabel(signal.getSide()) + "`\n"
                + "信号分数：" + signal.getScore() + "\n"
                + "1m 动量：" + formatPercent(signal.getOneMinuteRate())
                + "，阈值 " + formatPercent(expectedOneMinuteThreshold(signal, profile)) + "\n"
                + "5m 突破/跌破：" + formatPercent(signal.getFiveMinuteBreakRate())
                + "，阈值 " + formatPercent(expectedFiveMinuteThreshold(signal, profile)) + "\n"
                + "5m 量能倍数：" + signal.getFiveMinuteVolumeMultiplier().setScale(2, RoundingMode.HALF_UP) + "x"
                + "，阈值 " + profile.getVolumeMultiplierMin().setScale(2, RoundingMode.HALF_UP) + "x\n"
                + "15m 趋势：" + formatPercent(signal.getFifteenMinuteTrendRate())
                + "，阈值 " + formatPercent(expectedTrendThreshold(signal, profile)) + "\n"
                + "活跃时段命中：" + (signal.isActiveHour() ? "是" : "否") + "\n"
                + "最新价格：" + formatPrice(fiveMinuteKline.getClose()) + " USDT\n"
                + "1m 收盘：" + formatTime(oneMinuteKline.getEndTime())
                + "，5m 收盘：" + formatTime(fiveMinuteKline.getEndTime())
                + "，15m 收盘：" + formatTime(fifteenMinuteKline.getEndTime()) + "\n"
                + "[查看 Binance 合约图](https://www.binance.com/en/futures/"
                + signal.getSymbol()
                + "?type=spot&layout=pro&interval=5m)";
    }

    private BigDecimal expectedOneMinuteThreshold(BitLanglangStyleSignal signal, BitLanglangTradeProfile profile) {
        return signal.getSide() == BitLanglangTradeSide.LONG
                ? profile.getLongOneMinuteRateMin()
                : profile.getShortOneMinuteRateMin();
    }

    private BigDecimal expectedFiveMinuteThreshold(BitLanglangStyleSignal signal, BitLanglangTradeProfile profile) {
        return signal.getSide() == BitLanglangTradeSide.LONG
                ? profile.getLongFiveMinuteRateMin()
                : profile.getShortFiveMinuteRateMin();
    }

    private BigDecimal expectedTrendThreshold(BitLanglangStyleSignal signal, BitLanglangTradeProfile profile) {
        return signal.getSide() == BitLanglangTradeSide.LONG
                ? profile.getLongFifteenMinuteTrendRateMin()
                : profile.getShortFifteenMinuteTrendRateMin();
    }

    private String sideLabel(BitLanglangTradeSide side) {
        return side == BitLanglangTradeSide.LONG ? "做多" : "做空";
    }

    private void cleanupExpiredSignals() {
        long now = System.currentTimeMillis();
        sentSignals.entrySet().removeIf(entry -> now - entry.getValue() > cleanupMs);
    }

    private String formatPercent(BigDecimal rate) {
        return rate.multiply(new BigDecimal("100")).setScale(2, RoundingMode.HALF_UP) + "%";
    }

    private String formatPrice(String value) {
        return new BigDecimal(value).setScale(4, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString();
    }

    private String formatTime(Long timestamp) {
        return MESSAGE_TIME_FORMATTER.format(
                LocalDateTime.ofInstant(Instant.ofEpochMilli(timestamp), ZoneId.systemDefault())
        );
    }
}
