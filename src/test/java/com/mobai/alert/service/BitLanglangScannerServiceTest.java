package com.mobai.alert.service;

import com.mobai.alert.api.FeishuBotApi;
import com.mobai.alert.api.FeishuCardTemplate;
import com.mobai.alert.dto.BinanceKlineDTO;
import com.mobai.alert.dto.BinanceSymbolsDTO;
import com.mobai.alert.dto.BinanceSymbolsDetailDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

class BitLanglangScannerServiceTest {

    private AlertSymbolCacheService alertSymbolCacheService;
    private BinanceMarketDataService binanceMarketDataService;
    private BitLanglangTradeProfileService tradeProfileService;
    private BitLanglangStyleRuleEvaluator bitLanglangStyleRuleEvaluator;
    private MarketSentimentService marketSentimentService;
    private FeishuBotApi feishuBotApi;
    private BitLanglangScannerService scannerService;

    @BeforeEach
    void setUp() {
        alertSymbolCacheService = mock(AlertSymbolCacheService.class);
        binanceMarketDataService = mock(BinanceMarketDataService.class);
        tradeProfileService = mock(BitLanglangTradeProfileService.class);
        bitLanglangStyleRuleEvaluator = mock(BitLanglangStyleRuleEvaluator.class);
        marketSentimentService = mock(MarketSentimentService.class);
        feishuBotApi = mock(FeishuBotApi.class);
        when(marketSentimentService.enrichBody(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString()))
                .thenAnswer(invocation -> invocation.getArgument(1));
        scannerService = new BitLanglangScannerService(
                alertSymbolCacheService,
                binanceMarketDataService,
                tradeProfileService,
                bitLanglangStyleRuleEvaluator,
                marketSentimentService,
                feishuBotApi
        );
        ReflectionTestUtils.setField(scannerService, "enabled", true);
        ReflectionTestUtils.setField(scannerService, "cleanupMs", 60_000L);
    }

    @Test
    void shouldSendBlueOpportunityCardAndDeduplicateByDirectionAndFiveMinuteCloseTime() throws IOException {
        BitLanglangTradeProfile profile = new BitLanglangTradeProfile(
                Path.of("C:/Users/Administrator/Desktop/bit浪浪BTC.xlsx"),
                1L,
                1036,
                "BTCUSDT",
                List.of(20, 21, 22),
                new BigDecimal("14.0"),
                new BigDecimal("20"),
                new BigDecimal("0.55"),
                new BigDecimal("0.45"),
                4,
                new BigDecimal("1.60"),
                new BigDecimal("0.0020"),
                new BigDecimal("0.0020"),
                new BigDecimal("0.0010"),
                new BigDecimal("0.0010"),
                new BigDecimal("0.0050"),
                new BigDecimal("0.0050")
        );

        BinanceSymbolsDTO symbolsDTO = new BinanceSymbolsDTO();
        BinanceSymbolsDetailDTO btc = tradingSymbol("BTCUSDT");
        symbolsDTO.setSymbols(List.of(btc));

        List<BinanceKlineDTO> oneMinuteKlines = List.of(kline("BTCUSDT", 1_000L, "100", "101", "101", "99", "1000000"));
        List<BinanceKlineDTO> fiveMinuteKlines = List.of(
                kline("BTCUSDT", 2_000L, "99", "100", "100", "98", "1500000"),
                kline("BTCUSDT", 3_000L, "100", "102", "102", "99.5", "4000000")
        );
        List<BinanceKlineDTO> fifteenMinuteKlines = List.of(
                kline("BTCUSDT", 4_000L, "95", "97", "97", "94", "5000000"),
                kline("BTCUSDT", 5_000L, "97", "98", "98", "96", "5200000"),
                kline("BTCUSDT", 6_000L, "98", "100", "100", "97", "5300000"),
                kline("BTCUSDT", 7_000L, "100", "103", "103", "99", "5500000")
        );
        BitLanglangStyleSignal signal = new BitLanglangStyleSignal(
                "BTCUSDT",
                BitLanglangTradeSide.LONG,
                oneMinuteKlines.get(0),
                fiveMinuteKlines.get(1),
                fifteenMinuteKlines.get(3),
                new BigDecimal("0.0100"),
                new BigDecimal("0.0200"),
                new BigDecimal("2.3000"),
                new BigDecimal("0.0300"),
                true,
                110
        );

        when(tradeProfileService.getActiveProfile()).thenReturn(Optional.of(profile));
        when(alertSymbolCacheService.loadSymbols()).thenReturn(symbolsDTO);
        when(binanceMarketDataService.loadRecentClosedKlines("BTCUSDT", "1m", 2)).thenReturn(oneMinuteKlines);
        when(binanceMarketDataService.loadRecentClosedKlines("BTCUSDT", "5m", profile.getBreakoutLookback() + 1)).thenReturn(fiveMinuteKlines);
        when(binanceMarketDataService.loadRecentClosedKlines("BTCUSDT", "15m", 4)).thenReturn(fifteenMinuteKlines);
        when(bitLanglangStyleRuleEvaluator.evaluate("BTCUSDT", oneMinuteKlines, fiveMinuteKlines, fifteenMinuteKlines, profile))
                .thenReturn(List.of(signal));

        scannerService.scan();
        scannerService.scan();

        verify(feishuBotApi).sendGroupMessage(
                contains("BTCUSDT"),
                contains("当前方向：`做多`"),
                eq(FeishuCardTemplate.BLUE)
        );
        verifyNoMoreInteractions(feishuBotApi);
    }

    private BinanceSymbolsDetailDTO tradingSymbol(String symbol) {
        BinanceSymbolsDetailDTO dto = new BinanceSymbolsDetailDTO();
        dto.setSymbol(symbol);
        dto.setStatus("TRADING");
        return dto;
    }

    private BinanceKlineDTO kline(String symbol,
                                  long endTime,
                                  String open,
                                  String close,
                                  String high,
                                  String low,
                                  String volume) {
        BinanceKlineDTO dto = new BinanceKlineDTO();
        dto.setSymbol(symbol);
        dto.setOpen(open);
        dto.setClose(close);
        dto.setHigh(high);
        dto.setLow(low);
        dto.setVolume(volume);
        dto.setEndTime(endTime);
        return dto;
    }
}
