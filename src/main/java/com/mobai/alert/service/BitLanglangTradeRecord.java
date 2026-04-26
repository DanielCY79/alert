package com.mobai.alert.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDateTime;

/**
 * Normalized trade record loaded from the workbook.
 */
public class BitLanglangTradeRecord {

    private final int index;
    private final String sourceSymbol;
    private final String normalizedSymbol;
    private final BitLanglangTradeSide side;
    private final BigDecimal leverage;
    private final LocalDateTime entryTime;
    private final LocalDateTime exitTime;
    private final BigDecimal entryPrice;
    private final BigDecimal exitPrice;
    private final BigDecimal returnRate;
    private final BigDecimal profitUsd;
    private final BigDecimal tradeValueUsd;

    public BitLanglangTradeRecord(int index,
                                  String sourceSymbol,
                                  String normalizedSymbol,
                                  BitLanglangTradeSide side,
                                  BigDecimal leverage,
                                  LocalDateTime entryTime,
                                  LocalDateTime exitTime,
                                  BigDecimal entryPrice,
                                  BigDecimal exitPrice,
                                  BigDecimal returnRate,
                                  BigDecimal profitUsd,
                                  BigDecimal tradeValueUsd) {
        this.index = index;
        this.sourceSymbol = sourceSymbol;
        this.normalizedSymbol = normalizedSymbol;
        this.side = side;
        this.leverage = leverage;
        this.entryTime = entryTime;
        this.exitTime = exitTime;
        this.entryPrice = entryPrice;
        this.exitPrice = exitPrice;
        this.returnRate = returnRate;
        this.profitUsd = profitUsd;
        this.tradeValueUsd = tradeValueUsd;
    }

    public int getIndex() {
        return index;
    }

    public String getSourceSymbol() {
        return sourceSymbol;
    }

    public String getNormalizedSymbol() {
        return normalizedSymbol;
    }

    public BitLanglangTradeSide getSide() {
        return side;
    }

    public BigDecimal getLeverage() {
        return leverage;
    }

    public LocalDateTime getEntryTime() {
        return entryTime;
    }

    public LocalDateTime getExitTime() {
        return exitTime;
    }

    public BigDecimal getEntryPrice() {
        return entryPrice;
    }

    public BigDecimal getExitPrice() {
        return exitPrice;
    }

    public BigDecimal getReturnRate() {
        return returnRate;
    }

    public BigDecimal getProfitUsd() {
        return profitUsd;
    }

    public BigDecimal getTradeValueUsd() {
        return tradeValueUsd;
    }

    public BigDecimal getHoldMinutes() {
        long seconds = Duration.between(entryTime, exitTime).getSeconds();
        return BigDecimal.valueOf(seconds).divide(new BigDecimal("60"), 2, RoundingMode.HALF_UP);
    }

    public BigDecimal getDirectionalMoveRate() {
        if (entryPrice.signum() <= 0) {
            return BigDecimal.ZERO;
        }
        BigDecimal priceDelta;
        if (side == BitLanglangTradeSide.LONG) {
            priceDelta = exitPrice.subtract(entryPrice);
        } else {
            priceDelta = entryPrice.subtract(exitPrice);
        }
        return priceDelta.divide(entryPrice, 6, RoundingMode.HALF_UP);
    }
}
