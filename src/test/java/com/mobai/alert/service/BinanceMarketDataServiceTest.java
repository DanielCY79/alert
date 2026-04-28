package com.mobai.alert.service;

import com.mobai.alert.api.BinanceApi;
import com.mobai.alert.dto.BinanceKlineDTO;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BinanceMarketDataServiceTest {

    @Test
    void shouldUseWebSocketKlinesWhenCacheIsFreshEnough() {
        BinanceApi binanceApi = mock(BinanceApi.class);
        BinanceWebSocketService webSocketService = mock(BinanceWebSocketService.class);
        BinanceMarketDataService service = newService(binanceApi, webSocketService);
        List<BinanceKlineDTO> freshKlines = List.of(
                kline(System.currentTimeMillis() - 120_000L),
                kline(System.currentTimeMillis() - 60_000L),
                kline(System.currentTimeMillis() - 1_000L)
        );

        when(webSocketService.supportsInterval("1m")).thenReturn(true);
        when(webSocketService.getRecentClosedKlines("LYNUSDT", 3)).thenReturn(freshKlines);

        List<BinanceKlineDTO> result = service.loadRecentClosedKlines("LYNUSDT", "1m", 3);

        assertSame(freshKlines, result);
        verify(binanceApi, never()).listKline(org.mockito.ArgumentMatchers.any());
        verify(webSocketService, never()).mergeRestSnapshot(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyList());
    }

    @Test
    void shouldFallbackToRestAndBackfillWhenWebSocketCacheIsStale() {
        BinanceApi binanceApi = mock(BinanceApi.class);
        BinanceWebSocketService webSocketService = mock(BinanceWebSocketService.class);
        BinanceMarketDataService service = newService(binanceApi, webSocketService);
        List<BinanceKlineDTO> staleKlines = List.of(
                kline(System.currentTimeMillis() - 300_000L),
                kline(System.currentTimeMillis() - 240_000L),
                kline(System.currentTimeMillis() - 180_000L)
        );
        List<BinanceKlineDTO> restKlines = List.of(
                kline(System.currentTimeMillis() - 120_000L),
                kline(System.currentTimeMillis() - 60_000L),
                kline(System.currentTimeMillis() - 1_000L)
        );

        when(webSocketService.supportsInterval("1m")).thenReturn(true);
        when(webSocketService.getRecentClosedKlines("LYNUSDT", 3)).thenReturn(staleKlines);
        when(binanceApi.listKline(org.mockito.ArgumentMatchers.any())).thenReturn(restKlines);

        List<BinanceKlineDTO> result = service.loadRecentClosedKlines("LYNUSDT", "1m", 3);

        assertEquals(restKlines, result);
        verify(binanceApi).listKline(org.mockito.ArgumentMatchers.argThat(request ->
                "LYNUSDT".equals(request.getSymbol())
                        && "1m".equals(request.getInterval())
                        && request.getLimit() == 5
        ));
        verify(webSocketService).mergeRestSnapshot("LYNUSDT", restKlines);
    }

    private BinanceMarketDataService newService(BinanceApi binanceApi, BinanceWebSocketService webSocketService) {
        BinanceMarketDataService service = new BinanceMarketDataService(binanceApi, webSocketService);
        ReflectionTestUtils.setField(service, "wsCacheStaleGraceMs", 15_000L);
        return service;
    }

    private BinanceKlineDTO kline(long endTime) {
        BinanceKlineDTO dto = new BinanceKlineDTO();
        dto.setEndTime(endTime);
        dto.setStartTime(endTime - 59_999L);
        return dto;
    }
}
