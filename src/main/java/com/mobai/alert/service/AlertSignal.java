package com.mobai.alert.service;

import com.mobai.alert.dto.BinanceKlineDTO;

import java.math.BigDecimal;

/**
 * 轻量通知对象，用来在策略判断层和通知层之间传递告警信息。
 */
public class AlertSignal {
    private final String title;
    private final BinanceKlineDTO kline;
    private final String type;
    private final String detail;
    private final BigDecimal triggerPrice;
    private final BigDecimal stopPrice;

    public AlertSignal(String title, BinanceKlineDTO kline, String type, String detail,
                       BigDecimal triggerPrice, BigDecimal stopPrice) {
        this.title = title;
        this.kline = kline;
        this.type = type;
        this.detail = detail;
        this.triggerPrice = triggerPrice;
        this.stopPrice = stopPrice;
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

    public String getDetail() {
        return detail;
    }

    public BigDecimal getTriggerPrice() {
        return triggerPrice;
    }

    public BigDecimal getStopPrice() {
        return stopPrice;
    }
}
