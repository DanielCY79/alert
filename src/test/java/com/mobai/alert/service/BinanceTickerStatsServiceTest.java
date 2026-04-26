package com.mobai.alert.service;

import com.mobai.alert.api.BinanceApi;
import com.mobai.alert.dto.BinanceTicker24hrDTO;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class BinanceTickerStatsServiceTest {

    @Test
    void shouldConvert24HourQuoteVolumeToAverageOneMinuteVolume() {
        BinanceApi binanceApi = mock(BinanceApi.class);
        BinanceTickerStatsService service = new BinanceTickerStatsService(binanceApi);
        BinanceTicker24hrDTO ticker = new BinanceTicker24hrDTO();
        ticker.setSymbol("BTCUSDT");
        ticker.setQuoteVolume("1440000");

        when(binanceApi.list24hrTickerStats()).thenReturn(List.of(ticker));

        service.refreshSnapshot();

        assertEquals(new BigDecimal("1000.000000"), service.getAverageOneMinuteVolume("BTCUSDT"));
    }
}
