package com.mobai.alert.service;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;

/**
 * 应用统一时间工具，固定使用东八区。
 */
public final class AppTime {

    public static final ZoneId ZONE_ID = ZoneId.of("Asia/Shanghai");

    private AppTime() {
    }

    public static LocalDate today() {
        return LocalDate.now(ZONE_ID);
    }

    public static LocalDateTime now() {
        return LocalDateTime.now(ZONE_ID);
    }

    public static LocalDateTime toLocalDateTime(Long epochMillis) {
        return LocalDateTime.ofInstant(Instant.ofEpochMilli(epochMillis), ZONE_ID);
    }
}
