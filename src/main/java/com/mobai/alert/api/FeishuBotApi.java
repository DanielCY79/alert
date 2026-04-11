package com.mobai.alert.api;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 飞书机器人通知封装，负责组装交互式卡片并发送到群 webhook。
 */
@Component
public class FeishuBotApi {

    private final RestTemplate restTemplate;

    @Value("${notification.feishu.webhook-url}")
    private String webhookUrl;

    public FeishuBotApi(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    /**
     * 发送飞书群消息。
     *
     * @param title 标题
     * @param body 正文
     * @param highlightTitle 是否高亮标题
     */
    public void sendGroupMessage(String title, String body, boolean highlightTitle) {
        Map<String, Object> message = new HashMap<>();
        message.put("msg_type", "interactive");
        message.put("card", buildCard(title, body, highlightTitle));

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        HttpEntity<Map<String, Object>> request = new HttpEntity<>(message, headers);
        String response = restTemplate.postForObject(webhookUrl, request, String.class);
        System.out.println("Response from Feishu Bot: " + response);
    }

    /**
     * 构造飞书交互式卡片。
     */
    private Map<String, Object> buildCard(String title, String body, boolean highlightTitle) {
        Map<String, Object> card = new HashMap<>();

        Map<String, Object> config = new HashMap<>();
        config.put("wide_screen_mode", true);
        card.put("config", config);

        Map<String, Object> header = new HashMap<>();
        Map<String, String> headerTitle = new HashMap<>();
        headerTitle.put("tag", "plain_text");
        headerTitle.put("content", title);
        header.put("title", headerTitle);
        if (highlightTitle) {
            header.put("template", "yellow");
        } else {
            header.put("template", "grey");
        }
        card.put("header", header);

        List<Map<String, Object>> elements = new ArrayList<>();
        Map<String, Object> bodyElement = new HashMap<>();
        bodyElement.put("tag", "div");

        Map<String, String> text = new HashMap<>();
        text.put("tag", "lark_md");
        text.put("content", body);
        bodyElement.put("text", text);
        elements.add(bodyElement);

        card.put("elements", elements);
        return card;
    }
}
