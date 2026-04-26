package com.mobai.alert.service;

import com.mobai.alert.dto.BinanceKlineDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AlertRuleEvaluatorTest {

    private final AlertRuleEvaluator evaluator = new AlertRuleEvaluator();

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(evaluator, "type1AverageVolume5dMax", "300000000");
        ReflectionTestUtils.setField(evaluator, "type1OneMinuteVolumeMin", "80000");
        ReflectionTestUtils.setField(evaluator, "type1OneMinuteAmplitudeMin", "0.10");
        ReflectionTestUtils.setField(evaluator, "type2AverageVolume7dMax", "500000000");
        ReflectionTestUtils.setField(evaluator, "type2OneMinuteVolumeMin", "200000");
        ReflectionTestUtils.setField(evaluator, "type2PreviousOneMinuteVolumeMin", "40000");
        ReflectionTestUtils.setField(evaluator, "type2OneMinuteAmplitudeMin", "0.10");
    }

    @Test
    void shouldMatchTypeOneKlineWhenVolumeAndAmplitudeAreAboveThreshold() {
        BinanceKlineDTO kline = kline("BTCUSDT", "100", "112", "112", "100", "80001", 1L);

        assertTrue(evaluator.isTwoOfThreeMomentumKlineMatch(kline));
    }

    @Test
    void shouldRejectTypeOneKlineAtVolumeAndAmplitudeBoundary() {
        BinanceKlineDTO volumeBoundary = kline("BTCUSDT", "100", "112", "112", "100", "80000", 1L);
        BinanceKlineDTO amplitudeBoundary = kline("BTCUSDT", "100", "110", "110", "100", "80001", 2L);

        assertFalse(evaluator.isTwoOfThreeMomentumKlineMatch(volumeBoundary));
        assertFalse(evaluator.isTwoOfThreeMomentumKlineMatch(amplitudeBoundary));
    }

    @Test
    void shouldMatchTypeOneSignalWhenLowFiveDayVolumePriceAboveAllMa20AndTwoOfThreeKlinesMatch() {
        List<BinanceKlineDTO> recentThreeKlines = List.of(
                kline("BTCUSDT", "100", "112", "112", "100", "90000", 1L),
                kline("BTCUSDT", "100", "112", "112", "100", "90000", 2L),
                kline("BTCUSDT", "100", "112", "112", "100", "1000", 3L)
        );
        DailyMa20Snapshot dailyMa20Snapshot = new DailyMa20Snapshot(
                new BigDecimal("90"),
                new BigDecimal("299999999"),
                new BigDecimal("400000000"),
                System.currentTimeMillis() + 60_000L
        );

        TwoOfThreeMomentumSignalContext context = evaluator.evaluateTwoOfThreeMomentumSignal(
                dailyMa20Snapshot,
                recentThreeKlines.get(2),
                recentThreeKlines,
                maKlines("BTCUSDT", 20, "91"),
                maKlines("BTCUSDT", 20, "92"),
                maKlines("BTCUSDT", 20, "93")
        );

        assertNotNull(context);
        assertEquals(2, context.getMatchedOneMinuteCount());
        assertTrue(evaluator.shouldEvaluateTwoOfThreeMomentumSignal(
                dailyMa20Snapshot,
                recentThreeKlines.get(2),
                recentThreeKlines
        ));
    }

    @Test
    void shouldRejectTypeOneSignalWhenFiveDayAverageVolumeReachesLimit() {
        List<BinanceKlineDTO> recentThreeKlines = List.of(
                kline("BTCUSDT", "100", "112", "112", "100", "90000", 1L),
                kline("BTCUSDT", "100", "112", "112", "100", "90000", 2L),
                kline("BTCUSDT", "100", "112", "112", "100", "90000", 3L)
        );
        DailyMa20Snapshot dailyMa20Snapshot = new DailyMa20Snapshot(
                new BigDecimal("90"),
                new BigDecimal("300000000"),
                new BigDecimal("400000000"),
                System.currentTimeMillis() + 60_000L
        );

        assertFalse(evaluator.shouldEvaluateTwoOfThreeMomentumSignal(
                dailyMa20Snapshot,
                recentThreeKlines.get(2),
                recentThreeKlines
        ));
    }

    @Test
    void shouldRejectTypeOneSignalWhenCurrentPriceIsNotAboveEachIntradayMa20() {
        List<BinanceKlineDTO> recentThreeKlines = List.of(
                kline("BTCUSDT", "100", "112", "112", "100", "90000", 1L),
                kline("BTCUSDT", "100", "112", "112", "100", "90000", 2L),
                kline("BTCUSDT", "100", "112", "112", "100", "90000", 3L)
        );
        DailyMa20Snapshot dailyMa20Snapshot = new DailyMa20Snapshot(
                new BigDecimal("90"),
                new BigDecimal("299999999"),
                new BigDecimal("400000000"),
                System.currentTimeMillis() + 60_000L
        );

        TwoOfThreeMomentumSignalContext context = evaluator.evaluateTwoOfThreeMomentumSignal(
                dailyMa20Snapshot,
                recentThreeKlines.get(2),
                recentThreeKlines,
                maKlines("BTCUSDT", 20, "113"),
                maKlines("BTCUSDT", 20, "92"),
                maKlines("BTCUSDT", 20, "93")
        );

        assertNull(context);
    }

    @Test
    void shouldMatchType2SignalWhenLowSevenDayVolumeAndPriceAboveAllMa20() {
        BinanceKlineDTO latestOneMinuteKline = kline("BTCUSDT", "100", "112", "112", "100", "250000", 1441L);
        BinanceKlineDTO previousOneMinuteKline = kline("BTCUSDT", "100", "101", "101", "100", "40001", 1440L);
        DailyMa20Snapshot dailyMa20Snapshot = new DailyMa20Snapshot(
                new BigDecimal("90"),
                new BigDecimal("299999999"),
                new BigDecimal("499999999"),
                System.currentTimeMillis() + 60_000L
        );

        LowVolumeMa20SignalContext context = evaluator.evaluateLowVolumeMa20Signal(
                dailyMa20Snapshot,
                latestOneMinuteKline,
                previousOneMinuteKline,
                maKlines("BTCUSDT", 20, "91"),
                maKlines("BTCUSDT", 20, "92"),
                maKlines("BTCUSDT", 20, "93")
        );

        assertNotNull(context);
        assertTrue(evaluator.shouldEvaluateLowVolumeMa20Signal(
                dailyMa20Snapshot,
                latestOneMinuteKline,
                previousOneMinuteKline
        ));
    }

    @Test
    void shouldRejectType2SignalWhenSevenDayAverageVolumeReachesLimit() {
        BinanceKlineDTO latestOneMinuteKline = kline("BTCUSDT", "100", "112", "112", "100", "250000", 1441L);
        BinanceKlineDTO previousOneMinuteKline = kline("BTCUSDT", "100", "101", "101", "100", "40001", 1440L);
        DailyMa20Snapshot dailyMa20Snapshot = new DailyMa20Snapshot(
                new BigDecimal("90"),
                new BigDecimal("299999999"),
                new BigDecimal("500000000"),
                System.currentTimeMillis() + 60_000L
        );

        assertFalse(evaluator.shouldEvaluateLowVolumeMa20Signal(
                dailyMa20Snapshot,
                latestOneMinuteKline,
                previousOneMinuteKline
        ));
    }

    @Test
    void shouldRejectType2SignalWhenCurrentPriceIsNotAboveEachIntradayMa20() {
        BinanceKlineDTO latestOneMinuteKline = kline("BTCUSDT", "100", "112", "112", "100", "250000", 1441L);
        BinanceKlineDTO previousOneMinuteKline = kline("BTCUSDT", "100", "101", "101", "100", "40001", 1440L);
        DailyMa20Snapshot dailyMa20Snapshot = new DailyMa20Snapshot(
                new BigDecimal("90"),
                new BigDecimal("299999999"),
                new BigDecimal("400000000"),
                System.currentTimeMillis() + 60_000L
        );

        LowVolumeMa20SignalContext context = evaluator.evaluateLowVolumeMa20Signal(
                dailyMa20Snapshot,
                latestOneMinuteKline,
                previousOneMinuteKline,
                maKlines("BTCUSDT", 20, "113"),
                maKlines("BTCUSDT", 20, "92"),
                maKlines("BTCUSDT", 20, "93")
        );

        assertNull(context);
    }

    @Test
    void shouldRejectType2SignalAtOneMinuteVolumeAndAmplitudeBoundary() {
        BinanceKlineDTO latestOneMinuteKline = kline("BTCUSDT", "100", "110", "110", "100", "200000", 1441L);
        BinanceKlineDTO previousOneMinuteKline = kline("BTCUSDT", "100", "101", "101", "100", "40001", 1440L);
        DailyMa20Snapshot dailyMa20Snapshot = new DailyMa20Snapshot(
                new BigDecimal("90"),
                new BigDecimal("299999999"),
                new BigDecimal("400000000"),
                System.currentTimeMillis() + 60_000L
        );

        assertFalse(evaluator.shouldEvaluateLowVolumeMa20Signal(
                dailyMa20Snapshot,
                latestOneMinuteKline,
                previousOneMinuteKline
        ));
    }

    @Test
    void shouldRejectType2SignalWhenPreviousOneMinuteVolumeIsAtBoundary() {
        BinanceKlineDTO latestOneMinuteKline = kline("BTCUSDT", "100", "112", "112", "100", "250000", 1441L);
        BinanceKlineDTO previousOneMinuteKline = kline("BTCUSDT", "100", "101", "101", "100", "40000", 1440L);
        DailyMa20Snapshot dailyMa20Snapshot = new DailyMa20Snapshot(
                new BigDecimal("90"),
                new BigDecimal("299999999"),
                new BigDecimal("400000000"),
                System.currentTimeMillis() + 60_000L
        );

        assertFalse(evaluator.shouldEvaluateLowVolumeMa20Signal(
                dailyMa20Snapshot,
                latestOneMinuteKline,
                previousOneMinuteKline
        ));
    }

    private BinanceKlineDTO kline(String symbol,
                                  String open,
                                  String close,
                                  String high,
                                  String low,
                                  String volume,
                                  long endTime) {
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

    private List<BinanceKlineDTO> maKlines(String symbol, int size, String close) {
        List<BinanceKlineDTO> klines = new ArrayList<>();
        for (int i = 1; i <= size; i++) {
            klines.add(kline(symbol, close, close, close, close, "1000000", i));
        }
        return klines;
    }
}
