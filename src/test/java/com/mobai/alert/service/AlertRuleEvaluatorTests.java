package com.mobai.alert.service;

import com.mobai.alert.dto.BinanceKlineDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AlertRuleEvaluatorTests {

    private AlertRuleEvaluator evaluator;

    @BeforeEach
    void setUp() {
        evaluator = new AlertRuleEvaluator();
        ReflectionTestUtils.setField(evaluator, "shortMaPeriod", 10);
        ReflectionTestUtils.setField(evaluator, "middleMaPeriod", 20);
        ReflectionTestUtils.setField(evaluator, "longMaPeriod", 50);
        ReflectionTestUtils.setField(evaluator, "breakoutLookback", 20);
        ReflectionTestUtils.setField(evaluator, "baseLookback", 10);
        ReflectionTestUtils.setField(evaluator, "volumeLookback", 20);
        ReflectionTestUtils.setField(evaluator, "breakoutVolumeMultiplier", 1.6d);
        ReflectionTestUtils.setField(evaluator, "volumeDryFactor", 0.7d);
        ReflectionTestUtils.setField(evaluator, "maxBaseDepth", 0.18d);
        ReflectionTestUtils.setField(evaluator, "stopBuffer", 0.01d);
        ReflectionTestUtils.setField(evaluator, "maxRisk", 0.08d);
        ReflectionTestUtils.setField(evaluator, "minClosePositionInBar", 0.65d);
    }

    @Test
    void shouldMatchStageTwoBreakout() {
        Optional<AlertSignal> signal = evaluator.evaluateStageTwoBreakout(buildBreakoutKlines(false));
        assertTrue(signal.isPresent());
    }

    @Test
    void shouldRejectDeepBaseBreakout() {
        Optional<AlertSignal> signal = evaluator.evaluateStageTwoBreakout(buildBreakoutKlines(true));
        assertFalse(signal.isPresent());
    }

    private List<BinanceKlineDTO> buildBreakoutKlines(boolean deepBase) {
        List<BinanceKlineDTO> klines = new ArrayList<>();
        for (int i = 0; i < 49; i++) {
            double close = 100 + i * 0.8;
            klines.add(kline(close - 0.4, close + 1.0, close - 1.0, close, 120 + i, i));
        }

        klines.add(kline(137.5, 140.0, deepBase ? 110.0 : 136.0, 138.0, 90, 49));
        klines.add(kline(138.0, 139.8, 136.5, 138.5, 85, 50));
        klines.add(kline(138.7, 139.7, 137.0, 139.0, 80, 51));
        klines.add(kline(138.9, 139.8, 137.8, 139.1, 78, 52));
        klines.add(kline(139.0, 139.9, 138.0, 139.3, 75, 53));
        klines.add(kline(139.2, 140.0, 138.2, 139.4, 70, 54));
        klines.add(kline(139.5, 139.95, 138.9, 139.6, 60, 55));
        klines.add(kline(139.6, 139.98, 139.1, 139.7, 58, 56));
        klines.add(kline(139.7, 140.0, 139.2, 139.8, 55, 57));
        klines.add(kline(140.2, 141.8, 139.9, 141.5, 220, 58));
        klines.add(kline(141.4, 141.7, 141.0, 141.3, 65, 59));
        return klines;
    }

    private BinanceKlineDTO kline(double open, double high, double low, double close, double volume, int index) {
        BinanceKlineDTO dto = new BinanceKlineDTO();
        dto.setSymbol("TESTUSDT");
        dto.setInterval("1h");
        dto.setOpen(String.valueOf(open));
        dto.setHigh(String.valueOf(high));
        dto.setLow(String.valueOf(low));
        dto.setClose(String.valueOf(close));
        dto.setVolume(String.valueOf(volume));
        dto.setStartTime(index * 3_600_000L);
        dto.setEndTime((index + 1L) * 3_600_000L);
        return dto;
    }
}
