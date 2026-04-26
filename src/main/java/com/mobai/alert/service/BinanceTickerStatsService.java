package com.mobai.alert.service;

import com.mobai.alert.api.BinanceApi;
import com.mobai.alert.dto.BinanceTicker24hrDTO;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 24 小时成交额统计服务。
 */
@Service
public class BinanceTickerStatsService {

    private static final BigDecimal MINUTES_PER_DAY = new BigDecimal("1440");

    private final BinanceApi binanceApi;
    private final Map<String, BigDecimal> averageOneMinuteVolumes = new ConcurrentHashMap<>();

    public BinanceTickerStatsService(BinanceApi binanceApi) {
        this.binanceApi = binanceApi;
    }

    /**
     * 刷新全市场 24 小时统计快照。
     */
    public synchronized void refreshSnapshot() {
        List<BinanceTicker24hrDTO> tickerStats = binanceApi.list24hrTickerStats();
        if (CollectionUtils.isEmpty(tickerStats)) {
            return;
        }

        Map<String, BigDecimal> nextSnapshot = new HashMap<>();
        for (BinanceTicker24hrDTO ticker : tickerStats) {
            if (ticker == null
                    || !StringUtils.hasText(ticker.getSymbol())
                    || !StringUtils.hasText(ticker.getQuoteVolume())) {
                continue;
            }

            try {
                BigDecimal quoteVolume = new BigDecimal(ticker.getQuoteVolume());
                if (quoteVolume.signum() <= 0) {
                    continue;
                }
                nextSnapshot.put(
                        ticker.getSymbol(),
                        quoteVolume.divide(MINUTES_PER_DAY, 6, RoundingMode.HALF_UP)
                );
            } catch (Exception ignored) {
            }
        }

        if (nextSnapshot.isEmpty()) {
            return;
        }

        averageOneMinuteVolumes.clear();
        averageOneMinuteVolumes.putAll(nextSnapshot);
    }

    /**
     * 获取指定交易对过去 24 小时的 1 分钟平均成交额。
     */
    public BigDecimal getAverageOneMinuteVolume(String symbol) {
        return averageOneMinuteVolumes.get(symbol);
    }
}
