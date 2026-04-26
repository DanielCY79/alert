package com.mobai.alert.service;

/**
 * 告警冷却维度。
 */
public enum AlertCooldownCategory {

    LEGACY("legacy"),
    DAILY_MA20_VOLUME_SPIKE("daily_ma20_volume_spike");

    private final String code;

    AlertCooldownCategory(String code) {
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}
