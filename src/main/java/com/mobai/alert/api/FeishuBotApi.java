package com.mobai.alert.api;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;

@Component
public class FeishuBotApi {

    private final RestTemplate restTemplate;

    @Value("${notification.feishu.webhook-url}")
    private String webhookUrl;

    public FeishuBotApi(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    public void sendGroupMessage(String msg) {
        Map<String, Object> message = new HashMap<>();
        message.put("msg_type", "text");

        Map<String, String> content = new HashMap<>();
        content.put("text", msg);
        message.put("content", content);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        HttpEntity<Map<String, Object>> request = new HttpEntity<>(message, headers);
        String response = restTemplate.postForObject(webhookUrl, request, String.class);
        System.out.println("Response from Feishu Bot: " + response);
    }
}
