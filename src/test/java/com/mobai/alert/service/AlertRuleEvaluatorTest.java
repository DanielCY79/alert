package com.mobai.alert.service;

import com.mobai.alert.dto.BinanceKlineDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AlertRuleEvaluatorTest {

    private final AlertRuleEvaluator evaluator = new AlertRuleEvaluator();

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(evaluator, "rateLow", "0.009");
        ReflectionTestUtils.setField(evaluator, "rateHigh", "0.1");
        ReflectionTestUtils.setField(evaluator, "volumeLow", "200000");
        ReflectionTestUtils.setField(evaluator, "volumeHigh", "4000000");
    }

    @Test
    void shouldMatchContinuousThreeKlineWhenRisingAmplitudeAndVolumeAreInRange() {
        BinanceKlineDTO kline = kline("BTCUSDT", "100", "102", "102", "100", "250000", 1L);

        assertTrue(evaluator.isContinuousThreeMatch(kline));
    }

    @Test
    void shouldRejectContinuousThreeKlineWhenVolumeIsOutOfRange() {
        BinanceKlineDTO kline = kline("BTCUSDT", "100", "102", "102", "100", "5000000", 1L);

        assertFalse(evaluator.isContinuousThreeMatch(kline));
    }

    @Test
    void shouldMatchType2SignalWhenLowSevenDayVolumeAndPriceAboveAllMa20() {
        BinanceKlineDTO latestOneMinuteKline = kline("BTCUSDT", "100", "112", "112", "100", "250000", 1441L);
        DailyMa20Snapshot dailyMa20Snapshot = new DailyMa20Snapshot(
                new BigDecimal("90"),
                new BigDecimal("499999999"),
                System.currentTimeMillis() + 60_000L
        );

        LowVolumeMa20SignalContext context = evaluator.evaluateLowVolumeMa20Signal(
                dailyMa20Snapshot,
                latestOneMinuteKline,
                maKlines("BTCUSDT", 20, "91"),
                maKlines("BTCUSDT", 20, "92"),
                maKlines("BTCUSDT", 20, "93")
        );

        assertNotNull(context);
        assertTrue(evaluator.shouldEvaluateLowVolumeMa20Signal(dailyMa20Snapshot, latestOneMinuteKline));
    }

    @Test
    void shouldRejectType2SignalWhenSevenDayAverageVolumeReachesLimit() {
        BinanceKlineDTO latestOneMinuteKline = kline("BTCUSDT", "100", "112", "112", "100", "250000", 1441L);
        DailyMa20Snapshot dailyMa20Snapshot = new DailyMa20Snapshot(
                new BigDecimal("90"),
                new BigDecimal("500000000"),
                System.currentTimeMillis() + 60_000L
        );

        assertFalse(evaluator.shouldEvaluateLowVolumeMa20Signal(dailyMa20Snapshot, latestOneMinuteKline));
    }

    @Test
    void shouldRejectType2SignalWhenCurrentPriceIsNotAboveEachIntradayMa20() {
        BinanceKlineDTO latestOneMinuteKline = kline("BTCUSDT", "100", "112", "112", "100", "250000", 1441L);
        DailyMa20Snapshot dailyMa20Snapshot = new DailyMa20Snapshot(
                new BigDecimal("90"),
                new BigDecimal("400000000"),
                System.currentTimeMillis() + 60_000L
        );

        LowVolumeMa20SignalContext context = evaluator.evaluateLowVolumeMa20Signal(
                dailyMa20Snapshot,
                latestOneMinuteKline,
                maKlines("BTCUSDT", 20, "113"),
                maKlines("BTCUSDT", 20, "92"),
                maKlines("BTCUSDT", 20, "93")
        );

        assertNull(context);
    }

    @Test
    void shouldRejectType2SignalAtOneMinuteVolumeAndAmplitudeBoundary() {
        BinanceKlineDTO latestOneMinuteKline = kline("BTCUSDT", "100", "110", "110", "100", "200000", 1441L);
        DailyMa20Snapshot dailyMa20Snapshot = new DailyMa20Snapshot(
                new BigDecimal("90"),
                new BigDecimal("400000000"),
                System.currentTimeMillis() + 60_000L
        );

        assertFalse(evaluator.shouldEvaluateLowVolumeMa20Signal(dailyMa20Snapshot, latestOneMinuteKline));
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
