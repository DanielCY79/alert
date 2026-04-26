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

    @Value("${monitoring.rate.low}")
    private String rateLow;

    @Value("${monitoring.rate.high}")
    private String rateHigh;

    @Value("${monitoring.alert.volume.low}")
    private String volumeLow;

    @Value("${monitoring.alert.volume.high}")
    private String volumeHigh;

    @Value("${monitoring.type2.average-volume-7d.max:500000000}")
    private String type2AverageVolume7dMax = "500000000";

    @Value("${monitoring.type2.one-minute-volume.min:200000}")
    private String type2OneMinuteVolumeMin = "200000";

    @Value("${monitoring.type2.one-minute-amplitude.min:0.10}")
    private String type2OneMinuteAmplitudeMin = "0.10";

    /**
     * 判断单根 K 线是否满足“三连涨”使用的基础条件。
     * 要求为阳线，振幅落在设定区间内，且成交额位于阈值范围内。
     */
    public boolean isContinuousThreeMatch(BinanceKlineDTO kline) {
        if (!isRising(kline)) {
            return false;
        }

        BigDecimal rate = calculateAmplitude(kline);
        if (rate.compareTo(new BigDecimal(rateLow)) < 0 || rate.compareTo(new BigDecimal(rateHigh)) > 0) {
            return false;
        }

        BigDecimal volume = new BigDecimal(kline.getVolume());
        return volume.compareTo(new BigDecimal(volumeLow)) >= 0
                && volume.compareTo(new BigDecimal(volumeHigh)) <= 0;
    }

    /**
     * Type 2 rule precheck before loading extra 15m/1h/4h K lines.
     */
    public boolean shouldEvaluateLowVolumeMa20Signal(DailyMa20Snapshot dailyMa20Snapshot,
                                                     BinanceKlineDTO latestOneMinuteKline) {
        return matchesLowVolumeMa20BaseConditions(dailyMa20Snapshot, latestOneMinuteKline);
    }

    /**
     * Type 2 independent rule:
     * 7d average quote volume < 500M USDT, price above 1d/4h/1h/15m MA20,
     * 1m quote volume > 200K USDT, and 1m amplitude > 10%.
     */
    public LowVolumeMa20SignalContext evaluateLowVolumeMa20Signal(DailyMa20Snapshot dailyMa20Snapshot,
                                                                  BinanceKlineDTO latestOneMinuteKline,
                                                                  List<BinanceKlineDTO> fifteenMinuteKlines,
                                                                  List<BinanceKlineDTO> oneHourKlines,
                                                                  List<BinanceKlineDTO> fourHourKlines) {
        if (!matchesLowVolumeMa20BaseConditions(dailyMa20Snapshot, latestOneMinuteKline)) {
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
                calculateAmplitude(latestOneMinuteKline)
        );
    }

    /**
     * 判断当前 K 线是否为阳线。
     */
    private boolean isRising(BinanceKlineDTO kline) {
        BigDecimal open = new BigDecimal(kline.getOpen());
        BigDecimal close = new BigDecimal(kline.getClose());
        return close.compareTo(open) > 0;
    }

    private boolean matchesLowVolumeMa20BaseConditions(DailyMa20Snapshot dailyMa20Snapshot,
                                                       BinanceKlineDTO latestOneMinuteKline) {
        if (dailyMa20Snapshot == null
                || latestOneMinuteKline == null
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

        return calculateAmplitude(latestOneMinuteKline).compareTo(new BigDecimal(type2OneMinuteAmplitudeMin)) > 0;
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
}
