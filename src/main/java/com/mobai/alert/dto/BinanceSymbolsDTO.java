package com.mobai.alert.dto;

import java.util.List;

/**
 * Binance 交易对列表响应对象。
 */
public class BinanceSymbolsDTO {

    /**
     * 交易对明细列表。
     */
    private List<BinanceSymbolsDetailDTO> symbols;

    public List<BinanceSymbolsDetailDTO> getSymbols() {
        return symbols;
    }

    public void setSymbols(List<BinanceSymbolsDetailDTO> symbols) {
        this.symbols = symbols;
    }
}
