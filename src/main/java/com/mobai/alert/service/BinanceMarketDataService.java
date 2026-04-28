package com.mobai.alert.service;

import com.mobai.alert.api.BinanceApi;
import com.mobai.alert.dto.BinanceKlineDTO;
import com.mobai.alert.dto.BinanceSymbolsDetailDTO;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 行情读取门面，按配置优先使用 WebSocket，必要时回退到 REST。
 */
@Service
public class BinanceMarketDataService {

    private static final int REST_BUFFER_SIZE = 2;
    private static final int REST_MAX_LIMIT = 1000;
    private static final long DEFAULT_WS_CACHE_STALE_GRACE_MS = 15_000L;

    @Value("${market.ws.cache-stale-grace-ms:15000}")
    private long wsCacheStaleGraceMs = DEFAULT_WS_CACHE_STALE_GRACE_MS;

    private final BinanceApi binanceApi;
    private final BinanceWebSocketService binanceWebSocketService;

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
     * 当 WebSocket 缓存不足或最新 K 线过旧时，会回退到 REST 并回填缓存。
     */
    public List<BinanceKlineDTO> loadRecentClosedKlines(String symbol, String interval, int limit) {
        if (limit <= 0) {
            return Collections.emptyList();
        }

        long currentTime = System.currentTimeMillis();
        if (binanceWebSocketService.supportsInterval(interval)) {
            List<BinanceKlineDTO> wsKlines = binanceWebSocketService.getRecentClosedKlines(symbol, limit);
            if (wsKlines.size() >= limit && isFreshEnough(wsKlines, interval, currentTime)) {
                return wsKlines;
            }
        }

        List<BinanceKlineDTO> restKlines = loadRecentClosedKlinesFromRest(symbol, interval, limit, currentTime);
        if (binanceWebSocketService.supportsInterval(interval) && !CollectionUtils.isEmpty(restKlines)) {
            binanceWebSocketService.mergeRestSnapshot(symbol, restKlines);
        }
        return restKlines;
    }

    /**
     * 通过 REST 拉取最近已收盘的 K 线。
     */
    private List<BinanceKlineDTO> loadRecentClosedKlinesFromRest(String symbol, String interval, int limit, long currentTime) {
        int remaining = limit + REST_BUFFER_SIZE;
        Long endTime = currentTime;
        List<BinanceKlineDTO> closedKlines = new ArrayList<>();

        while (remaining > 0) {
            int batchLimit = Math.min(remaining, REST_MAX_LIMIT);
            List<BinanceKlineDTO> batch = loadClosedRestBatch(symbol, interval, batchLimit, endTime, currentTime);
            if (CollectionUtils.isEmpty(batch)) {
                break;
            }

            closedKlines.addAll(0, batch);
            remaining -= batch.size();

            BinanceKlineDTO oldestKline = batch.get(0);
            if (oldestKline.getStartTime() == null || oldestKline.getStartTime() <= 0 || batch.size() < batchLimit) {
                break;
            }
            endTime = oldestKline.getStartTime() - 1;
        }

        if (closedKlines.size() <= limit) {
            return closedKlines;
        }
        return closedKlines.subList(closedKlines.size() - limit, closedKlines.size());
    }

    /**
     * 拉取单批已收盘 K 线。
     */
    private List<BinanceKlineDTO> loadClosedRestBatch(String symbol,
                                                      String interval,
                                                      int limit,
                                                      Long endTime,
                                                      long currentTime) {
        BinanceKlineDTO reqDTO = new BinanceKlineDTO();
        reqDTO.setSymbol(symbol);
        reqDTO.setInterval(interval);
        reqDTO.setLimit(limit);
        reqDTO.setTimeZone("8");
        reqDTO.setEndTime(endTime);

        List<BinanceKlineDTO> rawKlines = binanceApi.listKline(reqDTO);
        if (CollectionUtils.isEmpty(rawKlines)) {
            return Collections.emptyList();
        }

        return rawKlines.stream()
                .filter(kline -> kline != null && kline.getEndTime() != null)
                .filter(kline -> kline.getEndTime() < currentTime)
                .collect(Collectors.toList());
    }

    private boolean isFreshEnough(List<BinanceKlineDTO> klines, String interval, long currentTime) {
        if (CollectionUtils.isEmpty(klines)) {
            return false;
        }

        BinanceKlineDTO latestKline = klines.get(klines.size() - 1);
        if (latestKline == null || latestKline.getEndTime() == null) {
            return false;
        }

        long intervalMillis = parseIntervalMillis(interval);
        long maxAgeMillis = intervalMillis + Math.max(0L, wsCacheStaleGraceMs);
        return currentTime - latestKline.getEndTime() <= maxAgeMillis;
    }

    private long parseIntervalMillis(String interval) {
        if (interval == null || interval.length() < 2) {
            return 60_000L;
        }

        try {
            long value = Long.parseLong(interval.substring(0, interval.length() - 1));
            char unit = Character.toLowerCase(interval.charAt(interval.length() - 1));
            return switch (unit) {
                case 's' -> value * 1_000L;
                case 'm' -> value * 60_000L;
                case 'h' -> value * 60L * 60_000L;
                case 'd' -> value * 24L * 60L * 60_000L;
                default -> 60_000L;
            };
        } catch (NumberFormatException e) {
            return 60_000L;
        }
    }

}
