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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AlertSymbolProcessorTest {

    private BinanceMarketDataService binanceMarketDataService;
    private BinanceTickerStatsService binanceTickerStatsService;
    private DailyMa20SnapshotService dailyMa20SnapshotService;
    private AlertRuleEvaluator alertRuleEvaluator;
    private AlertNotificationService alertNotificationService;
    private AlertSymbolProcessor processor;

    @BeforeEach
    void setUp() {
        binanceMarketDataService = mock(BinanceMarketDataService.class);
        binanceTickerStatsService = mock(BinanceTickerStatsService.class);
        dailyMa20SnapshotService = mock(DailyMa20SnapshotService.class);
        alertRuleEvaluator = mock(AlertRuleEvaluator.class);
        alertNotificationService = mock(AlertNotificationService.class);
        processor = new AlertSymbolProcessor(
                binanceMarketDataService,
                binanceTickerStatsService,
                dailyMa20SnapshotService,
                alertRuleEvaluator,
                alertNotificationService
        );
        ReflectionTestUtils.setField(processor, "excludeSymbol", "");
    }

    @Test
    void shouldRequireThreeClosedKlinesForContinuousThreeSignal() {
        BinanceSymbolsDetailDTO symbol = tradingSymbol("BTCUSDT");
        List<BinanceKlineDTO> closedKlines = List.of(
                kline("BTCUSDT", 1L),
                kline("BTCUSDT", 2L),
                kline("BTCUSDT", 3L),
                kline("BTCUSDT", 4L)
        );

        when(binanceMarketDataService.loadRecentClosedKlines("BTCUSDT", "1m", 5)).thenReturn(closedKlines);
        when(alertRuleEvaluator.isContinuousThreeMatch(closedKlines.get(1))).thenReturn(true);
        when(alertRuleEvaluator.isContinuousThreeMatch(closedKlines.get(2))).thenReturn(true);
        when(alertRuleEvaluator.isContinuousThreeMatch(closedKlines.get(3))).thenReturn(false);
        when(alertRuleEvaluator.isContinuousTwoMatch(any())).thenReturn(false);

        processor.process(symbol);

        ArgumentCaptor<AlertSignal> signalCaptor = ArgumentCaptor.forClass(AlertSignal.class);
        verify(alertNotificationService).send(signalCaptor.capture());
        assertEquals("4", signalCaptor.getValue().getType());
    }

    @Test
    void shouldUseLatestClosedKlineForWinningContinuousThreeSignal() {
        BinanceSymbolsDetailDTO symbol = tradingSymbol("BTCUSDT");
        List<BinanceKlineDTO> closedKlines = List.of(
                kline("BTCUSDT", 1L),
                kline("BTCUSDT", 2L),
                kline("BTCUSDT", 3L),
                kline("BTCUSDT", 4L),
                kline("BTCUSDT", 5L)
        );

        when(binanceMarketDataService.loadRecentClosedKlines("BTCUSDT", "1m", 5)).thenReturn(closedKlines);
        when(alertRuleEvaluator.isContinuousThreeMatch(closedKlines.get(2))).thenReturn(true);
        when(alertRuleEvaluator.isContinuousThreeMatch(closedKlines.get(3))).thenReturn(true);
        when(alertRuleEvaluator.isContinuousThreeMatch(closedKlines.get(4))).thenReturn(true);
        when(alertRuleEvaluator.isContinuousTwoMatch(closedKlines.get(3))).thenReturn(true);
        when(alertRuleEvaluator.isContinuousTwoMatch(closedKlines.get(4))).thenReturn(true);
        when(alertRuleEvaluator.isBacktrackMatch(closedKlines.get(4))).thenReturn(false);

        processor.process(symbol);

        ArgumentCaptor<AlertSignal> signalCaptor = ArgumentCaptor.forClass(AlertSignal.class);
        verify(alertNotificationService).send(signalCaptor.capture());
        assertEquals("1", signalCaptor.getValue().getType());
        assertSame(closedKlines.get(4), signalCaptor.getValue().getKline());
    }

    @Test
    void shouldPreferTwoOfThreeOverContinuousTwoWhenBothMatch() {
        BinanceSymbolsDetailDTO symbol = tradingSymbol("BTCUSDT");
        List<BinanceKlineDTO> closedKlines = List.of(
                kline("BTCUSDT", 1L),
                kline("BTCUSDT", 2L),
                kline("BTCUSDT", 3L),
                kline("BTCUSDT", 4L),
                kline("BTCUSDT", 5L)
        );

        when(binanceMarketDataService.loadRecentClosedKlines("BTCUSDT", "1m", 5)).thenReturn(closedKlines);
        when(alertRuleEvaluator.isContinuousThreeMatch(closedKlines.get(2))).thenReturn(false);
        when(alertRuleEvaluator.isContinuousThreeMatch(closedKlines.get(3))).thenReturn(true);
        when(alertRuleEvaluator.isContinuousThreeMatch(closedKlines.get(4))).thenReturn(true);
        when(alertRuleEvaluator.isContinuousTwoMatch(closedKlines.get(3))).thenReturn(true);
        when(alertRuleEvaluator.isContinuousTwoMatch(closedKlines.get(4))).thenReturn(true);
        when(alertRuleEvaluator.isBacktrackMatch(closedKlines.get(4))).thenReturn(false);

        processor.process(symbol);

        ArgumentCaptor<AlertSignal> signalCaptor = ArgumentCaptor.forClass(AlertSignal.class);
        verify(alertNotificationService).send(signalCaptor.capture());
        assertEquals("4", signalCaptor.getValue().getType());
        assertEquals("3根K线中2根满足规则", signalCaptor.getValue().getTitle());
        assertSame(closedKlines.get(4), signalCaptor.getValue().getKline());
    }

    @Test
    void shouldSendContinuousTwoWhenNoStrongerSignalMatches() {
        BinanceSymbolsDetailDTO symbol = tradingSymbol("BTCUSDT");
        List<BinanceKlineDTO> closedKlines = List.of(
                kline("BTCUSDT", 1L),
                kline("BTCUSDT", 2L),
                kline("BTCUSDT", 3L),
                kline("BTCUSDT", 4L),
                kline("BTCUSDT", 5L)
        );

        when(binanceMarketDataService.loadRecentClosedKlines("BTCUSDT", "1m", 5)).thenReturn(closedKlines);
        when(alertRuleEvaluator.isContinuousThreeMatch(closedKlines.get(2))).thenReturn(true);
        when(alertRuleEvaluator.isContinuousThreeMatch(closedKlines.get(3))).thenReturn(false);
        when(alertRuleEvaluator.isContinuousThreeMatch(closedKlines.get(4))).thenReturn(false);
        when(alertRuleEvaluator.isContinuousTwoMatch(closedKlines.get(3))).thenReturn(true);
        when(alertRuleEvaluator.isContinuousTwoMatch(closedKlines.get(4))).thenReturn(true);
        when(alertRuleEvaluator.isBacktrackMatch(closedKlines.get(4))).thenReturn(false);

        processor.process(symbol);

        ArgumentCaptor<AlertSignal> signalCaptor = ArgumentCaptor.forClass(AlertSignal.class);
        verify(alertNotificationService).send(signalCaptor.capture());
        assertEquals("3", signalCaptor.getValue().getType());
        assertSame(closedKlines.get(4), signalCaptor.getValue().getKline());
    }

    @Test
    void shouldSendMa20VolumeSpikeSignalWithPurpleCardTemplate() {
        BinanceSymbolsDetailDTO symbol = tradingSymbol("BTCUSDT");
        List<BinanceKlineDTO> oneMinuteKlines = oneMinuteKlines("BTCUSDT", 5, "100000");
        oneMinuteKlines.get(oneMinuteKlines.size() - 1).setVolume("320000");
        List<BinanceKlineDTO> dailyKlines = dailyKlines("BTCUSDT", 20);
        BinanceKlineDTO latestOneMinuteKline = oneMinuteKlines.get(oneMinuteKlines.size() - 1);
        BinanceKlineDTO latestDailyKline = dailyKlines.get(dailyKlines.size() - 1);
        DailyMa20Snapshot dailyMa20Snapshot = new DailyMa20Snapshot(
                latestDailyKline,
                new BigDecimal("100.1234"),
                new BigDecimal("120"),
                new BigDecimal("90"),
                System.currentTimeMillis() + 60_000L
        );
        Ma20VolumeSpikeContext context = new Ma20VolumeSpikeContext(
                latestDailyKline,
                latestOneMinuteKline,
                new BigDecimal("100.1234"),
                new BigDecimal("100000.0000"),
                new BigDecimal("320000.0000"),
                new BigDecimal("3.2000")
        );

        when(binanceMarketDataService.loadRecentClosedKlines("BTCUSDT", "1m", 5)).thenReturn(oneMinuteKlines);
        when(binanceTickerStatsService.getAverageOneMinuteVolume("BTCUSDT")).thenReturn(new BigDecimal("100000"));
        when(dailyMa20SnapshotService.getSnapshot("BTCUSDT")).thenReturn(dailyMa20Snapshot);
        when(alertRuleEvaluator.isContinuousThreeMatch(any())).thenReturn(false);
        when(alertRuleEvaluator.isContinuousTwoMatch(any())).thenReturn(false);
        when(alertRuleEvaluator.evaluateMa20VolumeSpike(dailyMa20Snapshot, latestOneMinuteKline, new BigDecimal("100000")))
                .thenReturn(context);

        processor.process(symbol);

        ArgumentCaptor<AlertSignal> signalCaptor = ArgumentCaptor.forClass(AlertSignal.class);
        verify(alertNotificationService).send(signalCaptor.capture());
        assertEquals("5", signalCaptor.getValue().getType());
        assertEquals(AlertCooldownCategory.DAILY_MA20_VOLUME_SPIKE, signalCaptor.getValue().getCooldownCategory());
        assertEquals(FeishuCardTemplate.PURPLE, signalCaptor.getValue().getTemplate());
        assertEquals("日K站上MA20且1m成交额放大", signalCaptor.getValue().getTitle());
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
        dto.setOpen("1");
        dto.setClose("2");
        dto.setHigh("2");
        dto.setLow("1");
        dto.setVolume("1000000");
        dto.setEndTime(endTime);
        return dto;
    }

    private List<BinanceKlineDTO> oneMinuteKlines(String symbol, int size, String volume) {
        List<BinanceKlineDTO> klines = new ArrayList<>();
        for (int i = 1; i <= size; i++) {
            BinanceKlineDTO dto = new BinanceKlineDTO();
            dto.setSymbol(symbol);
            dto.setOpen("100");
            dto.setClose("101");
            dto.setHigh("101");
            dto.setLow("99");
            dto.setVolume(volume);
            dto.setEndTime((long) i);
            klines.add(dto);
        }
        return klines;
    }

    private List<BinanceKlineDTO> dailyKlines(String symbol, int size) {
        List<BinanceKlineDTO> klines = new ArrayList<>();
        for (int i = 1; i <= size; i++) {
            BinanceKlineDTO dto = new BinanceKlineDTO();
            dto.setSymbol(symbol);
            dto.setOpen("100");
            dto.setClose(String.valueOf(100 + i));
            dto.setHigh(String.valueOf(101 + i));
            dto.setLow(String.valueOf(99 + i));
            dto.setVolume("1000000");
            dto.setEndTime(i * 86_400_000L);
            klines.add(dto);
        }
        return klines;
    }
}
