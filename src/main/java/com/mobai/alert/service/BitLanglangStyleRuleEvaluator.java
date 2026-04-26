package com.mobai.alert.service;

import com.mobai.alert.dto.BinanceKlineDTO;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Profile-driven evaluator for BTC long and short opportunities.
 */
@Service
public class BitLanglangStyleRuleEvaluator {

    public List<BitLanglangStyleSignal> evaluate(String symbol,
                                                 List<BinanceKlineDTO> oneMinuteKlines,
                                                 List<BinanceKlineDTO> fiveMinuteKlines,
                                                 List<BinanceKlineDTO> fifteenMinuteKlines,
                                                 BitLanglangTradeProfile profile) {
        if (CollectionUtils.isEmpty(oneMinuteKlines)
                || CollectionUtils.isEmpty(fiveMinuteKlines)
                || CollectionUtils.isEmpty(fifteenMinuteKlines)
                || profile == null) {
            return List.of();
        }

        int requiredFiveMinuteCount = profile.getBreakoutLookback() + 1;
        if (fiveMinuteKlines.size() < requiredFiveMinuteCount || fifteenMinuteKlines.size() < 4) {
            return List.of();
        }

        BinanceKlineDTO latestOneMinute = oneMinuteKlines.get(oneMinuteKlines.size() - 1);
        BinanceKlineDTO latestFiveMinute = fiveMinuteKlines.get(fiveMinuteKlines.size() - 1);
        List<BinanceKlineDTO> breakoutHistory = fiveMinuteKlines.subList(
                fiveMinuteKlines.size() - requiredFiveMinuteCount,
                fiveMinuteKlines.size() - 1
        );
        List<BinanceKlineDTO> fifteenMinuteWindow = fifteenMinuteKlines.subList(fifteenMinuteKlines.size() - 4, fifteenMinuteKlines.size());
        BinanceKlineDTO latestFifteenMinute = fifteenMinuteWindow.get(fifteenMinuteWindow.size() - 1);
        boolean activeHour = profile.isActiveHour(AppTime.now().getHour());

        List<BitLanglangStyleSignal> signals = new ArrayList<>(2);
        BitLanglangStyleSignal longSignal = evaluateLong(
                symbol,
                latestOneMinute,
                latestFiveMinute,
                breakoutHistory,
                fifteenMinuteWindow,
                latestFifteenMinute,
                profile,
                activeHour
        );
        if (longSignal != null) {
            signals.add(longSignal);
        }

        BitLanglangStyleSignal shortSignal = evaluateShort(
                symbol,
                latestOneMinute,
                latestFiveMinute,
                breakoutHistory,
                fifteenMinuteWindow,
                latestFifteenMinute,
                profile,
                activeHour
        );
        if (shortSignal != null) {
            signals.add(shortSignal);
        }
        return signals;
    }

    private BitLanglangStyleSignal evaluateLong(String symbol,
                                                BinanceKlineDTO latestOneMinute,
                                                BinanceKlineDTO latestFiveMinute,
                                                List<BinanceKlineDTO> breakoutHistory,
                                                List<BinanceKlineDTO> fifteenMinuteWindow,
                                                BinanceKlineDTO latestFifteenMinute,
                                                BitLanglangTradeProfile profile,
                                                boolean activeHour) {
        BigDecimal oneMinuteRate = riseRate(latestOneMinute);
        BigDecimal breakoutBaseHigh = breakoutHistory.stream()
                .map(this::highPrice)
                .max(BigDecimal::compareTo)
                .orElse(BigDecimal.ZERO);
        if (breakoutBaseHigh.signum() <= 0) {
            return null;
        }
        BigDecimal breakoutRate = closePrice(latestFiveMinute)
                .subtract(breakoutBaseHigh)
                .divide(breakoutBaseHigh, 6, RoundingMode.HALF_UP);
        BigDecimal volumeMultiplier = volumeMultiplier(latestFiveMinute, breakoutHistory);
        BigDecimal trendRate = trendRateUp(fifteenMinuteWindow);
        BigDecimal priorMaxClose = fifteenMinuteWindow.subList(0, fifteenMinuteWindow.size() - 1).stream()
                .map(this::closePrice)
                .max(BigDecimal::compareTo)
                .orElse(BigDecimal.ZERO);

        int score = 0;
        if (oneMinuteRate.compareTo(profile.getLongOneMinuteRateMin()) >= 0) {
            score += 30;
        }
        if (breakoutRate.compareTo(profile.getLongFiveMinuteRateMin()) >= 0
                && closePrice(latestFiveMinute).compareTo(breakoutBaseHigh) > 0) {
            score += 30;
        }
        if (volumeMultiplier.compareTo(profile.getVolumeMultiplierMin()) >= 0) {
            score += 20;
        }
        if (trendRate.compareTo(profile.getLongFifteenMinuteTrendRateMin()) >= 0
                && closePrice(latestFifteenMinute).compareTo(priorMaxClose) > 0) {
            score += 20;
        }
        if (activeHour) {
            score += 10;
        }

        if (score < 100 || !isRising(latestOneMinute) || !isRising(latestFiveMinute)) {
            return null;
        }
        return new BitLanglangStyleSignal(
                symbol,
                BitLanglangTradeSide.LONG,
                latestOneMinute,
                latestFiveMinute,
                latestFifteenMinute,
                oneMinuteRate,
                breakoutRate,
                volumeMultiplier,
                trendRate,
                activeHour,
                score
        );
    }

    private BitLanglangStyleSignal evaluateShort(String symbol,
                                                 BinanceKlineDTO latestOneMinute,
                                                 BinanceKlineDTO latestFiveMinute,
                                                 List<BinanceKlineDTO> breakoutHistory,
                                                 List<BinanceKlineDTO> fifteenMinuteWindow,
                                                 BinanceKlineDTO latestFifteenMinute,
                                                 BitLanglangTradeProfile profile,
                                                 boolean activeHour) {
        BigDecimal oneMinuteRate = dropRate(latestOneMinute);
        BigDecimal breakoutBaseLow = breakoutHistory.stream()
                .map(this::lowPrice)
                .min(BigDecimal::compareTo)
                .orElse(BigDecimal.ZERO);
        if (breakoutBaseLow.signum() <= 0) {
            return null;
        }
        BigDecimal breakoutRate = breakoutBaseLow
                .subtract(closePrice(latestFiveMinute))
                .divide(breakoutBaseLow, 6, RoundingMode.HALF_UP);
        BigDecimal volumeMultiplier = volumeMultiplier(latestFiveMinute, breakoutHistory);
        BigDecimal trendRate = trendRateDown(fifteenMinuteWindow);
        BigDecimal priorMinClose = fifteenMinuteWindow.subList(0, fifteenMinuteWindow.size() - 1).stream()
                .map(this::closePrice)
                .min(BigDecimal::compareTo)
                .orElse(BigDecimal.ZERO);

        int score = 0;
        if (oneMinuteRate.compareTo(profile.getShortOneMinuteRateMin()) >= 0) {
            score += 30;
        }
        if (breakoutRate.compareTo(profile.getShortFiveMinuteRateMin()) >= 0
                && closePrice(latestFiveMinute).compareTo(breakoutBaseLow) < 0) {
            score += 30;
        }
        if (volumeMultiplier.compareTo(profile.getVolumeMultiplierMin()) >= 0) {
            score += 20;
        }
        if (trendRate.compareTo(profile.getShortFifteenMinuteTrendRateMin()) >= 0
                && closePrice(latestFifteenMinute).compareTo(priorMinClose) < 0) {
            score += 20;
        }
        if (activeHour) {
            score += 10;
        }

        if (score < 100 || !isFalling(latestOneMinute) || !isFalling(latestFiveMinute)) {
            return null;
        }
        return new BitLanglangStyleSignal(
                symbol,
                BitLanglangTradeSide.SHORT,
                latestOneMinute,
                latestFiveMinute,
                latestFifteenMinute,
                oneMinuteRate,
                breakoutRate,
                volumeMultiplier,
                trendRate,
                activeHour,
                score
        );
    }

    private BigDecimal volumeMultiplier(BinanceKlineDTO latestFiveMinute, List<BinanceKlineDTO> breakoutHistory) {
        BigDecimal total = BigDecimal.ZERO;
        for (BinanceKlineDTO kline : breakoutHistory) {
            total = total.add(volume(kline));
        }
        BigDecimal average = total.divide(BigDecimal.valueOf(breakoutHistory.size()), 6, RoundingMode.HALF_UP);
        if (average.signum() <= 0) {
            return BigDecimal.ZERO;
        }
        return volume(latestFiveMinute).divide(average, 6, RoundingMode.HALF_UP);
    }

    private BigDecimal trendRateUp(List<BinanceKlineDTO> fifteenMinuteWindow) {
        BigDecimal baseOpen = openPrice(fifteenMinuteWindow.get(0));
        if (baseOpen.signum() <= 0) {
            return BigDecimal.ZERO;
        }
        return closePrice(fifteenMinuteWindow.get(fifteenMinuteWindow.size() - 1))
                .subtract(baseOpen)
                .divide(baseOpen, 6, RoundingMode.HALF_UP);
    }

    private BigDecimal trendRateDown(List<BinanceKlineDTO> fifteenMinuteWindow) {
        BigDecimal baseOpen = openPrice(fifteenMinuteWindow.get(0));
        if (baseOpen.signum() <= 0) {
            return BigDecimal.ZERO;
        }
        return baseOpen
                .subtract(closePrice(fifteenMinuteWindow.get(fifteenMinuteWindow.size() - 1)))
                .divide(baseOpen, 6, RoundingMode.HALF_UP);
    }

    private boolean isRising(BinanceKlineDTO kline) {
        return closePrice(kline).compareTo(openPrice(kline)) > 0;
    }

    private boolean isFalling(BinanceKlineDTO kline) {
        return closePrice(kline).compareTo(openPrice(kline)) < 0;
    }

    private BigDecimal riseRate(BinanceKlineDTO kline) {
        if (!isRising(kline)) {
            return BigDecimal.ZERO;
        }
        BigDecimal open = openPrice(kline);
        if (open.signum() <= 0) {
            return BigDecimal.ZERO;
        }
        return closePrice(kline).subtract(open).divide(open, 6, RoundingMode.HALF_UP);
    }

    private BigDecimal dropRate(BinanceKlineDTO kline) {
        if (!isFalling(kline)) {
            return BigDecimal.ZERO;
        }
        BigDecimal open = openPrice(kline);
        if (open.signum() <= 0) {
            return BigDecimal.ZERO;
        }
        return open.subtract(closePrice(kline)).divide(open, 6, RoundingMode.HALF_UP);
    }

    private BigDecimal openPrice(BinanceKlineDTO kline) {
        return new BigDecimal(kline.getOpen());
    }

    private BigDecimal closePrice(BinanceKlineDTO kline) {
        return new BigDecimal(kline.getClose());
    }

    private BigDecimal highPrice(BinanceKlineDTO kline) {
        return new BigDecimal(kline.getHigh());
    }

    private BigDecimal lowPrice(BinanceKlineDTO kline) {
        return new BigDecimal(kline.getLow());
    }

    private BigDecimal volume(BinanceKlineDTO kline) {
        return new BigDecimal(kline.getVolume());
    }
}
