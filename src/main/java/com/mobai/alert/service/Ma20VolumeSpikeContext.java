package com.mobai.alert.service;

import com.mobai.alert.dto.BinanceKlineDTO;

import java.math.BigDecimal;

/**
 * 日 K 站上 MA20 且 1 分钟成交额放量信号的计算结果。
 */
public class Ma20VolumeSpikeContext {

    private final BinanceKlineDTO latestDailyKline;
    private final BinanceKlineDTO latestOneMinuteKline;
    private final BigDecimal ma20;
    private final BigDecimal averageOneMinuteVolume;
    private final BigDecimal currentOneMinuteVolume;
    private final BigDecimal volumeRatio;

    public Ma20VolumeSpikeContext(BinanceKlineDTO latestDailyKline,
                                  BinanceKlineDTO latestOneMinuteKline,
                                  BigDecimal ma20,
                                  BigDecimal averageOneMinuteVolume,
                                  BigDecimal currentOneMinuteVolume,
                                  BigDecimal volumeRatio) {
        this.latestDailyKline = latestDailyKline;
        this.latestOneMinuteKline = latestOneMinuteKline;
        this.ma20 = ma20;
        this.averageOneMinuteVolume = averageOneMinuteVolume;
        this.currentOneMinuteVolume = currentOneMinuteVolume;
        this.volumeRatio = volumeRatio;
    }

    public BinanceKlineDTO getLatestDailyKline() {
        return latestDailyKline;
    }

    public BinanceKlineDTO getLatestOneMinuteKline() {
        return latestOneMinuteKline;
    }

    public BigDecimal getMa20() {
        return ma20;
    }

    public BigDecimal getAverageOneMinuteVolume() {
        return averageOneMinuteVolume;
    }

    public BigDecimal getCurrentOneMinuteVolume() {
        return currentOneMinuteVolume;
    }

    public BigDecimal getVolumeRatio() {
        return volumeRatio;
    }
}
