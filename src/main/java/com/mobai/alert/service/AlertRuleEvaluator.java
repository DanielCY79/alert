package com.mobai.alert.service;

import com.mobai.alert.dto.BinanceKlineDTO;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/**
 * 告警规则计算器，负责判断保留的 type=1 和 type=2 条件是否命中。
 */
@Service
public class AlertRuleEvaluator {

    private static final int MA20_PERIOD = 20;

    @Value("${monitoring.type1.average-volume-5d.max:300000000}")
    private String type1AverageVolume5dMax = "300000000";

    @Value("${monitoring.type1.one-minute-volume.min:80000}")
    private String type1OneMinuteVolumeMin = "80000";

    @Value("${monitoring.type1.one-minute-amplitude.min:0.01}")
    private String type1OneMinuteAmplitudeMin = "0.01";

    @Value("${monitoring.type1.one-minute-amplitude.max:0.50}")
    private String type1OneMinuteAmplitudeMax = "0.50";

    @Value("${monitoring.type2.average-volume-7d.max:500000000}")
    private String type2AverageVolume7dMax = "500000000";

    @Value("${monitoring.type2.one-minute-volume.min:200000}")
    private String type2OneMinuteVolumeMin = "200000";

    @Value("${monitoring.type2.previous-one-minute-volume.min:40000}")
    private String type2PreviousOneMinuteVolumeMin = "40000";

    @Value("${monitoring.type2.one-minute-amplitude.min:0.01}")
    private String type2OneMinuteAmplitudeMin = "0.01";

    @Value("${monitoring.type2.one-minute-amplitude.max:0.50}")
    private String type2OneMinuteAmplitudeMax = "0.50";

    /**
     * 判断单根 1m K 线是否满足 type=1 的放量大振幅条件。
     */
    public boolean isTwoOfThreeMomentumKlineMatch(BinanceKlineDTO kline) {
        BigDecimal volume = new BigDecimal(kline.getVolume());
        return volume.compareTo(new BigDecimal(type1OneMinuteVolumeMin)) > 0
                && isAmplitudeInRange(
                        calculateAmplitude(kline),
                        type1OneMinuteAmplitudeMin,
                        type1OneMinuteAmplitudeMax
                );
    }

    /**
     * Type 1 precheck before loading extra 15m/1h/4h K lines.
     */
    public boolean shouldEvaluateTwoOfThreeMomentumSignal(DailyMa20Snapshot dailyMa20Snapshot,
                                                          BinanceKlineDTO latestOneMinuteKline,
                                                          List<BinanceKlineDTO> recentThreeOneMinuteKlines) {
        return matchesTwoOfThreeMomentumBaseConditions(dailyMa20Snapshot, latestOneMinuteKline)
                && recentThreeOneMinuteKlines != null
                && recentThreeOneMinuteKlines.size() == 3
                && countMatches(recentThreeOneMinuteKlines, this::isTwoOfThreeMomentumKlineMatch) >= 2;
    }

    /**
     * Type 1 rule:
     * 5d average quote volume < 300M USDT, price above 1d/4h/1h/15m MA20,
     * and at least 2 of the latest 3 one-minute candles have quote volume > 80K USDT
     * and amplitude in [1%, 50%].
     */
    public TwoOfThreeMomentumSignalContext evaluateTwoOfThreeMomentumSignal(
            DailyMa20Snapshot dailyMa20Snapshot,
            BinanceKlineDTO latestOneMinuteKline,
            List<BinanceKlineDTO> recentThreeOneMinuteKlines,
            List<BinanceKlineDTO> fifteenMinuteKlines,
            List<BinanceKlineDTO> oneHourKlines,
            List<BinanceKlineDTO> fourHourKlines) {
        if (!shouldEvaluateTwoOfThreeMomentumSignal(
                dailyMa20Snapshot,
                latestOneMinuteKline,
                recentThreeOneMinuteKlines
        )) {
            return null;
        }

        BigDecimal currentPrice = closePrice(latestOneMinuteKline);
        BigDecimal fifteenMinuteMa20 = calculateCloseMa(fifteenMinuteKlines, MA20_PERIOD);
        BigDecimal oneHourMa20 = calculateCloseMa(oneHourKlines, MA20_PERIOD);
        BigDecimal fourHourMa20 = calculateCloseMa(fourHourKlines, MA20_PERIOD);
        if (fifteenMinuteMa20 == null || oneHourMa20 == null || fourHourMa20 == null) {
            return null;
        }

        if (currentPrice.compareTo(fifteenMinuteMa20) <= 0
                || currentPrice.compareTo(oneHourMa20) <= 0
                || currentPrice.compareTo(fourHourMa20) <= 0) {
            return null;
        }

        return new TwoOfThreeMomentumSignalContext(
                latestOneMinuteKline,
                currentPrice,
                dailyMa20Snapshot.getAverageVolume5d(),
                dailyMa20Snapshot.getMa20(),
                fourHourMa20,
                oneHourMa20,
                fifteenMinuteMa20,
                countMatches(recentThreeOneMinuteKlines, this::isTwoOfThreeMomentumKlineMatch)
        );
    }

    /**
     * Type 2 rule precheck before loading extra 15m/1h/4h K lines.
     */
    public boolean shouldEvaluateLowVolumeMa20Signal(DailyMa20Snapshot dailyMa20Snapshot,
                                                     BinanceKlineDTO latestOneMinuteKline,
                                                     BinanceKlineDTO previousOneMinuteKline) {
        return matchesLowVolumeMa20BaseConditions(
                dailyMa20Snapshot,
                latestOneMinuteKline,
                previousOneMinuteKline
        );
    }

    /**
     * Type 2 independent rule:
     * 7d average quote volume < 500M USDT, price above 1d/4h/1h/15m MA20,
     * latest 1m quote volume > 200K USDT, previous 1m quote volume > 40K USDT,
     * and latest 1m amplitude in [1%, 50%].
     */
    public LowVolumeMa20SignalContext evaluateLowVolumeMa20Signal(DailyMa20Snapshot dailyMa20Snapshot,
                                                                   BinanceKlineDTO latestOneMinuteKline,
                                                                   BinanceKlineDTO previousOneMinuteKline,
                                                                   List<BinanceKlineDTO> fifteenMinuteKlines,
                                                                   List<BinanceKlineDTO> oneHourKlines,
                                                                   List<BinanceKlineDTO> fourHourKlines) {
        if (!matchesLowVolumeMa20BaseConditions(
                dailyMa20Snapshot,
                latestOneMinuteKline,
                previousOneMinuteKline
        )) {
            return null;
        }

        BigDecimal currentPrice = closePrice(latestOneMinuteKline);
        BigDecimal fifteenMinuteMa20 = calculateCloseMa(fifteenMinuteKlines, MA20_PERIOD);
        BigDecimal oneHourMa20 = calculateCloseMa(oneHourKlines, MA20_PERIOD);
        BigDecimal fourHourMa20 = calculateCloseMa(fourHourKlines, MA20_PERIOD);
        if (fifteenMinuteMa20 == null || oneHourMa20 == null || fourHourMa20 == null) {
            return null;
        }

        if (currentPrice.compareTo(fifteenMinuteMa20) <= 0
                || currentPrice.compareTo(oneHourMa20) <= 0
                || currentPrice.compareTo(fourHourMa20) <= 0) {
            return null;
        }

        return new LowVolumeMa20SignalContext(
                latestOneMinuteKline,
                currentPrice,
                dailyMa20Snapshot.getAverageVolume7d(),
                dailyMa20Snapshot.getMa20(),
                fourHourMa20,
                oneHourMa20,
                fifteenMinuteMa20,
                new BigDecimal(latestOneMinuteKline.getVolume()),
                new BigDecimal(previousOneMinuteKline.getVolume()),
                calculateAmplitude(latestOneMinuteKline)
        );
    }

    private boolean matchesTwoOfThreeMomentumBaseConditions(DailyMa20Snapshot dailyMa20Snapshot,
                                                            BinanceKlineDTO latestOneMinuteKline) {
        if (dailyMa20Snapshot == null
                || latestOneMinuteKline == null
                || dailyMa20Snapshot.getMa20() == null
                || dailyMa20Snapshot.getAverageVolume5d() == null) {
            return false;
        }

        if (dailyMa20Snapshot.getAverageVolume5d().compareTo(new BigDecimal(type1AverageVolume5dMax)) >= 0) {
            return false;
        }

        return closePrice(latestOneMinuteKline).compareTo(dailyMa20Snapshot.getMa20()) > 0;
    }

    private boolean matchesLowVolumeMa20BaseConditions(DailyMa20Snapshot dailyMa20Snapshot,
                                                       BinanceKlineDTO latestOneMinuteKline,
                                                       BinanceKlineDTO previousOneMinuteKline) {
        if (dailyMa20Snapshot == null
                || latestOneMinuteKline == null
                || previousOneMinuteKline == null
                || dailyMa20Snapshot.getMa20() == null
                || dailyMa20Snapshot.getAverageVolume7d() == null) {
            return false;
        }

        if (dailyMa20Snapshot.getAverageVolume7d().compareTo(new BigDecimal(type2AverageVolume7dMax)) >= 0) {
            return false;
        }

        if (closePrice(latestOneMinuteKline).compareTo(dailyMa20Snapshot.getMa20()) <= 0) {
            return false;
        }

        if (new BigDecimal(latestOneMinuteKline.getVolume()).compareTo(new BigDecimal(type2OneMinuteVolumeMin)) <= 0) {
            return false;
        }

        if (new BigDecimal(previousOneMinuteKline.getVolume())
                .compareTo(new BigDecimal(type2PreviousOneMinuteVolumeMin)) <= 0) {
            return false;
        }

        return isAmplitudeInRange(
                calculateAmplitude(latestOneMinuteKline),
                type2OneMinuteAmplitudeMin,
                type2OneMinuteAmplitudeMax
        );
    }

    private BigDecimal calculateCloseMa(List<BinanceKlineDTO> klines, int period) {
        if (klines == null || klines.size() < period) {
            return null;
        }

        BigDecimal total = BigDecimal.ZERO;
        List<BinanceKlineDTO> recentKlines = klines.subList(klines.size() - period, klines.size());
        for (BinanceKlineDTO kline : recentKlines) {
            total = total.add(closePrice(kline));
        }
        return total.divide(BigDecimal.valueOf(period), 6, RoundingMode.HALF_UP);
    }

    private BigDecimal closePrice(BinanceKlineDTO kline) {
        return new BigDecimal(kline.getClose());
    }

    /**
     * 振幅统一按 (high - low) / low 计算，返回小数值而非百分比。
     */
    private BigDecimal calculateAmplitude(BinanceKlineDTO kline) {
        BigDecimal high = new BigDecimal(kline.getHigh());
        BigDecimal low = new BigDecimal(kline.getLow());
        return high.subtract(low).divide(low, 6, RoundingMode.HALF_UP);
    }

    private boolean isAmplitudeInRange(BigDecimal amplitude, String min, String max) {
        return amplitude.compareTo(new BigDecimal(min)) >= 0
                && amplitude.compareTo(new BigDecimal(max)) <= 0;
    }

    private int countMatches(List<BinanceKlineDTO> klines, java.util.function.Predicate<BinanceKlineDTO> predicate) {
        if (klines == null) {
            return 0;
        }
        int count = 0;
        for (BinanceKlineDTO kline : klines) {
            if (predicate.test(kline)) {
                count++;
            }
        }
        return count;
    }
}
