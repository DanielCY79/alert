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

@Service
public class AlertSymbolCacheService {

    private final BinanceApi binanceApi;
    private final Path cacheFilePath = Paths.get(System.getProperty("user.dir"), "symbolsCache.json");

    public AlertSymbolCacheService(BinanceApi binanceApi) {
        this.binanceApi = binanceApi;
    }

    /**
     * 交易对列表按天缓存，避免每分钟都去拉取一次全量交易对。
     */
    public BinanceSymbolsDTO loadSymbols() throws IOException {
        if (Files.exists(cacheFilePath) && Files.size(cacheFilePath) > 0) {
            BasicFileAttributes attrs = Files.readAttributes(cacheFilePath, BasicFileAttributes.class);
            LocalDateTime lastModifiedTime = attrs.lastModifiedTime()
                    .toInstant()
                    .atZone(ZoneId.systemDefault())
                    .toLocalDateTime();

            if (isToday(lastModifiedTime)) {
                // 当天缓存直接复用，只有内容为空时才重新刷新。
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

    private boolean isToday(LocalDateTime dateTime) {
        return LocalDate.now().equals(dateTime.toLocalDate());
    }

    private void clearFileContent() throws IOException {
        Files.writeString(cacheFilePath, "", StandardCharsets.UTF_8, StandardOpenOption.TRUNCATE_EXISTING);
    }

    private BinanceSymbolsDTO refreshSymbols() {
        // 刷新后立即覆盖本地缓存，方便下一轮调度直接读取。
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
