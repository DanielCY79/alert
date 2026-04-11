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

/**
 * 行情读取门面，按配置优先使用 WebSocket，必要时回退到 REST。
 */
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

    /**
     * 刷新 WebSocket 订阅交易对列表。
     */
    public void refreshSubscriptions(List<BinanceSymbolsDetailDTO> symbols) {
        binanceWebSocketService.refreshSubscriptions(symbols);
    }

    /**
     * 加载最近已收盘的 K 线。
     * 当 WebSocket 缓存不足时，会回退到 REST 并回填缓存。
     */
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

    /**
     * 通过 REST 拉取最近已收盘的 K 线。
     */
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

    /**
     * 判断当前是否优先使用 WebSocket 数据源。
     */
    private boolean preferWebSocket() {
        return !"rest".equals(normalizeMode());
    }

    /**
     * 统一规范化数据源模式字符串。
     */
    private String normalizeMode() {
        if (marketDataMode == null || marketDataMode.isBlank()) {
            return "hybrid";
        }
        return marketDataMode.trim().toLowerCase(Locale.ROOT);
    }
}
