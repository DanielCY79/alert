package com.mobai.alert.api;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpEntity;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FeishuBotApiTest {

    @Test
    void shouldSendInteractiveCardWithYellowHeaderForHighlightedMessage() {
        RestTemplate restTemplate = mock(RestTemplate.class);
        FeishuBotApi api = new FeishuBotApi(restTemplate);
        ReflectionTestUtils.setField(api, "webhookUrl", "http://localhost/test");
        when(restTemplate.postForObject(eq("http://localhost/test"), any(HttpEntity.class), eq(String.class)))
                .thenReturn("{\"code\":0}");

        api.sendGroupMessage("Test Title", "Close: 1.0000 USDT", true);

        ArgumentCaptor<HttpEntity> requestCaptor = ArgumentCaptor.forClass(HttpEntity.class);
        verify(restTemplate).postForObject(eq("http://localhost/test"), requestCaptor.capture(), eq(String.class));

        Map<String, Object> payload = castMap(requestCaptor.getValue().getBody());
        assertEquals("interactive", payload.get("msg_type"));

        Map<String, Object> card = castMap(payload.get("card"));
        Map<String, Object> header = castMap(card.get("header"));
        Map<String, Object> title = castMap(header.get("title"));
        List<Map<String, Object>> elements = castList(card.get("elements"));
        Map<String, Object> bodyElement = elements.get(0);
        Map<String, Object> text = castMap(bodyElement.get("text"));

        assertEquals("yellow", header.get("template"));
        assertEquals("plain_text", title.get("tag"));
        assertEquals("Test Title", title.get("content"));
        assertEquals("lark_md", text.get("tag"));
        assertEquals("Close: 1.0000 USDT", text.get("content"));
    }

    @Test
    void shouldSetGreyHeaderTemplateWhenMessageIsNotHighlighted() {
        RestTemplate restTemplate = mock(RestTemplate.class);
        FeishuBotApi api = new FeishuBotApi(restTemplate);
        ReflectionTestUtils.setField(api, "webhookUrl", "http://localhost/test");
        when(restTemplate.postForObject(eq("http://localhost/test"), any(HttpEntity.class), eq(String.class)))
                .thenReturn("{\"code\":0}");

        api.sendGroupMessage("Test Title", "Close: 1.0000 USDT", false);

        ArgumentCaptor<HttpEntity> requestCaptor = ArgumentCaptor.forClass(HttpEntity.class);
        verify(restTemplate).postForObject(eq("http://localhost/test"), requestCaptor.capture(), eq(String.class));

        Map<String, Object> payload = castMap(requestCaptor.getValue().getBody());
        Map<String, Object> card = castMap(payload.get("card"));
        Map<String, Object> header = castMap(card.get("header"));

        assertEquals("grey", header.get("template"));
    }

    @Test
    void shouldSetBlueHeaderTemplateForOpportunityMessage() {
        RestTemplate restTemplate = mock(RestTemplate.class);
        FeishuBotApi api = new FeishuBotApi(restTemplate);
        ReflectionTestUtils.setField(api, "webhookUrl", "http://localhost/test");
        when(restTemplate.postForObject(eq("http://localhost/test"), any(HttpEntity.class), eq(String.class)))
                .thenReturn("{\"code\":0}");

        api.sendGroupMessage("BitLanglang Opportunity", "Profile: breakout", FeishuCardTemplate.BLUE);

        ArgumentCaptor<HttpEntity> requestCaptor = ArgumentCaptor.forClass(HttpEntity.class);
        verify(restTemplate).postForObject(eq("http://localhost/test"), requestCaptor.capture(), eq(String.class));

        Map<String, Object> payload = castMap(requestCaptor.getValue().getBody());
        Map<String, Object> card = castMap(payload.get("card"));
        Map<String, Object> header = castMap(card.get("header"));

        assertEquals("blue", header.get("template"));
    }

    @Test
    void shouldSetPurpleHeaderTemplateForDailyMa20VolumeSpikeMessage() {
        RestTemplate restTemplate = mock(RestTemplate.class);
        FeishuBotApi api = new FeishuBotApi(restTemplate);
        ReflectionTestUtils.setField(api, "webhookUrl", "http://localhost/test");
        when(restTemplate.postForObject(eq("http://localhost/test"), any(HttpEntity.class), eq(String.class)))
                .thenReturn("{\"code\":0}");

        api.sendGroupMessage("Daily MA20 Volume Spike", "Volume ratio: 3.00x", FeishuCardTemplate.PURPLE);

        ArgumentCaptor<HttpEntity> requestCaptor = ArgumentCaptor.forClass(HttpEntity.class);
        verify(restTemplate).postForObject(eq("http://localhost/test"), requestCaptor.capture(), eq(String.class));

        Map<String, Object> payload = castMap(requestCaptor.getValue().getBody());
        Map<String, Object> card = castMap(payload.get("card"));
        Map<String, Object> header = castMap(card.get("header"));

        assertEquals("purple", header.get("template"));
    }

    @Test
    void shouldSendNewLogicMessageToDedicatedWebhook() {
        RestTemplate restTemplate = mock(RestTemplate.class);
        FeishuBotApi api = new FeishuBotApi(restTemplate);
        ReflectionTestUtils.setField(api, "webhookUrl", "http://localhost/default");
        ReflectionTestUtils.setField(api, "newLogicWebhookUrl", "http://localhost/new-logic");
        when(restTemplate.postForObject(eq("http://localhost/new-logic"), any(HttpEntity.class), eq(String.class)))
                .thenReturn("{\"code\":0}");

        api.sendGroupMessageToNewLogicWebhook("New Logic Alert", "Volume ratio: 3.00x", FeishuCardTemplate.PURPLE);

        verify(restTemplate).postForObject(eq("http://localhost/new-logic"), any(HttpEntity.class), eq(String.class));
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> castMap(Object value) {
        return (Map<String, Object>) value;
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> castList(Object value) {
        return (List<Map<String, Object>>) value;
    }
}
