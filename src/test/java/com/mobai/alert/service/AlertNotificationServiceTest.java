package com.mobai.alert.service;

import com.mobai.alert.api.FeishuBotApi;
import com.mobai.alert.dto.BinanceKlineDTO;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Map;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

class AlertNotificationServiceTest {

    @Test
    void shouldHighlightFirstNotificationWithin24Hours() {
        FeishuBotApi feishuBotApi = mock(FeishuBotApi.class);
        AlertNotificationService service = new AlertNotificationService(feishuBotApi);

        service.send(signal("BTCUSDT", "1"));

        verify(feishuBotApi).sendGroupMessage(anyString(), anyString(), eq(true));
    }

    @Test
    void shouldKeepCooldownEvenWhenHighlightWindowExists() {
        FeishuBotApi feishuBotApi = mock(FeishuBotApi.class);
        AlertNotificationService service = new AlertNotificationService(feishuBotApi);

        service.send(signal("BTCUSDT", "1"));
        reset(feishuBotApi);

        service.send(signal("BTCUSDT", "1"));

        verifyNoInteractions(feishuBotApi);
    }

    @Test
    void shouldOnlyHighlightOnceWithin24HoursAfterCooldownExpires() {
        FeishuBotApi feishuBotApi = mock(FeishuBotApi.class);
        AlertNotificationService service = new AlertNotificationService(feishuBotApi);
        AlertSignal signal = signal("BTCUSDT", "1");
        String recordKey = "BTCUSDT1";

        service.send(signal);
        reset(feishuBotApi);

        Map<String, Long> sentRecords = getRecordMap(service, "sentRecords");
        sentRecords.put(recordKey, System.currentTimeMillis() - (2 * 60 * 60 * 1000L) - 1);

        service.send(signal);

        verify(feishuBotApi).sendGroupMessage(anyString(), anyString(), eq(false));
    }

    @Test
    void shouldHighlightAgainAfter24Hours() {
        FeishuBotApi feishuBotApi = mock(FeishuBotApi.class);
        AlertNotificationService service = new AlertNotificationService(feishuBotApi);
        AlertSignal signal = signal("BTCUSDT", "1");
        String recordKey = "BTCUSDT1";

        service.send(signal);
        reset(feishuBotApi);

        Map<String, Long> sentRecords = getRecordMap(service, "sentRecords");
        Map<String, Long> highlightRecords = getRecordMap(service, "highlightRecords");
        sentRecords.put(recordKey, System.currentTimeMillis() - (2 * 60 * 60 * 1000L) - 1);
        highlightRecords.put(recordKey, System.currentTimeMillis() - (24 * 60 * 60 * 1000L) - 1);

        service.send(signal);

        verify(feishuBotApi).sendGroupMessage(anyString(), anyString(), eq(true));
    }

    @SuppressWarnings("unchecked")
    private Map<String, Long> getRecordMap(AlertNotificationService service, String fieldName) {
        return (Map<String, Long>) ReflectionTestUtils.getField(service, fieldName);
    }

    private AlertSignal signal(String symbol, String type) {
        BinanceKlineDTO kline = new BinanceKlineDTO();
        kline.setSymbol(symbol);
        kline.setClose("1");
        kline.setHigh("2");
        kline.setLow("1");
        kline.setVolume("100000");
        kline.setEndTime(System.currentTimeMillis());
        return new AlertSignal("测试通知", kline, type);
    }
}
