package com.mobai.alert.service;

import com.mobai.alert.dto.BinanceSymbolsDTO;
import com.mobai.alert.dto.BinanceSymbolsDetailDTO;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

/**
 * 告警调度服务，负责按周期刷新交易对、同步行情订阅并触发逐个处理。
 */
@Service
public class AlertService {

    private static final DateTimeFormatter LOG_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final AlertSymbolCacheService alertSymbolCacheService;
    private final BinanceMarketDataService binanceMarketDataService;
    private final BinanceTickerStatsService binanceTickerStatsService;
    private final DailyMa20SnapshotService dailyMa20SnapshotService;
    private final AlertSymbolProcessor alertSymbolProcessor;

    public AlertService(AlertSymbolCacheService alertSymbolCacheService,
                        BinanceMarketDataService binanceMarketDataService,
                        BinanceTickerStatsService binanceTickerStatsService,
                        DailyMa20SnapshotService dailyMa20SnapshotService,
                        AlertSymbolProcessor alertSymbolProcessor) {
        this.alertSymbolCacheService = alertSymbolCacheService;
        this.binanceMarketDataService = binanceMarketDataService;
        this.binanceTickerStatsService = binanceTickerStatsService;
        this.dailyMa20SnapshotService = dailyMa20SnapshotService;
        this.alertSymbolProcessor = alertSymbolProcessor;
    }

    /**
     * 定时执行监控主流程。
     */
    @Scheduled(fixedRateString = "${monitoring.cycle:60000}")
    public void monitoring() {
        System.out.println("Monitoring started " + LOG_TIME_FORMATTER.format(AppTime.now()));

        BinanceSymbolsDTO symbolsDTO;
        try {
            symbolsDTO = alertSymbolCacheService.loadSymbols();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        if (symbolsDTO == null || CollectionUtils.isEmpty(symbolsDTO.getSymbols())) {
            return;
        }

        binanceMarketDataService.refreshSubscriptions(symbolsDTO.getSymbols());
        binanceTickerStatsService.refreshSnapshot();
        dailyMa20SnapshotService.refreshSnapshot(symbolsDTO.getSymbols());
        processSymbols(symbolsDTO.getSymbols(), 4);
        System.out.println("Monitoring finished " + LOG_TIME_FORMATTER.format(AppTime.now()));
    }

    /**
     * 并发处理交易对，线程池大小不会超过交易对数量，避免空闲线程。
     */
    private void processSymbols(List<BinanceSymbolsDetailDTO> symbols, int threadCount) {
        int poolSize = Math.max(1, Math.min(threadCount, symbols.size()));
        ExecutorService executor = Executors.newFixedThreadPool(poolSize);
        try {
            List<CompletableFuture<Void>> futures = symbols.stream()
                    .map(symbol -> CompletableFuture.runAsync(() -> alertSymbolProcessor.process(symbol), executor)
                            .exceptionally(ex -> {
                                ex.printStackTrace();
                                return null;
                            }))
                    .collect(Collectors.toList());
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
        } finally {
            executor.shutdown();
        }
    }
}
