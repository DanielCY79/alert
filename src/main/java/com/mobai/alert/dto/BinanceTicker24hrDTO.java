package com.mobai.alert.dto;

/**
 * Binance 24 小时统计快照。
 */
public class BinanceTicker24hrDTO {

    private String symbol;
    private String quoteVolume;

    public String getSymbol() {
        return symbol;
    }

    public void setSymbol(String symbol) {
        this.symbol = symbol;
    }

    public String getQuoteVolume() {
        return quoteVolume;
    }

    public void setQuoteVolume(String quoteVolume) {
        this.quoteVolume = quoteVolume;
    }
}
