package com.mobai.alert.service;

import com.mobai.alert.api.FeishuBotApi;
import com.mobai.alert.dto.BinanceKlineDTO;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.Map;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * 告警通知服务测试。
 */
class AlertNotificationServiceTest {

    /**
     * 当天首次通知应高亮标题。
     */
    @Test
    void shouldHighlightFirstNotificationOfTheDay() {
        FeishuBotApi feishuBotApi = mock(FeishuBotApi.class);
        AlertNotificationService service = new AlertNotificationService(feishuBotApi);

        service.send(signal("BTCUSDT", "1"));

        verify(feishuBotApi).sendGroupMessage(anyString(), anyString(), eq(true));
    }

    /**
     * 冷却期内重复发送应被直接拦截。
     */
    @Test
    void shouldKeepCooldownEvenWhenTodayHasNoGreyMessageYet() {
        FeishuBotApi feishuBotApi = mock(FeishuBotApi.class);
        AlertNotificationService service = new AlertNotificationService(feishuBotApi);

        service.send(signal("BTCUSDT", "1"));
        reset(feishuBotApi);

        service.send(signal("BTCUSDT", "1"));

        verifyNoInteractions(feishuBotApi);
    }

    /**
     * 同一天内冷却结束后再次通知，应切换为灰色标题。
     */
    @Test
    void shouldUseGreyAfterCooldownWhenItIsStillTheSameDay() {
        FeishuBotApi feishuBotApi = mock(FeishuBotApi.class);
        AlertNotificationService service = new AlertNotificationService(feishuBotApi);
        AlertSignal signal = signal("BTCUSDT", "1");
        String recordKey = "BTCUSDT1";

        service.send(signal);
        reset(feishuBotApi);

        Map<String, Long> sentRecords = getLongRecordMap(service, "sentRecords");
        sentRecords.put(recordKey, System.currentTimeMillis() - (2 * 60 * 60 * 1000L) - 1);

        service.send(signal);

        verify(feishuBotApi).sendGroupMessage(anyString(), anyString(), eq(false));
    }

    /**
     * 跨天后再次通知，应重新高亮标题。
     */
    @Test
    void shouldHighlightAgainOnNextNaturalDay() {
        FeishuBotApi feishuBotApi = mock(FeishuBotApi.class);
        AlertNotificationService service = new AlertNotificationService(feishuBotApi);
        AlertSignal signal = signal("BTCUSDT", "1");
        String recordKey = "BTCUSDT1";

        service.send(signal);
        reset(feishuBotApi);

        Map<String, Long> sentRecords = getLongRecordMap(service, "sentRecords");
        Map<String, String> highlightRecords = getStringRecordMap(service, "highlightRecords");
        sentRecords.put(recordKey, System.currentTimeMillis() - (2 * 60 * 60 * 1000L) - 1);
        highlightRecords.put(recordKey, LocalDate.now().minusDays(1).toString());

        service.send(signal);

        verify(feishuBotApi).sendGroupMessage(anyString(), anyString(), eq(true));
    }

    /**
     * 启动时应从本地文件恢复当天高亮记录。
     */
    @Test
    void shouldLoadTodayHighlightRecordFromLocalFile() throws IOException {
        FeishuBotApi feishuBotApi = mock(FeishuBotApi.class);
        AlertNotificationService service = new AlertNotificationService(feishuBotApi);
        AlertSignal signal = signal("BTCUSDT", "1");
        String recordKey = "BTCUSDT1";
        Path tempFile = Files.createTempFile("feishu-highlight-records", ".json");

        try {
            Files.writeString(
                    tempFile,
                    "{\"" + recordKey + "\":\"" + LocalDate.now() + "\"}",
                    StandardCharsets.UTF_8
            );
            ReflectionTestUtils.setField(service, "highlightRecordFilePath", tempFile);
            service.initHighlightRecords();

            Map<String, Long> sentRecords = getLongRecordMap(service, "sentRecords");
            sentRecords.put(recordKey, System.currentTimeMillis() - (2 * 60 * 60 * 1000L) - 1);

            service.send(signal);

            verify(feishuBotApi).sendGroupMessage(anyString(), anyString(), eq(false));
        } finally {
            Files.deleteIfExists(tempFile);
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Long> getLongRecordMap(AlertNotificationService service, String fieldName) {
        return (Map<String, Long>) ReflectionTestUtils.getField(service, fieldName);
    }

    @SuppressWarnings("unchecked")
    private Map<String, String> getStringRecordMap(AlertNotificationService service, String fieldName) {
        return (Map<String, String>) ReflectionTestUtils.getField(service, fieldName);
    }

    /**
     * 构造测试用告警信号。
     */
    private AlertSignal signal(String symbol, String type) {
        BinanceKlineDTO kline = new BinanceKlineDTO();
        kline.setSymbol(symbol);
        kline.setClose("1");
        kline.setHigh("2");
        kline.setLow("1");
        kline.setVolume("100000");
        kline.setEndTime(System.currentTimeMillis());
        return new AlertSignal("测试告警", kline, type);
    }
}
