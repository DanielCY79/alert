package com.mobai.alert.service;

import com.mobai.alert.api.FeishuBotApi;
import com.mobai.alert.api.FeishuCardTemplate;
import com.mobai.alert.dto.BinanceKlineDTO;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class AlertNotificationServiceTest {

    @Test
    void shouldHighlightFirstLegacyNotificationOfTheDay() {
        FeishuBotApi feishuBotApi = mock(FeishuBotApi.class);
        AlertNotificationService service = newService(feishuBotApi);

        service.send(signal("BTCUSDT", "1"));

        verify(feishuBotApi).sendGroupMessage(anyString(), anyString(), eq(true));
    }

    @Test
    void shouldAllowThreeNotificationsForSameSymbolWithinSameNaturalDay() {
        FeishuBotApi feishuBotApi = mock(FeishuBotApi.class);
        AlertNotificationService service = newService(feishuBotApi);

        service.send(signal("BTCUSDT", "1"));
        service.send(signal("BTCUSDT", "1"));
        service.send(signal("BTCUSDT", "1"));

        verify(feishuBotApi, times(3)).sendGroupMessage(anyString(), anyString(), eq(true));
    }

    @Test
    void shouldSuppressFourthNotificationForSameSymbolWithinSameNaturalDay() {
        FeishuBotApi feishuBotApi = mock(FeishuBotApi.class);
        AlertNotificationService service = newService(feishuBotApi);

        service.send(signal("BTCUSDT", "1"));
        service.send(signal("BTCUSDT", "1"));
        service.send(signal("BTCUSDT", "1"));
        reset(feishuBotApi);

        service.send(signal("BTCUSDT", "1"));

        verifyNoInteractions(feishuBotApi);
    }

    @Test
    void shouldStillSendForDifferentSymbolsWithinSameNaturalDay() {
        FeishuBotApi feishuBotApi = mock(FeishuBotApi.class);
        AlertNotificationService service = newService(feishuBotApi);

        service.send(signal("BTCUSDT", "1"));
        service.send(signal("BTCUSDT", "1"));
        service.send(signal("BTCUSDT", "1"));
        reset(feishuBotApi);

        service.send(signal("ETHUSDT", "1"));

        verify(feishuBotApi).sendGroupMessage(anyString(), anyString(), eq(true));
    }

    @Test
    void shouldHighlightAgainOnNextNaturalDay() {
        FeishuBotApi feishuBotApi = mock(FeishuBotApi.class);
        AlertNotificationService service = newService(feishuBotApi);
        AlertSignal signal = signal("BTCUSDT", "1");
        String recordKey = "BTCUSDT";

        Map<String, String> sentRecords = getStringRecordMap(service, "sentRecords");
        sentRecords.put(recordKey, AppTime.today().minusDays(1) + ":3");

        service.send(signal);

        verify(feishuBotApi).sendGroupMessage(anyString(), anyString(), eq(true));
    }

    @Test
    void shouldLoadTodaySentRecordFromLocalFile() throws IOException {
        FeishuBotApi feishuBotApi = mock(FeishuBotApi.class);
        AlertNotificationService service = newService(feishuBotApi);
        AlertSignal signal = signal("BTCUSDT", "1");
        String recordKey = "BTCUSDT";
        Path tempFile = Files.createTempFile("feishu-highlight-records", ".json");

        try {
            Files.writeString(
                    tempFile,
                    "{\"" + recordKey + "\":\"" + AppTime.today() + ":3\"}",
                    StandardCharsets.UTF_8
            );
            ReflectionTestUtils.setField(service, "sentRecordFilePath", tempFile);
            service.initSentRecords();

            service.send(signal);

            verifyNoInteractions(feishuBotApi);
        } finally {
            Files.deleteIfExists(tempFile);
        }
    }

    @Test
    void shouldCountTypeOneAndTypeTwoTogetherForSameSymbol() {
        FeishuBotApi feishuBotApi = mock(FeishuBotApi.class);
        AlertNotificationService service = newService(feishuBotApi);

        service.send(signal("BTCUSDT", "1"));
        service.send(type2Signal("BTCUSDT"));
        service.send(signal("BTCUSDT", "1"));
        reset(feishuBotApi);

        service.send(type2Signal("BTCUSDT"));

        verifyNoInteractions(feishuBotApi);
    }

    @Test
    void shouldMigrateLegacyCategoryRecordsToSymbolCount() throws IOException {
        FeishuBotApi feishuBotApi = mock(FeishuBotApi.class);
        AlertNotificationService service = newService(feishuBotApi);
        Path tempFile = Files.createTempFile("feishu-highlight-records", ".json");

        try {
            Files.writeString(
                    tempFile,
                    "{\"legacy:BTCUSDT\":\"" + AppTime.today() + "\","
                            + "\"low_volume_ma20:BTCUSDT\":\"" + AppTime.today() + "\"}",
                    StandardCharsets.UTF_8
            );
            ReflectionTestUtils.setField(service, "sentRecordFilePath", tempFile);
            service.initSentRecords();

            service.send(signal("BTCUSDT", "1"));
            reset(feishuBotApi);
            service.send(signal("BTCUSDT", "1"));

            verifyNoInteractions(feishuBotApi);
        } finally {
            Files.deleteIfExists(tempFile);
        }
    }

    @Test
    void shouldPassBodyThroughMarketSentimentEnrichment() {
        FeishuBotApi feishuBotApi = mock(FeishuBotApi.class);
        MarketSentimentService marketSentimentService = mock(MarketSentimentService.class);
        when(marketSentimentService.enrichBody(anyString(), anyString()))
                .thenAnswer(invocation -> invocation.getArgument(1) + "\n市场热度：74/100（高）");
        AlertNotificationService service = new AlertNotificationService(feishuBotApi, marketSentimentService);

        service.send(signal("BTCUSDT", "1"));

        verify(feishuBotApi).sendGroupMessage(anyString(), contains("市场热度：74/100（高）"), eq(true));
    }

    @SuppressWarnings("unchecked")
    private Map<String, String> getStringRecordMap(AlertNotificationService service, String fieldName) {
        return (Map<String, String>) ReflectionTestUtils.getField(service, fieldName);
    }

    private AlertSignal signal(String symbol, String type) {
        return new AlertSignal("Legacy Alert", kline(symbol), type);
    }

    private AlertSignal type2Signal(String symbol) {
        return new AlertSignal(
                "Low Volume MA20",
                kline(symbol),
                "2",
                AlertCooldownCategory.LOW_VOLUME_MA20,
                "1m振幅：12%",
                FeishuCardTemplate.YELLOW
        );
    }

    private AlertNotificationService newService(FeishuBotApi feishuBotApi) {
        MarketSentimentService marketSentimentService = mock(MarketSentimentService.class);
        when(marketSentimentService.enrichBody(anyString(), anyString()))
                .thenAnswer(invocation -> invocation.getArgument(1));
        return new AlertNotificationService(feishuBotApi, marketSentimentService);
    }

    private BinanceKlineDTO kline(String symbol) {
        BinanceKlineDTO kline = new BinanceKlineDTO();
        kline.setSymbol(symbol);
        kline.setClose("1");
        kline.setHigh("2");
        kline.setLow("1");
        kline.setVolume("100000");
        kline.setEndTime(System.currentTimeMillis());
        return kline;
    }
}
