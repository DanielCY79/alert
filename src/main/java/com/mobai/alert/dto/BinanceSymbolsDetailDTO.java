package com.mobai.alert.dto;

/**
 * 单个交易对的基础信息。
 */
public class BinanceSymbolsDetailDTO {

    /**
     * 交易对名称，例如 BTCUSDT。
     */
    private String symbol;

    /**
     * 交易状态，例如 TRADING 表示可交易。
     */
    private String status;

    public String getSymbol() {
        return symbol;
    }

    public void setSymbol(String symbol) {
        this.symbol = symbol;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }
}
