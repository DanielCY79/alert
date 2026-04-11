package com.mobai.alert.service;

import com.mobai.alert.dto.BinanceKlineDTO;

import java.math.BigDecimal;

/**
 * Directional opportunity signal generated from the fitted BitLanglang profile.
 */
public class BitLanglangStyleSignal {

    private final String symbol;
    private final BitLanglangTradeSide side;
    private final BinanceKlineDTO oneMinuteKline;
    private final BinanceKlineDTO fiveMinuteKline;
    private final BinanceKlineDTO fifteenMinuteKline;
    private final BigDecimal oneMinuteRate;
    private final BigDecimal fiveMinuteBreakRate;
    private final BigDecimal fiveMinuteVolumeMultiplier;
    private final BigDecimal fifteenMinuteTrendRate;
    private final boolean activeHour;
    private final int score;

    public BitLanglangStyleSignal(String symbol,
                                  BitLanglangTradeSide side,
                                  BinanceKlineDTO oneMinuteKline,
                                  BinanceKlineDTO fiveMinuteKline,
                                  BinanceKlineDTO fifteenMinuteKline,
                                  BigDecimal oneMinuteRate,
                                  BigDecimal fiveMinuteBreakRate,
                                  BigDecimal fiveMinuteVolumeMultiplier,
                                  BigDecimal fifteenMinuteTrendRate,
                                  boolean activeHour,
                                  int score) {
        this.symbol = symbol;
        this.side = side;
        this.oneMinuteKline = oneMinuteKline;
        this.fiveMinuteKline = fiveMinuteKline;
        this.fifteenMinuteKline = fifteenMinuteKline;
        this.oneMinuteRate = oneMinuteRate;
        this.fiveMinuteBreakRate = fiveMinuteBreakRate;
        this.fiveMinuteVolumeMultiplier = fiveMinuteVolumeMultiplier;
        this.fifteenMinuteTrendRate = fifteenMinuteTrendRate;
        this.activeHour = activeHour;
        this.score = score;
    }

    public String getSymbol() {
        return symbol;
    }

    public BitLanglangTradeSide getSide() {
        return side;
    }

    public BinanceKlineDTO getOneMinuteKline() {
        return oneMinuteKline;
    }

    public BinanceKlineDTO getFiveMinuteKline() {
        return fiveMinuteKline;
    }

    public BinanceKlineDTO getFifteenMinuteKline() {
        return fifteenMinuteKline;
    }

    public BigDecimal getOneMinuteRate() {
        return oneMinuteRate;
    }

    public BigDecimal getFiveMinuteBreakRate() {
        return fiveMinuteBreakRate;
    }

    public BigDecimal getFiveMinuteVolumeMultiplier() {
        return fiveMinuteVolumeMultiplier;
    }

    public BigDecimal getFifteenMinuteTrendRate() {
        return fifteenMinuteTrendRate;
    }

    public boolean isActiveHour() {
        return activeHour;
    }

    public int getScore() {
        return score;
    }

    public String buildDedupKey() {
        return symbol + ":" + side + ":" + fiveMinuteKline.getEndTime();
    }
}
