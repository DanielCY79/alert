package com.mobai.alert.service;

import com.mobai.alert.dto.BinanceKlineDTO;
import com.mobai.alert.dto.BinanceSymbolsDetailDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 交易对告警处理器测试。
 */
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

    /**
     * 三连涨判断必须基于最近三根已收盘 K 线。
     */
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

    /**
     * 告警信号应引用最近一根已收盘 K 线。
     */
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

    /**
     * 最近三根 K 线中恰好两根命中时，应发送“3根中2根满足规则”告警。
     */
    @Test
    void shouldSendTwoOfThreeSignalWhenExactlyTwoRecentKlinesMatch() {
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
        when(alertRuleEvaluator.isContinuousThreeMatch(closedKlines.get(4))).thenReturn(false);
        when(alertRuleEvaluator.isBacktrackMatch(closedKlines.get(4))).thenReturn(false);
        when(alertRuleEvaluator.isContinuousTwoMatch(closedKlines.get(3))).thenReturn(false);
        when(alertRuleEvaluator.isContinuousTwoMatch(closedKlines.get(4))).thenReturn(false);

        processor.process(symbol);

        ArgumentCaptor<AlertSignal> signalCaptor = ArgumentCaptor.forClass(AlertSignal.class);
        verify(alertNotificationService).send(signalCaptor.capture());

        AlertSignal signal = signalCaptor.getValue();
        assertEquals("4", signal.getType());
        assertEquals("3根K线中2根满足规则", signal.getTitle());
        assertSame(closedKlines.get(4), signal.getKline());
    }

    /**
     * 构造可交易交易对。
     */
    private BinanceSymbolsDetailDTO tradingSymbol(String symbol) {
        BinanceSymbolsDetailDTO dto = new BinanceSymbolsDetailDTO();
        dto.setSymbol(symbol);
        dto.setStatus("TRADING");
        return dto;
    }

    /**
     * 构造测试用 K 线。
     */
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
