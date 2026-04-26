package com.mobai.alert.service;

import com.mobai.alert.dto.BinanceKlineDTO;

import java.math.BigDecimal;

/**
 * 单个交易对的日 K MA20 快照。
 */
public class DailyMa20Snapshot {

    private final BinanceKlineDTO latestDailyKline;
    private final BigDecimal ma20;
    private final BigDecimal bollingerUpper;
    private final BigDecimal bollingerLower;
    private final long refreshAfterMillis;

    public DailyMa20Snapshot(BinanceKlineDTO latestDailyKline,
                             BigDecimal ma20,
                             BigDecimal bollingerUpper,
                             BigDecimal bollingerLower,
                             long refreshAfterMillis) {
        this.latestDailyKline = latestDailyKline;
        this.ma20 = ma20;
        this.bollingerUpper = bollingerUpper;
        this.bollingerLower = bollingerLower;
        this.refreshAfterMillis = refreshAfterMillis;
    }

    public BinanceKlineDTO getLatestDailyKline() {
        return latestDailyKline;
    }

    public BigDecimal getMa20() {
        return ma20;
    }

    public BigDecimal getBollingerUpper() {
        return bollingerUpper;
    }

    public BigDecimal getBollingerLower() {
        return bollingerLower;
    }

    public long getRefreshAfterMillis() {
        return refreshAfterMillis;
    }
}
