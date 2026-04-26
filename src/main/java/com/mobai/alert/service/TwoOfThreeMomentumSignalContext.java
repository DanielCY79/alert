package com.mobai.alert.service;

import com.mobai.alert.dto.BinanceKlineDTO;

import java.math.BigDecimal;

/**
 * Type 1 signal details for the low-volume multi-timeframe MA20 two-of-three rule.
 */
public class TwoOfThreeMomentumSignalContext {

    private final BinanceKlineDTO latestOneMinuteKline;
    private final BigDecimal currentPrice;
    private final BigDecimal averageVolume5d;
    private final BigDecimal dailyMa20;
    private final BigDecimal fourHourMa20;
    private final BigDecimal oneHourMa20;
    private final BigDecimal fifteenMinuteMa20;
    private final int matchedOneMinuteCount;

    public TwoOfThreeMomentumSignalContext(BinanceKlineDTO latestOneMinuteKline,
                                           BigDecimal currentPrice,
                                           BigDecimal averageVolume5d,
                                           BigDecimal dailyMa20,
                                           BigDecimal fourHourMa20,
                                           BigDecimal oneHourMa20,
                                           BigDecimal fifteenMinuteMa20,
                                           int matchedOneMinuteCount) {
        this.latestOneMinuteKline = latestOneMinuteKline;
        this.currentPrice = currentPrice;
        this.averageVolume5d = averageVolume5d;
        this.dailyMa20 = dailyMa20;
        this.fourHourMa20 = fourHourMa20;
        this.oneHourMa20 = oneHourMa20;
        this.fifteenMinuteMa20 = fifteenMinuteMa20;
        this.matchedOneMinuteCount = matchedOneMinuteCount;
    }

    public BinanceKlineDTO getLatestOneMinuteKline() {
        return latestOneMinuteKline;
    }

    public BigDecimal getCurrentPrice() {
        return currentPrice;
    }

    public BigDecimal getAverageVolume5d() {
        return averageVolume5d;
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

    public int getMatchedOneMinuteCount() {
        return matchedOneMinuteCount;
    }
}
