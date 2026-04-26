package com.mobai.alert.service;

import com.mobai.alert.dto.BinanceKlineDTO;

import java.math.BigDecimal;

/**
 * Type 2 signal details for the low-volume multi-timeframe MA20 rule.
 */
public class LowVolumeMa20SignalContext {

    private final BinanceKlineDTO latestOneMinuteKline;
    private final BigDecimal currentPrice;
    private final BigDecimal averageVolume7d;
    private final BigDecimal dailyMa20;
    private final BigDecimal fourHourMa20;
    private final BigDecimal oneHourMa20;
    private final BigDecimal fifteenMinuteMa20;
    private final BigDecimal oneMinuteVolume;
    private final BigDecimal previousOneMinuteVolume;
    private final BigDecimal oneMinuteAmplitude;

    public LowVolumeMa20SignalContext(BinanceKlineDTO latestOneMinuteKline,
                                      BigDecimal currentPrice,
                                      BigDecimal averageVolume7d,
                                      BigDecimal dailyMa20,
                                      BigDecimal fourHourMa20,
                                      BigDecimal oneHourMa20,
                                      BigDecimal fifteenMinuteMa20,
                                      BigDecimal oneMinuteVolume,
                                      BigDecimal previousOneMinuteVolume,
                                      BigDecimal oneMinuteAmplitude) {
        this.latestOneMinuteKline = latestOneMinuteKline;
        this.currentPrice = currentPrice;
        this.averageVolume7d = averageVolume7d;
        this.dailyMa20 = dailyMa20;
        this.fourHourMa20 = fourHourMa20;
        this.oneHourMa20 = oneHourMa20;
        this.fifteenMinuteMa20 = fifteenMinuteMa20;
        this.oneMinuteVolume = oneMinuteVolume;
        this.previousOneMinuteVolume = previousOneMinuteVolume;
        this.oneMinuteAmplitude = oneMinuteAmplitude;
    }

    public BinanceKlineDTO getLatestOneMinuteKline() {
        return latestOneMinuteKline;
    }

    public BigDecimal getCurrentPrice() {
        return currentPrice;
    }

    public BigDecimal getAverageVolume7d() {
        return averageVolume7d;
    }

    public BigDecimal getDailyMa20() {
        return dailyMa20;
    }

    public BigDecimal getFourHourMa20() {
        return fourHourMa20;
    }

    public BigDecimal getOneHourMa20() {
        return oneHourMa20;
    }

    public BigDecimal getFifteenMinuteMa20() {
        return fifteenMinuteMa20;
    }

    public BigDecimal getOneMinuteVolume() {
        return oneMinuteVolume;
    }

    public BigDecimal getPreviousOneMinuteVolume() {
        return previousOneMinuteVolume;
    }

    public BigDecimal getOneMinuteAmplitude() {
        return oneMinuteAmplitude;
    }
}
