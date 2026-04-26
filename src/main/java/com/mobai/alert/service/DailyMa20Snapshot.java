package com.mobai.alert.service;

import java.math.BigDecimal;

/**
 * 单个交易对的日 K MA20 和 7 日平均成交额快照。
 */
public class DailyMa20Snapshot {

    private final BigDecimal ma20;
    private final BigDecimal averageVolume7d;
    private final long refreshAfterMillis;

    public DailyMa20Snapshot(BigDecimal ma20,
                             BigDecimal averageVolume7d,
                             long refreshAfterMillis) {
        this.ma20 = ma20;
        this.averageVolume7d = averageVolume7d;
        this.refreshAfterMillis = refreshAfterMillis;
    }

    public BigDecimal getMa20() {
        return ma20;
    }

    public BigDecimal getAverageVolume7d() {
        return averageVolume7d;
    }

    public long getRefreshAfterMillis() {
        return refreshAfterMillis;
    }
}
