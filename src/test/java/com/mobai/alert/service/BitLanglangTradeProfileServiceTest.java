package com.mobai.alert.service;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class BitLanglangTradeProfileServiceTest {

    @Test
    void shouldBuildBtcDirectionalProfileFromWorkbookRecords() throws IOException {
        BitLanglangTradeHistoryLoader loader = mock(BitLanglangTradeHistoryLoader.class);
        BitLanglangTradeProfileService service = new BitLanglangTradeProfileService(loader);
        Path tempFile = Files.createTempFile("bitlanglang-history", ".xlsx");

        try {
            ReflectionTestUtils.setField(service, "historyPath", tempFile.toString());
            ReflectionTestUtils.setField(service, "activeHourTopN", 2);
            ReflectionTestUtils.setField(service, "minimumTradeCount", 3);

            List<BitLanglangTradeRecord> records = List.of(
                    record(1, BitLanglangTradeSide.LONG, 20, hour(2024, 6, 20, 22, 0), hour(2024, 6, 20, 22, 15), "100", "102", "80", "200000"),
                    record(2, BitLanglangTradeSide.SHORT, 30, hour(2024, 6, 20, 22, 30), hour(2024, 6, 20, 22, 40), "103", "101", "75", "180000"),
                    record(3, BitLanglangTradeSide.LONG, 20, hour(2024, 6, 20, 23, 0), hour(2024, 6, 20, 23, 12), "101", "103", "60", "210000"),
                    record(4, BitLanglangTradeSide.LONG, 10, hour(2024, 6, 21, 9, 0), hour(2024, 6, 21, 9, 20), "103", "104", "-10", "160000")
            );
            when(loader.load(eq(tempFile))).thenReturn(records);

            Optional<BitLanglangTradeProfile> profileOptional = service.getActiveProfile();

            assertTrue(profileOptional.isPresent());
            BitLanglangTradeProfile profile = profileOptional.get();
            assertEquals("BTCUSDT", profile.getPrimarySymbol());
            assertEquals(4, profile.getTradeCount());
            assertEquals(List.of(22, 23), profile.getActiveHours());
            assertEquals(3, profile.getBreakoutLookback());
            assertTrue(profile.getLongShare().compareTo(profile.getShortShare()) > 0);
            assertTrue(profile.getLongOneMinuteRateMin().compareTo(BigDecimal.ZERO) > 0);
            assertTrue(profile.getShortOneMinuteRateMin().compareTo(BigDecimal.ZERO) > 0);
        } finally {
            Files.deleteIfExists(tempFile);
        }
    }

    private BitLanglangTradeRecord record(int index,
                                          BitLanglangTradeSide side,
                                          int leverage,
                                          LocalDateTime entryTime,
                                          LocalDateTime exitTime,
                                          String entryPrice,
                                          String exitPrice,
                                          String profitUsd,
                                          String tradeValueUsd) {
        return new BitLanglangTradeRecord(
                index,
                "BTC-USDT-SWAP",
                "BTCUSDT",
                side,
                new BigDecimal(leverage),
                entryTime,
                exitTime,
                new BigDecimal(entryPrice),
                new BigDecimal(exitPrice),
                BigDecimal.ZERO,
                new BigDecimal(profitUsd),
                new BigDecimal(tradeValueUsd)
        );
    }

    private LocalDateTime hour(int year, int month, int day, int hour, int minute) {
        return LocalDateTime.of(year, month, day, hour, minute);
    }
}
