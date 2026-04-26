package com.mobai.alert.service;

import com.mobai.alert.api.FeishuCardTemplate;
import com.mobai.alert.api.FeishuBotApi;
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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class AlertNotificationServiceTest {

    @Test
    void shouldHighlightFirstNotificationOfTheDay() {
        FeishuBotApi feishuBotApi = mock(FeishuBotApi.class);
        AlertNotificationService service = newService(feishuBotApi);

        service.send(signal("BTCUSDT", "1"));

        verify(feishuBotApi).sendGroupMessage(anyString(), anyString(), eq(true));
    }

    @Test
    void shouldSuppressDifferentLegacyTypeForSameSymbolWithinSameNaturalDay() {
        FeishuBotApi feishuBotApi = mock(FeishuBotApi.class);
        AlertNotificationService service = newService(feishuBotApi);

        service.send(signal("BTCUSDT", "1"));
        reset(feishuBotApi);

        service.send(signal("BTCUSDT", "4"));

        verifyNoInteractions(feishuBotApi);
    }

    @Test
    void shouldStillSendForDifferentSymbolsWithinSameNaturalDay() {
        FeishuBotApi feishuBotApi = mock(FeishuBotApi.class);
        AlertNotificationService service = newService(feishuBotApi);

        service.send(signal("BTCUSDT", "1"));
        reset(feishuBotApi);

        service.send(signal("ETHUSDT", "4"));

        verify(feishuBotApi).sendGroupMessage(anyString(), anyString(), eq(true));
    }

    @Test
    void shouldHighlightAgainOnNextNaturalDay() {
        FeishuBotApi feishuBotApi = mock(FeishuBotApi.class);
        AlertNotificationService service = newService(feishuBotApi);
        AlertSignal firstSignal = signal("BTCUSDT", "1");
        AlertSignal secondSignal = signal("BTCUSDT", "4");
        String recordKey = AlertCooldownCategory.LEGACY.getCode() + ":BTCUSDT";

        service.send(firstSignal);
        reset(feishuBotApi);

        Map<String, String> sentRecords = getStringRecordMap(service, "sentRecords");
        sentRecords.put(recordKey, AppTime.today().minusDays(1).toString());

        service.send(secondSignal);

        verify(feishuBotApi).sendGroupMessage(anyString(), anyString(), eq(true));
    }

    @Test
    void shouldLoadTodaySentRecordFromLocalFile() throws IOException {
        FeishuBotApi feishuBotApi = mock(FeishuBotApi.class);
        AlertNotificationService service = newService(feishuBotApi);
        AlertSignal signal = signal("BTCUSDT", "4");
        String recordKey = AlertCooldownCategory.LEGACY.getCode() + ":BTCUSDT";
        Path tempFile = Files.createTempFile("feishu-highlight-records", ".json");

        try {
            Files.writeString(
                    tempFile,
                    "{\"" + recordKey + "\":\"" + AppTime.today() + "\"}",
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
    void shouldKeepLegacyAndNewCategoryCooldownIndependent() {
        FeishuBotApi feishuBotApi = mock(FeishuBotApi.class);
        AlertNotificationService service = newService(feishuBotApi);

        service.send(signal("BTCUSDT", "1"));
        reset(feishuBotApi);

        service.send(new AlertSignal(
                "Daily MA20 Volume Spike",
                kline("BTCUSDT"),
                "5",
                AlertCooldownCategory.DAILY_MA20_VOLUME_SPIKE,
                "Volume ratio: 3.00x",
                FeishuCardTemplate.PURPLE
        ));

        verify(feishuBotApi).sendGroupMessageToNewLogicWebhook(anyString(), contains("Volume ratio"), eq(FeishuCardTemplate.PURPLE));
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
