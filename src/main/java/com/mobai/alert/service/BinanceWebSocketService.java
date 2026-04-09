package com.mobai.alert.service;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.mobai.alert.dto.BinanceKlineDTO;
import com.mobai.alert.dto.BinanceSymbolsDTO;
import com.mobai.alert.dto.BinanceSymbolsDetailDTO;
import jakarta.annotation.PreDestroy;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

@Service
public class BinanceWebSocketService {

    private static final int MIN_CACHE_SIZE = 5;
    private static final long RECONNECT_DELAY_MS = 3000L;

    private final OkHttpClient okHttpClient;
    private final AlertSymbolCacheService alertSymbolCacheService;
    private final ConcurrentMap<String, ConcurrentSkipListMap<Long, BinanceKlineDTO>> closedKlineCache = new ConcurrentHashMap<>();
    private final List<SocketConnection> socketConnections = new CopyOnWriteArrayList<>();
    private final ExecutorService reconnectExecutor = Executors.newSingleThreadExecutor();
    private final AtomicBoolean reconnectScheduled = new AtomicBoolean(false);
    private final AtomicLong lastMessageAt = new AtomicLong(0L);

    @Value("${market.data.mode:hybrid}")
    private String marketDataMode;

    @Value("${market.ws.base-url:wss://fstream.binance.com}")
    private String webSocketBaseUrl;

    @Value("${market.ws.interval:1m}")
    private String interval;

    @Value("${market.ws.batch-size:100}")
    private int batchSize;

    @Value("${market.ws.cache-size:10}")
    private int cacheSize;

    @Value("${market.ws.stale-threshold-ms:120000}")
    private long staleThresholdMs;

    private volatile Set<String> desiredSymbols = Collections.emptySet();

    public BinanceWebSocketService(OkHttpClient okHttpClient, AlertSymbolCacheService alertSymbolCacheService) {
        this.okHttpClient = okHttpClient;
        this.alertSymbolCacheService = alertSymbolCacheService;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void initialize() {
        if (!isEnabled()) {
            return;
        }
        try {
            BinanceSymbolsDTO symbolsDTO = alertSymbolCacheService.loadSymbols();
            if (symbolsDTO != null) {
                refreshSubscriptions(symbolsDTO.getSymbols());
            }
        } catch (IOException e) {
            System.out.println("WebSocket initialization failed, will retry later.");
            e.printStackTrace();
        }
    }

    public boolean isEnabled() {
        return !"rest".equals(normalizeMode());
    }

    public void refreshSubscriptions(List<BinanceSymbolsDetailDTO> symbols) {
        if (!isEnabled()) {
            return;
        }
        Set<String> nextSymbols = extractSymbols(symbols);
        if (nextSymbols.equals(desiredSymbols)) {
            return;
        }
        desiredSymbols = Collections.unmodifiableSet(nextSymbols);
        reconnectNow("symbol list refreshed");
    }

    public List<BinanceKlineDTO> getRecentClosedKlines(String symbol, int limit) {
        if (symbol == null || symbol.isBlank() || limit <= 0) {
            return Collections.emptyList();
        }
        ConcurrentSkipListMap<Long, BinanceKlineDTO> cache = closedKlineCache.get(symbol);
        if (cache == null || cache.isEmpty()) {
            return Collections.emptyList();
        }

        List<BinanceKlineDTO> descending = new ArrayList<>(cache.descendingMap().values());
        int endIndex = Math.min(limit, descending.size());
        List<BinanceKlineDTO> result = new ArrayList<>(descending.subList(0, endIndex));
        Collections.reverse(result);
        return result;
    }

    public void mergeRestSnapshot(String symbol, List<BinanceKlineDTO> klines) {
        if (!isEnabled() || symbol == null || CollectionUtils.isEmpty(klines)) {
            return;
        }
        for (BinanceKlineDTO kline : klines) {
            if (kline != null && symbol.equals(kline.getSymbol())) {
                storeClosedKline(kline);
            }
        }
    }

    @Scheduled(fixedDelayString = "${market.ws.health-check-ms:30000}")
    public void healthCheck() {
        if (!isEnabled() || desiredSymbols.isEmpty()) {
            return;
        }
        long currentTime = System.currentTimeMillis();
        boolean hasOpenConnection = socketConnections.stream().anyMatch(SocketConnection::isOpen);
        boolean stale = lastMessageAt.get() > 0 && currentTime - lastMessageAt.get() > staleThresholdMs;
        if (socketConnections.isEmpty() || !hasOpenConnection || stale) {
            scheduleReconnect("health check");
        }
    }

    @PreDestroy
    public void shutdown() {
        desiredSymbols = Collections.emptySet();
        closeConnections();
        reconnectExecutor.shutdownNow();
    }

    private synchronized void reconnectNow(String reason) {
        closeConnections();
        if (desiredSymbols.isEmpty()) {
            return;
        }

        List<String> symbols = new ArrayList<>(desiredSymbols);
        int safeBatchSize = Math.max(1, batchSize);
        for (int start = 0; start < symbols.size(); start += safeBatchSize) {
            int end = Math.min(start + safeBatchSize, symbols.size());
            List<String> batch = new ArrayList<>(symbols.subList(start, end));
            openSocket(batch);
        }
        lastMessageAt.set(System.currentTimeMillis());
        System.out.println("WebSocket subscriptions refreshed, reason=" + reason + ", symbols=" + symbols.size() + ", sockets=" + socketConnections.size());
    }

    private void openSocket(List<String> symbols) {
        String joinedStreams = symbols.stream()
                .map(symbol -> symbol.toLowerCase(Locale.ROOT) + "@kline_" + interval)
                .collect(Collectors.joining("/"));
        String url = webSocketBaseUrl + "/stream?streams=" + joinedStreams;
        SocketConnection connection = new SocketConnection(url);
        Request request = new Request.Builder().url(url).build();
        connection.setWebSocket(okHttpClient.newWebSocket(request, new BinanceKlineListener(connection)));
        socketConnections.add(connection);
    }

    private void closeConnections() {
        for (SocketConnection connection : socketConnections) {
            connection.setManualClose(true);
            WebSocket webSocket = connection.getWebSocket();
            if (webSocket != null) {
                try {
                    webSocket.close(1000, "refresh");
                } catch (Exception ignored) {
                    webSocket.cancel();
                }
            }
        }
        socketConnections.clear();
    }

    private void scheduleReconnect(String reason) {
        if (!isEnabled() || desiredSymbols.isEmpty()) {
            return;
        }
        if (reconnectScheduled.compareAndSet(false, true)) {
            reconnectExecutor.submit(() -> {
                try {
                    Thread.sleep(RECONNECT_DELAY_MS);
                    reconnectNow(reason);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    reconnectScheduled.set(false);
                }
            });
        }
    }

    private void handleMessage(String text) {
        JSONObject payload = JSON.parseObject(text);
        JSONObject data = payload.getJSONObject("data");
        if (data == null && "kline".equals(payload.getString("e"))) {
            data = payload;
        }
        if (data == null) {
            return;
        }

        JSONObject klineJson = data.getJSONObject("k");
        if (klineJson == null || !Boolean.TRUE.equals(klineJson.getBoolean("x"))) {
            return;
        }

        BinanceKlineDTO klineDTO = new BinanceKlineDTO();
        klineDTO.setSymbol(data.getString("s"));
        klineDTO.setInterval(klineJson.getString("i"));
        klineDTO.setStartTime(klineJson.getLong("t"));
        klineDTO.setEndTime(klineJson.getLong("T"));
        klineDTO.setOpen(klineJson.getString("o"));
        klineDTO.setClose(klineJson.getString("c"));
        klineDTO.setHigh(klineJson.getString("h"));
        klineDTO.setLow(klineJson.getString("l"));
        klineDTO.setAmount(klineJson.getString("v"));
        klineDTO.setVolume(klineJson.getString("q"));
        klineDTO.setOpenTime(String.valueOf(klineJson.getLong("t")));
        klineDTO.setCloseTime(String.valueOf(klineJson.getLong("T")));
        storeClosedKline(klineDTO);
    }

    private void storeClosedKline(BinanceKlineDTO kline) {
        if (kline == null || kline.getSymbol() == null || kline.getEndTime() == null) {
            return;
        }

        ConcurrentSkipListMap<Long, BinanceKlineDTO> cache = closedKlineCache.computeIfAbsent(
                kline.getSymbol(),
                key -> new ConcurrentSkipListMap<>()
        );
        cache.put(kline.getEndTime(), kline);

        int maxCacheSize = Math.max(cacheSize, MIN_CACHE_SIZE);
        while (cache.size() > maxCacheSize) {
            Map.Entry<Long, BinanceKlineDTO> oldestEntry = cache.pollFirstEntry();
            if (oldestEntry == null) {
                break;
            }
        }
        lastMessageAt.set(System.currentTimeMillis());
    }

    private Set<String> extractSymbols(List<BinanceSymbolsDetailDTO> symbols) {
        if (CollectionUtils.isEmpty(symbols)) {
            return Collections.emptySet();
        }
        return symbols.stream()
                .filter(symbol -> symbol != null && "TRADING".equals(symbol.getStatus()))
                .map(BinanceSymbolsDetailDTO::getSymbol)
                .filter(symbol -> symbol != null && !symbol.isBlank())
                .filter(symbol -> symbol.contains("USDT"))
                .collect(Collectors.toCollection(TreeSet::new));
    }

    private String normalizeMode() {
        if (marketDataMode == null || marketDataMode.isBlank()) {
            return "hybrid";
        }
        return marketDataMode.trim().toLowerCase(Locale.ROOT);
    }

    private final class BinanceKlineListener extends WebSocketListener {

        private final SocketConnection connection;

        private BinanceKlineListener(SocketConnection connection) {
            this.connection = connection;
        }

        @Override
        public void onOpen(WebSocket webSocket, Response response) {
            connection.setOpen(true);
            lastMessageAt.set(System.currentTimeMillis());
            System.out.println("WebSocket connected: " + connection.getUrl());
        }

        @Override
        public void onMessage(WebSocket webSocket, String text) {
            try {
                lastMessageAt.set(System.currentTimeMillis());
                handleMessage(text);
            } catch (Exception e) {
                System.out.println("Failed to parse WebSocket message: " + text);
                e.printStackTrace();
            }
        }

        @Override
        public void onClosing(WebSocket webSocket, int code, String reason) {
            connection.setOpen(false);
            webSocket.close(code, reason);
            if (!connection.isManualClose()) {
                scheduleReconnect("server closing socket");
            }
        }

        @Override
        public void onClosed(WebSocket webSocket, int code, String reason) {
            connection.setOpen(false);
            if (!connection.isManualClose()) {
                scheduleReconnect("socket closed");
            }
        }

        @Override
        public void onFailure(WebSocket webSocket, Throwable t, Response response) {
            connection.setOpen(false);
            if (!connection.isManualClose()) {
                System.out.println("WebSocket failure: " + connection.getUrl());
                t.printStackTrace();
                scheduleReconnect("socket failure");
            }
        }
    }

    private static final class SocketConnection {

        private final String url;
        private volatile WebSocket webSocket;
        private volatile boolean open;
        private volatile boolean manualClose;

        private SocketConnection(String url) {
            this.url = url;
        }

        public WebSocket getWebSocket() {
            return webSocket;
        }

        public void setWebSocket(WebSocket webSocket) {
            this.webSocket = webSocket;
        }

        public boolean isOpen() {
            return open;
        }

        public void setOpen(boolean open) {
            this.open = open;
        }

        public boolean isManualClose() {
            return manualClose;
        }

        public void setManualClose(boolean manualClose) {
            this.manualClose = manualClose;
        }

        public String getUrl() {
            return url;
        }
    }
}
