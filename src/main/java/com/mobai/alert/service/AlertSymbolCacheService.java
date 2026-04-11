package com.mobai.alert.service;

import com.alibaba.fastjson.JSON;
import com.mobai.alert.api.BinanceApi;
import com.mobai.alert.dto.BinanceSymbolsDTO;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Objects;

/**
 * 交易对缓存服务，按天缓存交易对列表，减少对 Binance 的重复请求。
 */
@Service
public class AlertSymbolCacheService {

    private final BinanceApi binanceApi;
    private final Path cacheFilePath = Paths.get(System.getProperty("user.dir"), "symbolsCache.json");

    public AlertSymbolCacheService(BinanceApi binanceApi) {
        this.binanceApi = binanceApi;
    }

    /**
     * 加载交易对列表。
     * 当天缓存可直接复用，跨天后会重新拉取并覆盖本地缓存。
     */
    public BinanceSymbolsDTO loadSymbols() throws IOException {
        if (Files.exists(cacheFilePath) && Files.size(cacheFilePath) > 0) {
            BasicFileAttributes attrs = Files.readAttributes(cacheFilePath, BasicFileAttributes.class);
            LocalDateTime lastModifiedTime = attrs.lastModifiedTime()
                    .toInstant()
                    .atZone(ZoneId.systemDefault())
                    .toLocalDateTime();

            if (isToday(lastModifiedTime)) {
                String content = Files.readString(cacheFilePath, StandardCharsets.UTF_8);
                if (!StringUtils.hasText(content) || "{}".equals(content)) {
                    return refreshSymbols();
                }
                return JSON.parseObject(content, BinanceSymbolsDTO.class);
            }

            clearFileContent();
        }

        return refreshSymbols();
    }

    /**
     * 判断缓存时间是否为当天。
     */
    private boolean isToday(LocalDateTime dateTime) {
        return LocalDate.now().equals(dateTime.toLocalDate());
    }

    /**
     * 清空旧缓存内容。
     */
    private void clearFileContent() throws IOException {
        Files.writeString(cacheFilePath, "", StandardCharsets.UTF_8, StandardOpenOption.TRUNCATE_EXISTING);
    }

    /**
     * 从远端刷新交易对列表，并立即写回本地缓存。
     */
    private BinanceSymbolsDTO refreshSymbols() {
        BinanceSymbolsDTO symbolsDTO = binanceApi.listSymbols();
        try {
            Files.writeString(
                    cacheFilePath,
                    Objects.requireNonNull(JSON.toJSONString(symbolsDTO)),
                    StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING
            );
            System.out.println("Updated file with new data.");
        } catch (IOException e) {
            e.printStackTrace();
        }
        return symbolsDTO;
    }
}
