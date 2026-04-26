package com.mobai.alert.service;

import com.mobai.alert.dto.BinanceKlineDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class AlertRuleEvaluatorTest {

    private final AlertRuleEvaluator evaluator = new AlertRuleEvaluator();

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(evaluator, "twoVolumeLow", "1000000");
    }

    @Test
    void shouldMatchWhenDailyCloseIsAboveMa20AndOneMinuteVolumeIsThreeTimesAverage() {
        List<BinanceKlineDTO> dailyKlines = sequentialDailyKlines("BTCUSDT", 91, 20);
        BinanceKlineDTO latestOneMinuteKline = oneMinuteKline("BTCUSDT", 3000000, 1441L);
        DailyMa20Snapshot dailyMa20Snapshot = new DailyMa20Snapshot(
                dailyKlines.get(dailyKlines.size() - 1),
                new BigDecimal("100.5"),
                new BigDecimal("120"),
                new BigDecimal("90"),
                System.currentTimeMillis() + 60_000L
        );

        Ma20VolumeSpikeContext context = evaluator.evaluateMa20VolumeSpike(
                dailyMa20Snapshot,
                latestOneMinuteKline,
                new BigDecimal("1000000")
        );

        assertNotNull(context);
        assertEquals("100.5", context.getMa20().stripTrailingZeros().toPlainString());
        assertEquals("3", context.getVolumeRatio().stripTrailingZeros().toPlainString());
    }

    @Test
    void shouldRejectWhenLatestDailyCloseIsNotAboveMa20() {
        List<BinanceKlineDTO> dailyKlines = dailyKlinesBelowMa20("BTCUSDT");
        BinanceKlineDTO latestOneMinuteKline = oneMinuteKline("BTCUSDT", 300000, 1441L);
        DailyMa20Snapshot dailyMa20Snapshot = new DailyMa20Snapshot(
                dailyKlines.get(dailyKlines.size() - 1),
                new BigDecimal("110"),
                new BigDecimal("120"),
                new BigDecimal("90"),
                System.currentTimeMillis() + 60_000L
        );

        Ma20VolumeSpikeContext context = evaluator.evaluateMa20VolumeSpike(
                dailyMa20Snapshot,
                latestOneMinuteKline,
                new BigDecimal("100000")
        );

        assertNull(context);
    }

    @Test
    void shouldRejectWhenOneMinuteVolumeIsBelowConfiguredMinimum() {
        List<BinanceKlineDTO> dailyKlines = sequentialDailyKlines("BTCUSDT", 91, 20);
        BinanceKlineDTO latestOneMinuteKline = oneMinuteKline("BTCUSDT", 600000, 1441L);
        DailyMa20Snapshot dailyMa20Snapshot = new DailyMa20Snapshot(
                dailyKlines.get(dailyKlines.size() - 1),
                new BigDecimal("100.5"),
                new BigDecimal("120"),
                new BigDecimal("90"),
                System.currentTimeMillis() + 60_000L
        );

        Ma20VolumeSpikeContext context = evaluator.evaluateMa20VolumeSpike(
                dailyMa20Snapshot,
                latestOneMinuteKline,
                new BigDecimal("200000")
        );

        assertNull(context);
    }

    @Test
    void shouldRejectWhenDailyBollingerBandWidthIsTooWide() {
        List<BinanceKlineDTO> dailyKlines = sequentialDailyKlines("BTCUSDT", 91, 20);
        BinanceKlineDTO latestOneMinuteKline = oneMinuteKline("BTCUSDT", 3000000, 1441L);
        DailyMa20Snapshot dailyMa20Snapshot = new DailyMa20Snapshot(
                dailyKlines.get(dailyKlines.size() - 1),
                new BigDecimal("100.5"),
                new BigDecimal("150"),
                new BigDecimal("90"),
                System.currentTimeMillis() + 60_000L
        );

        Ma20VolumeSpikeContext context = evaluator.evaluateMa20VolumeSpike(
                dailyMa20Snapshot,
                latestOneMinuteKline,
                new BigDecimal("1000000")
        );

        assertNull(context);
    }

    @Test
    void shouldRejectWhenOneMinuteKlineIsFalling() {
        List<BinanceKlineDTO> dailyKlines = sequentialDailyKlines("BTCUSDT", 91, 20);
        BinanceKlineDTO latestOneMinuteKline = oneMinuteKline("BTCUSDT", 3000000, 1441L);
        latestOneMinuteKline.setOpen("101");
        latestOneMinuteKline.setClose("100");
        DailyMa20Snapshot dailyMa20Snapshot = new DailyMa20Snapshot(
                dailyKlines.get(dailyKlines.size() - 1),
                new BigDecimal("100.5"),
                new BigDecimal("120"),
                new BigDecimal("90"),
                System.currentTimeMillis() + 60_000L
        );

        Ma20VolumeSpikeContext context = evaluator.evaluateMa20VolumeSpike(
                dailyMa20Snapshot,
                latestOneMinuteKline,
                new BigDecimal("1000000")
        );

        assertNull(context);
    }

    private List<BinanceKlineDTO> sequentialDailyKlines(String symbol, int startClose, int count) {
        List<BinanceKlineDTO> klines = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            klines.add(dailyKline(symbol, startClose + i, i + 1));
        }
        return klines;
    }

    private List<BinanceKlineDTO> dailyKlinesBelowMa20(String symbol) {
        List<BinanceKlineDTO> klines = new ArrayList<>();
        for (int i = 0; i < 19; i++) {
            klines.add(dailyKline(symbol, 101 + i, i + 1));
        }
        klines.add(dailyKline(symbol, 100, 20));
        return klines;
    }

    private BinanceKlineDTO dailyKline(String symbol, int close, int dayIndex) {
        BinanceKlineDTO dto = new BinanceKlineDTO();
        dto.setSymbol(symbol);
        dto.setOpen(String.valueOf(close - 1));
        dto.setClose(String.valueOf(close));
        dto.setHigh(String.valueOf(close + 1));
        dto.setLow(String.valueOf(close - 2));
        dto.setVolume("1000000");
        dto.setEndTime(dayIndex * 86_400_000L);
        return dto;
    }

    private BinanceKlineDTO oneMinuteKline(String symbol, int volume, long endTime) {
        BinanceKlineDTO dto = new BinanceKlineDTO();
        dto.setSymbol(symbol);
        dto.setOpen("100");
        dto.setClose("101");
        dto.setHigh("101");
        dto.setLow("99");
        dto.setVolume(String.valueOf(volume));
        dto.setEndTime(endTime);
        return dto;
    }
}
