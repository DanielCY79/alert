package com.mobai.alert.service;

import com.mobai.alert.dto.BinanceKlineDTO;
import com.mobai.alert.dto.BinanceSymbolsDetailDTO;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * 全市场日 K MA20 快照缓存。
 */
@Service
public class DailyMa20SnapshotService {

    private static final int MA20_PERIOD = 20;
    private static final int AVERAGE_VOLUME_PERIOD = 7;
    private static final long ONE_DAY_MILLIS = 24 * 60 * 60 * 1000L;

    private final BinanceMarketDataService binanceMarketDataService;
    private final ConcurrentMap<String, DailyMa20Snapshot> snapshots = new ConcurrentHashMap<>();

    public DailyMa20SnapshotService(BinanceMarketDataService binanceMarketDataService) {
        this.binanceMarketDataService = binanceMarketDataService;
    }

    /**
     * 按交易对列表刷新快照。
     * 同一交易对在同一日 K 周期内只会回源一次。
     */
    public synchronized void refreshSnapshot(List<BinanceSymbolsDetailDTO> symbols) {
        if (CollectionUtils.isEmpty(symbols)) {
            snapshots.clear();
            return;
        }

        long currentTime = System.currentTimeMillis();
        Set<String> activeSymbols = new HashSet<>();
        for (BinanceSymbolsDetailDTO symbol : symbols) {
            if (symbol == null
                    || symbol.getSymbol() == null
                    || !symbol.getSymbol().contains("USDT")
                    || !"TRADING".equals(symbol.getStatus())) {
                continue;
            }
            activeSymbols.add(symbol.getSymbol());
            DailyMa20Snapshot snapshot = snapshots.get(symbol.getSymbol());
            if (snapshot != null && snapshot.getRefreshAfterMillis() > currentTime) {
                continue;
            }

            DailyMa20Snapshot refreshedSnapshot = loadSnapshot(symbol.getSymbol());
            if (refreshedSnapshot != null) {
                snapshots.put(symbol.getSymbol(), refreshedSnapshot);
            } else {
                snapshots.remove(symbol.getSymbol());
            }
        }

        snapshots.keySet().removeIf(symbol -> !activeSymbols.contains(symbol));
    }

    /**
     * 获取指定交易对的快照。
     */
    public DailyMa20Snapshot getSnapshot(String symbol) {
        return snapshots.get(symbol);
    }

    /**
     * 读取最近 20 根日 K 并生成 MA20 快照。
     */
    private DailyMa20Snapshot loadSnapshot(String symbol) {
        List<BinanceKlineDTO> dailyKlines = binanceMarketDataService.loadRecentClosedKlines(symbol, "1d", MA20_PERIOD);
        if (CollectionUtils.isEmpty(dailyKlines) || dailyKlines.size() < MA20_PERIOD) {
            return null;
        }

        BigDecimal total = BigDecimal.ZERO;
        for (BinanceKlineDTO kline : dailyKlines) {
            total = total.add(new BigDecimal(kline.getClose()));
        }
        BigDecimal ma20 = total.divide(BigDecimal.valueOf(MA20_PERIOD), 6, RoundingMode.HALF_UP);
        BinanceKlineDTO latestDailyKline = dailyKlines.get(dailyKlines.size() - 1);
        long refreshAfterMillis = latestDailyKline.getEndTime() == null
                ? 0L
                : latestDailyKline.getEndTime() + ONE_DAY_MILLIS + 1L;

        BigDecimal averageVolume7d = calculateAverageVolume(dailyKlines, AVERAGE_VOLUME_PERIOD);

        return new DailyMa20Snapshot(ma20, averageVolume7d, refreshAfterMillis);
    }

    private BigDecimal calculateAverageVolume(List<BinanceKlineDTO> klines, int period) {
        if (CollectionUtils.isEmpty(klines) || klines.size() < period) {
            return null;
        }

        BigDecimal total = BigDecimal.ZERO;
        List<BinanceKlineDTO> recentKlines = klines.subList(klines.size() - period, klines.size());
        for (BinanceKlineDTO kline : recentKlines) {
            total = total.add(new BigDecimal(kline.getVolume()));
        }
        return total.divide(BigDecimal.valueOf(period), 6, RoundingMode.HALF_UP);
    }
}
