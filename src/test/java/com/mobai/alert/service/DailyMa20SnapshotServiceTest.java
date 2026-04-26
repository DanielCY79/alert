package com.mobai.alert.service;

import com.mobai.alert.dto.BinanceKlineDTO;
import com.mobai.alert.dto.BinanceSymbolsDetailDTO;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DailyMa20SnapshotServiceTest {

    @Test
    void shouldReuseSnapshotBeforeRefreshTimeAndReloadAfterExpiry() {
        BinanceMarketDataService binanceMarketDataService = mock(BinanceMarketDataService.class);
        DailyMa20SnapshotService service = new DailyMa20SnapshotService(binanceMarketDataService);
        BinanceSymbolsDetailDTO symbol = tradingSymbol("BTCUSDT");

        List<BinanceKlineDTO> firstDailyKlines = dailyKlines("BTCUSDT", System.currentTimeMillis());
        List<BinanceKlineDTO> secondDailyKlines = dailyKlines("BTCUSDT", System.currentTimeMillis() + 24 * 60 * 60 * 1000L);
        when(binanceMarketDataService.loadRecentClosedKlines("BTCUSDT", "1d", 20))
                .thenReturn(firstDailyKlines)
                .thenReturn(secondDailyKlines);

        service.refreshSnapshot(List.of(symbol));
        assertNotNull(service.getSnapshot("BTCUSDT"));
        assertNotNull(service.getSnapshot("BTCUSDT").getMa20());
        assertNotNull(service.getSnapshot("BTCUSDT").getAverageVolume7d());

        service.refreshSnapshot(List.of(symbol));
        verify(binanceMarketDataService, times(1)).loadRecentClosedKlines("BTCUSDT", "1d", 20);

        forceExpiry(service, "BTCUSDT");
        service.refreshSnapshot(List.of(symbol));
        verify(binanceMarketDataService, times(2)).loadRecentClosedKlines("BTCUSDT", "1d", 20);
    }

    private void forceExpiry(DailyMa20SnapshotService service, String symbol) {
        DailyMa20Snapshot snapshot = service.getSnapshot(symbol);
        DailyMa20Snapshot expiredSnapshot = new DailyMa20Snapshot(
                snapshot.getMa20(),
                snapshot.getAverageVolume7d(),
                System.currentTimeMillis() - 1L
        );
        replaceSnapshot(service, symbol, expiredSnapshot);
    }

    @SuppressWarnings("unchecked")
    private void replaceSnapshot(DailyMa20SnapshotService service, String symbol, DailyMa20Snapshot snapshot) {
        try {
            java.lang.reflect.Field field = DailyMa20SnapshotService.class.getDeclaredField("snapshots");
            field.setAccessible(true);
            ((java.util.concurrent.ConcurrentMap<String, DailyMa20Snapshot>) field.get(service)).put(symbol, snapshot);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private BinanceSymbolsDetailDTO tradingSymbol(String symbol) {
        BinanceSymbolsDetailDTO dto = new BinanceSymbolsDetailDTO();
        dto.setSymbol(symbol);
        dto.setStatus("TRADING");
        return dto;
    }

    private List<BinanceKlineDTO> dailyKlines(String symbol, long latestEndTime) {
        List<BinanceKlineDTO> klines = new ArrayList<>();
        long startEndTime = latestEndTime - (19L * 24 * 60 * 60 * 1000L);
        for (int i = 0; i < 20; i++) {
            BinanceKlineDTO dto = new BinanceKlineDTO();
            dto.setSymbol(symbol);
            dto.setOpen(String.valueOf(100 + i));
            dto.setClose(String.valueOf(101 + i));
            dto.setHigh(String.valueOf(102 + i));
            dto.setLow(String.valueOf(99 + i));
            dto.setVolume("1000000");
            dto.setEndTime(startEndTime + i * 24L * 60 * 60 * 1000L);
            klines.add(dto);
        }
        klines.get(klines.size() - 1).setEndTime(latestEndTime);
        return klines;
    }
}
