package com.mobai.alert.service;

/**
 * 告警冷却维度。
 */
public enum AlertCooldownCategory {

    LEGACY("legacy"),
    LOW_VOLUME_MA20("low_volume_ma20");

    private final String code;

    AlertCooldownCategory(String code) {
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}
