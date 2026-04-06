package com.mobai.alert.service;

import com.mobai.alert.dto.BinanceKlineDTO;

/**
 * 轻量通知对象，用来在业务判断层和通知层之间传递告警信息。
 */
public class AlertSignal {
    private final String title;
    private final BinanceKlineDTO kline;
    private final String type;

    public AlertSignal(String title, BinanceKlineDTO kline, String type) {
        this.title = title;
        this.kline = kline;
        this.type = type;
    }

    public String getTitle() {
        return title;
    }

    public BinanceKlineDTO getKline() {
        return kline;
    }

    public String getType() {
        return type;
    }
}
