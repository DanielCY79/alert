package com.mobai.alert.service;

import com.mobai.alert.dto.BinanceSymbolsDetailDTO;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AlertSymbolFilterTest {

    @Test
    void shouldRejectBtcAndEthBaseSymbols() {
        assertFalse(AlertSymbolFilter.isMonitorCandidate(tradingSymbol("BTCUSDT")));
        assertFalse(AlertSymbolFilter.isMonitorCandidate(tradingSymbol("ETHUSDT")));
        assertFalse(AlertSymbolFilter.isMonitorCandidate(tradingSymbol("BTCUSDT_260626")));
        assertFalse(AlertSymbolFilter.isMonitorCandidate(tradingSymbol("ETHBTC")));
    }

    @Test
    void shouldNotRejectOtherSymbolsThatOnlyContainEthText() {
        assertTrue(AlertSymbolFilter.isMonitorCandidate(tradingSymbol("ETHFIUSDT")));
        assertTrue(AlertSymbolFilter.isMonitorCandidate(tradingSymbol("NEIROETHUSDT")));
    }

    private BinanceSymbolsDetailDTO tradingSymbol(String symbol) {
        BinanceSymbolsDetailDTO dto = new BinanceSymbolsDetailDTO();
        dto.setSymbol(symbol);
        dto.setStatus("TRADING");
        return dto;
    }
}
