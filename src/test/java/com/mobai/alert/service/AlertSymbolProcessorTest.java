package com.mobai.alert.service;

import com.mobai.alert.api.FeishuCardTemplate;
import com.mobai.alert.dto.BinanceKlineDTO;
import com.mobai.alert.dto.BinanceSymbolsDetailDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class AlertSymbolProcessorTest {

    private BinanceMarketDataService binanceMarketDataService;
    private DailyMa20SnapshotService dailyMa20SnapshotService;
    private AlertRuleEvaluator alertRuleEvaluator;
    private AlertNotificationService alertNotificationService;
    private AlertSymbolProcessor processor;

    @BeforeEach
    void setUp() {
        binanceMarketDataService = mock(BinanceMarketDataService.class);
        dailyMa20SnapshotService = mock(DailyMa20SnapshotService.class);
        alertRuleEvaluator = mock(AlertRuleEvaluator.class);
        alertNotificationService = mock(AlertNotificationService.class);
        processor = new AlertSymbolProcessor(
                binanceMarketDataService,
                dailyMa20SnapshotService,
                alertRuleEvaluator,
                alertNotificationService
        );
        ReflectionTestUtils.setField(processor, "excludeSymbol", "");
    }

    @Test
    void shouldSendTypeOneWhenTwoOfLastThreeKlinesMatch() {
        BinanceSymbolsDetailDTO symbol = tradingSymbol("SOLUSDT");
        List<BinanceKlineDTO> closedKlines = List.of(
                kline("SOLUSDT", 1L),
                kline("SOLUSDT", 2L),
                kline("SOLUSDT", 3L),
                kline("SOLUSDT", 4L),
                kline("SOLUSDT", 5L)
        );

        when(binanceMarketDataService.loadRecentClosedKlines("SOLUSDT", "1m", 5)).thenReturn(closedKlines);
        when(alertRuleEvaluator.isContinuousThreeMatch(closedKlines.get(2))).thenReturn(true);
        when(alertRuleEvaluator.isContinuousThreeMatch(closedKlines.get(3))).thenReturn(true);
        when(alertRuleEvaluator.isContinuousThreeMatch(closedKlines.get(4))).thenReturn(false);

        processor.process(symbol);

        ArgumentCaptor<AlertSignal> signalCaptor = ArgumentCaptor.forClass(AlertSignal.class);
        verify(alertNotificationService).send(signalCaptor.capture());
        assertEquals("1", signalCaptor.getValue().getType());
        assertEquals("3根K线中2根拉升", signalCaptor.getValue().getTitle());
        assertSame(closedKlines.get(4), signalCaptor.getValue().getKline());
    }

    @Test
    void shouldSendLowVolumeMa20SignalAsTypeTwo() {
        BinanceSymbolsDetailDTO symbol = tradingSymbol("SOLUSDT");
        List<BinanceKlineDTO> closedKlines = List.of(
                kline("SOLUSDT", 1L),
                kline("SOLUSDT", 2L),
                kline("SOLUSDT", 3L),
                kline("SOLUSDT", 4L),
                kline("SOLUSDT", 5L)
        );
        BinanceKlineDTO latestClosedKline = closedKlines.get(closedKlines.size() - 1);
        DailyMa20Snapshot dailyMa20Snapshot = new DailyMa20Snapshot(
                new BigDecimal("93"),
                new BigDecimal("400000000"),
                System.currentTimeMillis() + 60_000L
        );
        List<BinanceKlineDTO> fifteenMinuteKlines = maKlines("SOLUSDT", 20, "90");
        List<BinanceKlineDTO> oneHourKlines = maKlines("SOLUSDT", 20, "91");
        List<BinanceKlineDTO> fourHourKlines = maKlines("SOLUSDT", 20, "92");
        LowVolumeMa20SignalContext context = new LowVolumeMa20SignalContext(
                latestClosedKline,
                new BigDecimal("112"),
                new BigDecimal("400000000"),
                new BigDecimal("93"),
                new BigDecimal("92"),
                new BigDecimal("91"),
                new BigDecimal("90"),
                new BigDecimal("250000"),
                new BigDecimal("0.12")
        );

        when(binanceMarketDataService.loadRecentClosedKlines("SOLUSDT", "1m", 5)).thenReturn(closedKlines);
        when(dailyMa20SnapshotService.getSnapshot("SOLUSDT")).thenReturn(dailyMa20Snapshot);
        when(alertRuleEvaluator.shouldEvaluateLowVolumeMa20Signal(dailyMa20Snapshot, latestClosedKline)).thenReturn(true);
        when(binanceMarketDataService.loadRecentClosedKlines("SOLUSDT", "15m", 20)).thenReturn(fifteenMinuteKlines);
        when(binanceMarketDataService.loadRecentClosedKlines("SOLUSDT", "1h", 20)).thenReturn(oneHourKlines);
        when(binanceMarketDataService.loadRecentClosedKlines("SOLUSDT", "4h", 20)).thenReturn(fourHourKlines);
        when(alertRuleEvaluator.evaluateLowVolumeMa20Signal(
                dailyMa20Snapshot,
                latestClosedKline,
                fifteenMinuteKlines,
                oneHourKlines,
                fourHourKlines
        )).thenReturn(context);

        processor.process(symbol);

        ArgumentCaptor<AlertSignal> signalCaptor = ArgumentCaptor.forClass(AlertSignal.class);
        verify(alertNotificationService).send(signalCaptor.capture());
        assertEquals("2", signalCaptor.getValue().getType());
        assertEquals(AlertCooldownCategory.LOW_VOLUME_MA20, signalCaptor.getValue().getCooldownCategory());
        assertEquals(FeishuCardTemplate.YELLOW, signalCaptor.getValue().getTemplate());
    }

    @Test
    void shouldNotSendWhenRetainedRulesDoNotMatch() {
        BinanceSymbolsDetailDTO symbol = tradingSymbol("SOLUSDT");
        List<BinanceKlineDTO> closedKlines = List.of(
                kline("SOLUSDT", 1L),
                kline("SOLUSDT", 2L),
                kline("SOLUSDT", 3L),
                kline("SOLUSDT", 4L),
                kline("SOLUSDT", 5L)
        );
        BinanceKlineDTO latestClosedKline = closedKlines.get(closedKlines.size() - 1);

        when(binanceMarketDataService.loadRecentClosedKlines("SOLUSDT", "1m", 5)).thenReturn(closedKlines);
        when(alertRuleEvaluator.isContinuousThreeMatch(closedKlines.get(2))).thenReturn(false);
        when(dailyMa20SnapshotService.getSnapshot("SOLUSDT")).thenReturn(null);
        when(alertRuleEvaluator.shouldEvaluateLowVolumeMa20Signal(null, latestClosedKline)).thenReturn(false);

        processor.process(symbol);

        verifyNoInteractions(alertNotificationService);
    }

    @Test
    void shouldSkipBtcAndEthRelatedSymbols() {
        processor.process(tradingSymbol("BTCUSDT"));
        processor.process(tradingSymbol("ETHUSDT"));
        processor.process(tradingSymbol("BTCUSDT_260626"));
        processor.process(tradingSymbol("ETHBTC"));

        verifyNoInteractions(binanceMarketDataService, alertNotificationService);
    }

    private BinanceSymbolsDetailDTO tradingSymbol(String symbol) {
        BinanceSymbolsDetailDTO dto = new BinanceSymbolsDetailDTO();
        dto.setSymbol(symbol);
        dto.setStatus("TRADING");
        return dto;
    }

    private BinanceKlineDTO kline(String symbol, long endTime) {
        BinanceKlineDTO dto = new BinanceKlineDTO();
        dto.setSymbol(symbol);
        dto.setOpen("100");
        dto.setClose("112");
        dto.setHigh("112");
        dto.setLow("100");
        dto.setVolume("250000");
        dto.setEndTime(endTime);
        return dto;
    }

    private List<BinanceKlineDTO> maKlines(String symbol, int size, String close) {
        List<BinanceKlineDTO> klines = new ArrayList<>();
        for (int i = 1; i <= size; i++) {
            BinanceKlineDTO dto = new BinanceKlineDTO();
            dto.setSymbol(symbol);
            dto.setOpen(close);
            dto.setClose(close);
            dto.setHigh(close);
            dto.setLow(close);
            dto.setVolume("1000000");
            dto.setEndTime((long) i);
            klines.add(dto);
        }
        return klines;
    }
}
