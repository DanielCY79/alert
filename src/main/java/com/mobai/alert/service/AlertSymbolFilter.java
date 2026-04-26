package com.mobai.alert.service;

import com.mobai.alert.dto.BinanceSymbolsDetailDTO;
import org.springframework.util.StringUtils;

import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Shared symbol-level filters for all alert rules.
 */
public final class AlertSymbolFilter {

    private static final Pattern BTC_ETH_RELATED_SYMBOL = Pattern.compile("^(BTC|ETH)(USDT|USDC|BTC)(_.+)?$");

    private AlertSymbolFilter() {
    }

    public static boolean isMonitorCandidate(BinanceSymbolsDetailDTO symbolDTO) {
        if (symbolDTO == null) {
            return false;
        }
        String symbol = symbolDTO.getSymbol();
        return StringUtils.hasText(symbol)
                && symbol.contains("USDT")
                && !isBtcOrEthRelatedSymbol(symbol)
                && Objects.equals(symbolDTO.getStatus(), "TRADING");
    }

    public static boolean isBtcOrEthRelatedSymbol(String symbol) {
        if (!StringUtils.hasText(symbol)) {
            return false;
        }
        return BTC_ETH_RELATED_SYMBOL.matcher(symbol.toUpperCase(Locale.ROOT)).matches();
    }
}
