package com.mobai.alert.service;

import com.mobai.alert.api.FeishuCardTemplate;
import com.mobai.alert.dto.BinanceKlineDTO;

/**
 * 告警信号对象，用于在规则判断层和通知层之间传递结果。
 */
public class AlertSignal {

    private final String title;
    private final BinanceKlineDTO kline;
    private final String type;
    private final AlertCooldownCategory cooldownCategory;
    private final String body;
    private final FeishuCardTemplate template;

    public AlertSignal(String title, BinanceKlineDTO kline, String type) {
        this(title, kline, type, AlertCooldownCategory.LEGACY, null, null);
    }

    public AlertSignal(String title,
                       BinanceKlineDTO kline,
                       String type,
                       AlertCooldownCategory cooldownCategory,
                       String body,
                       FeishuCardTemplate template) {
        this.title = title;
        this.kline = kline;
        this.type = type;
        this.cooldownCategory = cooldownCategory == null ? AlertCooldownCategory.LEGACY : cooldownCategory;
        this.body = body;
        this.template = template;
    }

    /**
     * 获取告警标题。
     */
    public String getTitle() {
        return title;
    }

    /**
     * 获取触发告警的 K 线数据。
     */
    public BinanceKlineDTO getKline() {
        return kline;
    }

    /**
     * 获取告警类型编码。
     */
    public String getType() {
        return type;
    }

    public AlertCooldownCategory getCooldownCategory() {
        return cooldownCategory;
    }

    public String getBody() {
        return body;
    }

    public FeishuCardTemplate getTemplate() {
        return template;
    }
}
