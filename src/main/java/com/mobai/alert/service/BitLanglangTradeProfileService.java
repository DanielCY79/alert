package com.mobai.alert.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Build a scan profile from the local BitLanglang trade history workbook.
 */
@Service
public class BitLanglangTradeProfileService {

    private static final BigDecimal DEFAULT_MOVE_RATE = new BigDecimal("0.0100");

    private final BitLanglangTradeHistoryLoader tradeHistoryLoader;

    @Value("${scanner.bitlanglang.history.path:C:/Users/Administrator/Desktop/bit浪浪BTC.xlsx}")
    private String historyPath;

    @Value("${scanner.bitlanglang.history.active-hour-top-n:6}")
    private int activeHourTopN;

    @Value("${scanner.bitlanglang.history.min-trades:30}")
    private int minimumTradeCount;

    private volatile BitLanglangTradeProfile cachedProfile;
    private volatile Path cachedPath;
    private volatile long cachedLastModified;

    public BitLanglangTradeProfileService(BitLanglangTradeHistoryLoader tradeHistoryLoader) {
        this.tradeHistoryLoader = tradeHistoryLoader;
    }

    public Optional<BitLanglangTradeProfile> getActiveProfile() {
        Path workbookPath = resolveHistoryPath();
        if (workbookPath == null || !Files.exists(workbookPath)) {
            return Optional.empty();
        }

        long lastModified;
        try {
            lastModified = Files.getLastModifiedTime(workbookPath).toMillis();
        } catch (IOException e) {
            return Optional.empty();
        }

        BitLanglangTradeProfile profile = cachedProfile;
        if (profile != null
                && workbookPath.equals(cachedPath)
                && lastModified == cachedLastModified) {
            return Optional.of(profile);
        }

        synchronized (this) {
            if (cachedProfile != null
                    && workbookPath.equals(cachedPath)
                    && lastModified == cachedLastModified) {
                return Optional.of(cachedProfile);
            }

            try {
                List<BitLanglangTradeRecord> records = tradeHistoryLoader.load(workbookPath);
                BitLanglangTradeProfile rebuiltProfile = buildProfile(records, workbookPath, lastModified);
                if (rebuiltProfile == null) {
                    return Optional.empty();
                }
                cachedProfile = rebuiltProfile;
                cachedPath = workbookPath;
                cachedLastModified = lastModified;
                return Optional.of(rebuiltProfile);
            } catch (IOException e) {
                System.out.println("Failed to load BitLanglang workbook: " + e.getMessage());
                return Optional.empty();
            }
        }
    }

    private Path resolveHistoryPath() {
        if (!StringUtils.hasText(historyPath)) {
            return null;
        }
        return Paths.get(historyPath.trim());
    }

    private BitLanglangTradeProfile buildProfile(List<BitLanglangTradeRecord> records, Path workbookPath, long lastModified) {
        if (CollectionUtils.isEmpty(records) || records.size() < minimumTradeCount) {
            return null;
        }

        String primarySymbol = records.stream()
                .collect(Collectors.groupingBy(BitLanglangTradeRecord::getNormalizedSymbol, Collectors.counting()))
                .entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse(null);
        if (!StringUtils.hasText(primarySymbol)) {
            return null;
        }

        List<BitLanglangTradeRecord> primaryRecords = records.stream()
                .filter(record -> primarySymbol.equals(record.getNormalizedSymbol()))
                .collect(Collectors.toList());
        if (primaryRecords.size() < minimumTradeCount) {
            return null;
        }

        List<Integer> activeHours = primaryRecords.stream()
                .collect(Collectors.groupingBy(record -> record.getEntryTime().getHour(), Collectors.counting()))
                .entrySet().stream()
                .sorted(Map.Entry.<Integer, Long>comparingByValue().reversed())
                .limit(Math.max(1, activeHourTopN))
                .map(Map.Entry::getKey)
                .sorted()
                .collect(Collectors.toList());

        List<BitLanglangTradeRecord> longRecords = primaryRecords.stream()
                .filter(record -> record.getSide() == BitLanglangTradeSide.LONG)
                .collect(Collectors.toList());
        List<BitLanglangTradeRecord> shortRecords = primaryRecords.stream()
                .filter(record -> record.getSide() == BitLanglangTradeSide.SHORT)
                .collect(Collectors.toList());

        BigDecimal tradeCount = BigDecimal.valueOf(primaryRecords.size());
        BigDecimal longShare = divide(BigDecimal.valueOf(longRecords.size()), tradeCount);
        BigDecimal shortShare = divide(BigDecimal.valueOf(shortRecords.size()), tradeCount);
        BigDecimal medianHoldMinutes = median(primaryRecords.stream().map(BitLanglangTradeRecord::getHoldMinutes).collect(Collectors.toList()));
        BigDecimal medianLeverage = median(primaryRecords.stream().map(BitLanglangTradeRecord::getLeverage).collect(Collectors.toList()));

        BigDecimal longMoveAnchor = moveAnchor(longRecords);
        BigDecimal shortMoveAnchor = moveAnchor(shortRecords);
        int breakoutLookback = clamp(Math.round(medianHoldMinutes.floatValue() / 5.0f), 3, 8);
        BigDecimal volumeMultiplierMin = clamp(
                new BigDecimal("2.00").subtract(medianHoldMinutes.divide(new BigDecimal("60"), 4, RoundingMode.HALF_UP)),
                new BigDecimal("1.25"),
                new BigDecimal("2.00")
        );

        return new BitLanglangTradeProfile(
                workbookPath,
                lastModified,
                primaryRecords.size(),
                primarySymbol,
                activeHours,
                medianHoldMinutes,
                medianLeverage,
                longShare,
                shortShare,
                breakoutLookback,
                volumeMultiplierMin,
                clamp(longMoveAnchor.divide(new BigDecimal("5"), 6, RoundingMode.HALF_UP), new BigDecimal("0.0010"), new BigDecimal("0.0060")),
                clamp(shortMoveAnchor.divide(new BigDecimal("5"), 6, RoundingMode.HALF_UP), new BigDecimal("0.0010"), new BigDecimal("0.0060")),
                clamp(longMoveAnchor.divide(new BigDecimal("8"), 6, RoundingMode.HALF_UP), new BigDecimal("0.0008"), new BigDecimal("0.0040")),
                clamp(shortMoveAnchor.divide(new BigDecimal("8"), 6, RoundingMode.HALF_UP), new BigDecimal("0.0008"), new BigDecimal("0.0040")),
                clamp(longMoveAnchor.divide(new BigDecimal("3"), 6, RoundingMode.HALF_UP), new BigDecimal("0.0030"), new BigDecimal("0.0200")),
                clamp(shortMoveAnchor.divide(new BigDecimal("3"), 6, RoundingMode.HALF_UP), new BigDecimal("0.0030"), new BigDecimal("0.0200"))
        );
    }

    private BigDecimal moveAnchor(List<BitLanglangTradeRecord> records) {
        List<BigDecimal> profitableMoves = records.stream()
                .filter(record -> record.getProfitUsd().compareTo(BigDecimal.ZERO) > 0)
                .map(BitLanglangTradeRecord::getDirectionalMoveRate)
                .filter(rate -> rate.compareTo(BigDecimal.ZERO) > 0)
                .collect(Collectors.toList());
        if (profitableMoves.isEmpty()) {
            profitableMoves = records.stream()
                    .map(BitLanglangTradeRecord::getDirectionalMoveRate)
                    .filter(rate -> rate.compareTo(BigDecimal.ZERO) > 0)
                    .collect(Collectors.toList());
        }
        if (profitableMoves.isEmpty()) {
            return DEFAULT_MOVE_RATE;
        }
        return percentile(profitableMoves, 0.5);
    }

    private BigDecimal median(List<BigDecimal> values) {
        return percentile(values, 0.5);
    }

    private BigDecimal percentile(List<BigDecimal> values, double percentile) {
        if (CollectionUtils.isEmpty(values)) {
            return BigDecimal.ZERO;
        }
        List<BigDecimal> sorted = values.stream()
                .sorted()
                .collect(Collectors.toList());
        if (sorted.size() == 1) {
            return sorted.get(0);
        }
        double index = percentile * (sorted.size() - 1);
        int lower = (int) Math.floor(index);
        int upper = (int) Math.ceil(index);
        if (lower == upper) {
            return sorted.get(lower);
        }
        BigDecimal lowerValue = sorted.get(lower);
        BigDecimal upperValue = sorted.get(upper);
        BigDecimal weight = BigDecimal.valueOf(index - lower);
        return lowerValue.add(upperValue.subtract(lowerValue).multiply(weight)).setScale(6, RoundingMode.HALF_UP);
    }

    private BigDecimal divide(BigDecimal numerator, BigDecimal denominator) {
        if (denominator == null || denominator.signum() == 0) {
            return BigDecimal.ZERO;
        }
        return numerator.divide(denominator, 6, RoundingMode.HALF_UP);
    }

    private int clamp(long value, int min, int max) {
        return (int) Math.max(min, Math.min(max, value));
    }

    private BigDecimal clamp(BigDecimal value, BigDecimal min, BigDecimal max) {
        if (value.compareTo(min) < 0) {
            return min;
        }
        if (value.compareTo(max) > 0) {
            return max;
        }
        return value;
    }
}
