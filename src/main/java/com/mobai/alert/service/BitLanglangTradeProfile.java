package com.mobai.alert.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.Path;
import java.util.List;

/**
 * Trading profile derived from historical BitLanglang workbook data.
 */
public class BitLanglangTradeProfile {

    private final Path sourcePath;
    private final long sourceLastModified;
    private final int tradeCount;
    private final String primarySymbol;
    private final List<Integer> activeHours;
    private final BigDecimal medianHoldMinutes;
    private final BigDecimal medianLeverage;
    private final BigDecimal longShare;
    private final BigDecimal shortShare;
    private final int breakoutLookback;
    private final BigDecimal volumeMultiplierMin;
    private final BigDecimal longOneMinuteRateMin;
    private final BigDecimal shortOneMinuteRateMin;
    private final BigDecimal longFiveMinuteRateMin;
    private final BigDecimal shortFiveMinuteRateMin;
    private final BigDecimal longFifteenMinuteTrendRateMin;
    private final BigDecimal shortFifteenMinuteTrendRateMin;

    public BitLanglangTradeProfile(Path sourcePath,
                                   long sourceLastModified,
                                   int tradeCount,
                                   String primarySymbol,
                                   List<Integer> activeHours,
                                   BigDecimal medianHoldMinutes,
                                   BigDecimal medianLeverage,
                                   BigDecimal longShare,
                                   BigDecimal shortShare,
                                   int breakoutLookback,
                                   BigDecimal volumeMultiplierMin,
                                   BigDecimal longOneMinuteRateMin,
                                   BigDecimal shortOneMinuteRateMin,
                                   BigDecimal longFiveMinuteRateMin,
                                   BigDecimal shortFiveMinuteRateMin,
                                   BigDecimal longFifteenMinuteTrendRateMin,
                                   BigDecimal shortFifteenMinuteTrendRateMin) {
        this.sourcePath = sourcePath;
        this.sourceLastModified = sourceLastModified;
        this.tradeCount = tradeCount;
        this.primarySymbol = primarySymbol;
        this.activeHours = activeHours;
        this.medianHoldMinutes = medianHoldMinutes;
        this.medianLeverage = medianLeverage;
        this.longShare = longShare;
        this.shortShare = shortShare;
        this.breakoutLookback = breakoutLookback;
        this.volumeMultiplierMin = volumeMultiplierMin;
        this.longOneMinuteRateMin = longOneMinuteRateMin;
        this.shortOneMinuteRateMin = shortOneMinuteRateMin;
        this.longFiveMinuteRateMin = longFiveMinuteRateMin;
        this.shortFiveMinuteRateMin = shortFiveMinuteRateMin;
        this.longFifteenMinuteTrendRateMin = longFifteenMinuteTrendRateMin;
        this.shortFifteenMinuteTrendRateMin = shortFifteenMinuteTrendRateMin;
    }

    public Path getSourcePath() {
        return sourcePath;
    }

    public long getSourceLastModified() {
        return sourceLastModified;
    }

    public int getTradeCount() {
        return tradeCount;
    }

    public String getPrimarySymbol() {
        return primarySymbol;
    }

    public List<Integer> getActiveHours() {
        return activeHours;
    }

    public BigDecimal getMedianHoldMinutes() {
        return medianHoldMinutes;
    }

    public BigDecimal getMedianLeverage() {
        return medianLeverage;
    }

    public BigDecimal getLongShare() {
        return longShare;
    }

    public BigDecimal getShortShare() {
        return shortShare;
    }

    public int getBreakoutLookback() {
        return breakoutLookback;
    }

    public BigDecimal getVolumeMultiplierMin() {
        return volumeMultiplierMin;
    }

    public BigDecimal getLongOneMinuteRateMin() {
        return longOneMinuteRateMin;
    }

    public BigDecimal getShortOneMinuteRateMin() {
        return shortOneMinuteRateMin;
    }

    public BigDecimal getLongFiveMinuteRateMin() {
        return longFiveMinuteRateMin;
    }

    public BigDecimal getShortFiveMinuteRateMin() {
        return shortFiveMinuteRateMin;
    }

    public BigDecimal getLongFifteenMinuteTrendRateMin() {
        return longFifteenMinuteTrendRateMin;
    }

    public BigDecimal getShortFifteenMinuteTrendRateMin() {
        return shortFifteenMinuteTrendRateMin;
    }

    public boolean isActiveHour(int hour) {
        return activeHours.contains(hour);
    }

    public String summary() {
        BigDecimal longPercent = longShare.multiply(new BigDecimal("100")).setScale(1, RoundingMode.HALF_UP);
        BigDecimal shortPercent = shortShare.multiply(new BigDecimal("100")).setScale(1, RoundingMode.HALF_UP);
        return tradeCount + " 笔历史，" + primarySymbol
                + "，多/空=" + longPercent + "%/" + shortPercent + "%"
                + "，中位持仓=" + medianHoldMinutes.setScale(1, RoundingMode.HALF_UP) + " 分钟"
                + "，中位杠杆=" + medianLeverage.setScale(1, RoundingMode.HALF_UP) + "x";
    }
}
