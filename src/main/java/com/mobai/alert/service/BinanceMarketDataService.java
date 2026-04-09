package com.mobai.alert.service;

import com.mobai.alert.api.BinanceApi;
import com.mobai.alert.dto.BinanceKlineDTO;
import com.mobai.alert.dto.BinanceSymbolsDetailDTO;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

@Service
public class BinanceMarketDataService {

    private static final int REST_BUFFER_SIZE = 2;

    private final BinanceApi binanceApi;
    private final BinanceWebSocketService binanceWebSocketService;

    @Value("${market.data.mode:hybrid}")
    private String marketDataMode;

    public BinanceMarketDataService(BinanceApi binanceApi, BinanceWebSocketService binanceWebSocketService) {
        this.binanceApi = binanceApi;
        this.binanceWebSocketService = binanceWebSocketService;
    }

    public void refreshSubscriptions(List<BinanceSymbolsDetailDTO> symbols) {
        binanceWebSocketService.refreshSubscriptions(symbols);
    }

    public List<BinanceKlineDTO> loadRecentClosedKlines(String symbol, String interval, int limit) {
        if (limit <= 0) {
            return Collections.emptyList();
        }

        if (preferWebSocket()) {
            List<BinanceKlineDTO> wsKlines = binanceWebSocketService.getRecentClosedKlines(symbol, limit);
            if (wsKlines.size() >= limit) {
                return wsKlines;
            }
        }

        List<BinanceKlineDTO> restKlines = loadRecentClosedKlinesFromRest(symbol, interval, limit);
        if (preferWebSocket() && !CollectionUtils.isEmpty(restKlines)) {
            binanceWebSocketService.mergeRestSnapshot(symbol, restKlines);
        }
        return restKlines;
    }

    private List<BinanceKlineDTO> loadRecentClosedKlinesFromRest(String symbol, String interval, int limit) {
        BinanceKlineDTO reqDTO = new BinanceKlineDTO();
        reqDTO.setSymbol(symbol);
        reqDTO.setInterval(interval);
        reqDTO.setLimit(limit + REST_BUFFER_SIZE);
        reqDTO.setTimeZone("8");

        long currentTime = System.currentTimeMillis();
        List<BinanceKlineDTO> rawKlines = binanceApi.listKline(reqDTO);
        if (CollectionUtils.isEmpty(rawKlines)) {
            return Collections.emptyList();
        }

        List<BinanceKlineDTO> closedKlines = rawKlines.stream()
                .filter(kline -> kline != null && kline.getEndTime() != null)
                .filter(kline -> kline.getEndTime() < currentTime)
                .collect(Collectors.toList());

        if (closedKlines.size() <= limit) {
            return closedKlines;
        }
        return closedKlines.subList(closedKlines.size() - limit, closedKlines.size());
    }

    private boolean preferWebSocket() {
        return !"rest".equals(normalizeMode());
    }

    private String normalizeMode() {
        if (marketDataMode == null || marketDataMode.isBlank()) {
            return "hybrid";
        }
        return marketDataMode.trim().toLowerCase(Locale.ROOT);
    }
}
