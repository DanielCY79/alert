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
        sendGroupMessage(title, body, highlightTitle ? FeishuCardTemplate.YELLOW : FeishuCardTemplate.GREY);
    }

    /**
     * 鍙戦€侀涔︾兢娑堟伅銆?
     *
     * @param title 鏍囬
     * @param body 姝ｆ枃
     * @param template card header template
     */
    public void sendGroupMessage(String title, String body, FeishuCardTemplate template) {
        sendGroupMessage(webhookUrl, title, body, template);
    }

    private void sendGroupMessage(String targetWebhookUrl, String title, String body, FeishuCardTemplate template) {
        Map<String, Object> message = new HashMap<>();
        message.put("msg_type", "interactive");
        message.put("card", buildCard(title, body, template));

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        HttpEntity<Map<String, Object>> request = new HttpEntity<>(message, headers);
        String response = restTemplate.postForObject(targetWebhookUrl, request, String.class);
        System.out.println("Response from Feishu Bot: " + response);
    }

    /**
     * 构造飞书交互式卡片。
     */
    private Map<String, Object> buildCard(String title, String body, FeishuCardTemplate template) {
        Map<String, Object> card = new HashMap<>();

        Map<String, Object> config = new HashMap<>();
        config.put("wide_screen_mode", true);
        card.put("config", config);

        Map<String, Object> header = new HashMap<>();
        Map<String, String> headerTitle = new HashMap<>();
        headerTitle.put("tag", "plain_text");
        headerTitle.put("content", title);
        header.put("title", headerTitle);
        header.put("template", template.getTemplateCode());
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
