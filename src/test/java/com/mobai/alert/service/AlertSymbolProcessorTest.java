package com.mobai.alert.service;

import com.mobai.alert.dto.BinanceKlineDTO;
import com.mobai.alert.dto.BinanceSymbolsDetailDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.mockito.ArgumentCaptor;

class AlertSymbolProcessorTest {

    private BinanceMarketDataService binanceMarketDataService;
    private AlertRuleEvaluator alertRuleEvaluator;
    private AlertNotificationService alertNotificationService;
    private AlertSymbolProcessor processor;

    @BeforeEach
    void setUp() {
        binanceMarketDataService = mock(BinanceMarketDataService.class);
        alertRuleEvaluator = mock(AlertRuleEvaluator.class);
        alertNotificationService = mock(AlertNotificationService.class);
        processor = new AlertSymbolProcessor(binanceMarketDataService, alertRuleEvaluator, alertNotificationService);
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

        verify(alertNotificationService, never()).send(any(AlertSignal.class));
    }

    @Test
    void shouldUseLatestClosedKlineAsSignalReference() {
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
        when(alertRuleEvaluator.isBacktrackMatch(closedKlines.get(4))).thenReturn(false);
        when(alertRuleEvaluator.isContinuousTwoMatch(closedKlines.get(3))).thenReturn(true);
        when(alertRuleEvaluator.isContinuousTwoMatch(closedKlines.get(4))).thenReturn(true);

        processor.process(symbol);

        ArgumentCaptor<AlertSignal> signalCaptor = ArgumentCaptor.forClass(AlertSignal.class);
        verify(alertNotificationService, atLeastOnce()).send(signalCaptor.capture());

        List<AlertSignal> signals = signalCaptor.getAllValues();
        List<String> signalTypes = signals.stream().map(AlertSignal::getType).collect(Collectors.toList());
        assertTrue(signalTypes.contains("1"));
        assertTrue(signalTypes.contains("3"));
        assertTrue(signals.stream().allMatch(signal -> signal.getKline() == closedKlines.get(4)));
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
}
