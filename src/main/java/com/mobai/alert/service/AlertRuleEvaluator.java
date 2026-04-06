package com.mobai.alert.service;

import com.mobai.alert.dto.BinanceKlineDTO;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Service
public class AlertRuleEvaluator {

    @Value("${monitoring.rate.low}")
    private String rateLow;

    @Value("${monitoring.rate.back.low}")
    private String rateBackLow;

    @Value("${monitoring.rate.high}")
    private String rateHigh;

    @Value("${monitoring.alert.volume.low}")
    private String volumeLow;

    @Value("${monitoring.alert.volume.back.low}")
    private String volumeBackLow;

    @Value("${monitoring.alert.volume.high}")
    private String volumeHigh;

    @Value("${monitoring.rate.two.low}")
    private String twoRateLow;

    @Value("${monitoring.volume.two.low}")
    private String twoVolumeLow;

    /**
     * 连续三根上涨的基础条件：
     * 收盘价高于开盘价，振幅在阈值区间内，且成交额命中范围。
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
     * 两连涨使用较宽松的阈值，只校验最低振幅和最低成交额。
     */
    public boolean isContinuousTwoMatch(BinanceKlineDTO kline) {
        if (!isRising(kline)) {
            return false;
        }

        BigDecimal rate = calculateAmplitude(kline);
        if (rate.compareTo(new BigDecimal(twoRateLow)) < 0) {
            return false;
        }

        return new BigDecimal(kline.getVolume()).compareTo(new BigDecimal(twoVolumeLow)) >= 0;
    }

    /**
     * 回踩信号要求当前已收盘 K 线为阴线，并且跌幅、成交额都达到配置阈值。
     */
    public boolean isBacktrackMatch(BinanceKlineDTO kline) {
        BigDecimal open = new BigDecimal(kline.getOpen());
        BigDecimal close = new BigDecimal(kline.getClose());
        if (close.compareTo(open) >= 0) {
            return false;
        }

        BigDecimal rate = open.subtract(close).abs().divide(close, 6, RoundingMode.HALF_UP);
        if (rate.compareTo(new BigDecimal(rateBackLow)) < 0) {
            return false;
        }

        return new BigDecimal(kline.getVolume()).compareTo(new BigDecimal(volumeBackLow)) >= 0;
    }

    private boolean isRising(BinanceKlineDTO kline) {
        BigDecimal open = new BigDecimal(kline.getOpen());
        BigDecimal close = new BigDecimal(kline.getClose());
        return close.compareTo(open) > 0;
    }

    private BigDecimal calculateAmplitude(BinanceKlineDTO kline) {
        // 振幅统一按 (high - low) / low 计算，返回小数值而非百分比。
        BigDecimal high = new BigDecimal(kline.getHigh());
        BigDecimal low = new BigDecimal(kline.getLow());
        return high.subtract(low).divide(low, 6, RoundingMode.HALF_UP);
    }
}
