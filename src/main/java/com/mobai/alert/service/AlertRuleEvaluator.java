package com.mobai.alert.service;

import com.mobai.alert.dto.BinanceKlineDTO;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
public class AlertRuleEvaluator {

    public static final String SIGNAL_STAGE2_BREAKOUT = "stage2-breakout";

    @Value("${monitoring.strategy.ma.short:10}")
    private int shortMaPeriod;

    @Value("${monitoring.strategy.ma.middle:20}")
    private int middleMaPeriod;

    @Value("${monitoring.strategy.ma.long:50}")
    private int longMaPeriod;

    @Value("${monitoring.strategy.breakout.lookback:20}")
    private int breakoutLookback;

    @Value("${monitoring.strategy.base.lookback:10}")
    private int baseLookback;

    @Value("${monitoring.strategy.volume.lookback:20}")
    private int volumeLookback;

    @Value("${monitoring.strategy.breakout.volume.multiplier:1.6}")
    private double breakoutVolumeMultiplier;

    @Value("${monitoring.strategy.volume.dry.factor:0.7}")
    private double volumeDryFactor;

    @Value("${monitoring.strategy.max.base.depth:0.18}")
    private double maxBaseDepth;

    @Value("${monitoring.strategy.stop.buffer:0.01}")
    private double stopBuffer;

    @Value("${monitoring.strategy.max.risk:0.08}")
    private double maxRisk;

    @Value("${monitoring.strategy.min.close.position.in.bar:0.65}")
    private double minClosePositionInBar;

    public Optional<AlertSignal> evaluateStageTwoBreakout(List<BinanceKlineDTO> klines) {
        if (CollectionUtils.isEmpty(klines)) {
            return Optional.empty();
        }

        List<Bar> bars = toBars(klines);
        int currentIndex = bars.size() - 2;
        int minimumBars = Math.max(longMaPeriod + 5, Math.max(breakoutLookback, volumeLookback) + baseLookback + 2);
        if (currentIndex < minimumBars) {
            return Optional.empty();
        }

        Bar current = bars.get(currentIndex);
        Bar previous = bars.get(currentIndex - 1);
        if (current.close.compareTo(current.open) <= 0) {
            return Optional.empty();
        }

        BigDecimal pivot = highestHigh(bars, currentIndex - breakoutLookback, currentIndex - 1);
        if (pivot == null || current.close.compareTo(pivot) <= 0 || previous.close.compareTo(pivot) > 0) {
            return Optional.empty();
        }

        if (!matchesTrendTemplate(bars, currentIndex)) {
            return Optional.empty();
        }

        BigDecimal baseLow = lowestLow(bars, currentIndex - baseLookback, currentIndex - 1);
        BigDecimal baseDepth = divide(pivot.subtract(baseLow), pivot);
        if (baseDepth.compareTo(decimal(maxBaseDepth)) > 0) {
            return Optional.empty();
        }

        if (!hasVolatilityContraction(bars, currentIndex - 1)) {
            return Optional.empty();
        }

        BigDecimal averageVolume = averageVolume(bars, currentIndex - volumeLookback, currentIndex - 1);
        if (averageVolume == null || averageVolume.compareTo(BigDecimal.ZERO) <= 0) {
            return Optional.empty();
        }

        BigDecimal dryUpVolume = averageVolume(bars, currentIndex - 5, currentIndex - 1);
        if (dryUpVolume == null || dryUpVolume.compareTo(averageVolume.multiply(decimal(volumeDryFactor))) >= 0) {
            return Optional.empty();
        }

        if (current.volume.compareTo(averageVolume.multiply(decimal(breakoutVolumeMultiplier))) < 0) {
            return Optional.empty();
        }

        BigDecimal closePosition = closePositionInBar(current);
        if (closePosition.compareTo(decimal(minClosePositionInBar)) < 0) {
            return Optional.empty();
        }

        BigDecimal stopPrice = min(baseLow, current.low).multiply(BigDecimal.ONE.subtract(decimal(stopBuffer)))
                .setScale(4, RoundingMode.HALF_UP);
        BigDecimal risk = divide(current.close.subtract(stopPrice), current.close);
        if (risk.compareTo(BigDecimal.ZERO) <= 0 || risk.compareTo(decimal(maxRisk)) > 0) {
            return Optional.empty();
        }

        String detail = buildBreakoutDetail(baseDepth, current.volume, averageVolume, pivot);
        return Optional.of(new AlertSignal(
                "SEPA二阶段放量突破",
                current.source,
                SIGNAL_STAGE2_BREAKOUT,
                detail,
                current.close.setScale(4, RoundingMode.HALF_UP),
                stopPrice
        ));
    }

    private boolean matchesTrendTemplate(List<Bar> bars, int currentIndex) {
        BigDecimal close = bars.get(currentIndex).close;
        BigDecimal maShort = averageClose(bars, currentIndex - shortMaPeriod + 1, currentIndex);
        BigDecimal maMiddle = averageClose(bars, currentIndex - middleMaPeriod + 1, currentIndex);
        BigDecimal maLong = averageClose(bars, currentIndex - longMaPeriod + 1, currentIndex);
        BigDecimal previousMaMiddle = averageClose(bars, currentIndex - middleMaPeriod - 4, currentIndex - 5);

        return maShort != null
                && maMiddle != null
                && maLong != null
                && previousMaMiddle != null
                && close.compareTo(maShort) > 0
                && maShort.compareTo(maMiddle) > 0
                && maMiddle.compareTo(maLong) > 0
                && maMiddle.compareTo(previousMaMiddle) > 0;
    }

    private boolean hasVolatilityContraction(List<Bar> bars, int baseEndIndex) {
        if (baseEndIndex < 9) {
            return false;
        }

        BigDecimal recent = averageRange(bars, baseEndIndex - 2, baseEndIndex);
        BigDecimal middle = averageRange(bars, baseEndIndex - 5, baseEndIndex - 3);
        BigDecimal early = averageRange(bars, baseEndIndex - 8, baseEndIndex - 6);
        return recent != null
                && middle != null
                && early != null
                && recent.compareTo(middle) < 0
                && middle.compareTo(early) <= 0;
    }

    private String buildBreakoutDetail(BigDecimal baseDepth, BigDecimal currentVolume, BigDecimal averageVolume,
                                       BigDecimal pivot) {
        BigDecimal depthPct = baseDepth.multiply(new BigDecimal("100")).setScale(2, RoundingMode.HALF_UP);
        BigDecimal volumeFactor = divide(currentVolume, averageVolume).setScale(2, RoundingMode.HALF_UP);
        BigDecimal pivotPrice = pivot.setScale(4, RoundingMode.HALF_UP);
        return "趋势模板通过，基底回撤 " + depthPct + "%，波动持续收缩，当前成交额放大至均量 "
                + volumeFactor + " 倍，并向上突破枢轴价 " + pivotPrice;
    }

    private List<Bar> toBars(List<BinanceKlineDTO> klines) {
        List<Bar> bars = new ArrayList<>(klines.size());
        for (BinanceKlineDTO kline : klines) {
            bars.add(new Bar(kline));
        }
        return bars;
    }

    private BigDecimal highestHigh(List<Bar> bars, int fromInclusive, int toInclusive) {
        if (fromInclusive < 0 || toInclusive >= bars.size() || fromInclusive > toInclusive) {
            return null;
        }
        BigDecimal highest = bars.get(fromInclusive).high;
        for (int i = fromInclusive + 1; i <= toInclusive; i++) {
            if (bars.get(i).high.compareTo(highest) > 0) {
                highest = bars.get(i).high;
            }
        }
        return highest;
    }

    private BigDecimal lowestLow(List<Bar> bars, int fromInclusive, int toInclusive) {
        if (fromInclusive < 0 || toInclusive >= bars.size() || fromInclusive > toInclusive) {
            return null;
        }
        BigDecimal lowest = bars.get(fromInclusive).low;
        for (int i = fromInclusive + 1; i <= toInclusive; i++) {
            if (bars.get(i).low.compareTo(lowest) < 0) {
                lowest = bars.get(i).low;
            }
        }
        return lowest;
    }

    private BigDecimal averageClose(List<Bar> bars, int fromInclusive, int toInclusive) {
        if (fromInclusive < 0 || toInclusive >= bars.size() || fromInclusive > toInclusive) {
            return null;
        }
        BigDecimal total = BigDecimal.ZERO;
        for (int i = fromInclusive; i <= toInclusive; i++) {
            total = total.add(bars.get(i).close);
        }
        return total.divide(BigDecimal.valueOf(toInclusive - fromInclusive + 1L), 6, RoundingMode.HALF_UP);
    }

    private BigDecimal averageVolume(List<Bar> bars, int fromInclusive, int toInclusive) {
        if (fromInclusive < 0 || toInclusive >= bars.size() || fromInclusive > toInclusive) {
            return null;
        }
        BigDecimal total = BigDecimal.ZERO;
        for (int i = fromInclusive; i <= toInclusive; i++) {
            total = total.add(bars.get(i).volume);
        }
        return total.divide(BigDecimal.valueOf(toInclusive - fromInclusive + 1L), 6, RoundingMode.HALF_UP);
    }

    private BigDecimal averageRange(List<Bar> bars, int fromInclusive, int toInclusive) {
        if (fromInclusive < 0 || toInclusive >= bars.size() || fromInclusive > toInclusive) {
            return null;
        }
        BigDecimal total = BigDecimal.ZERO;
        for (int i = fromInclusive; i <= toInclusive; i++) {
            total = total.add(bars.get(i).range);
        }
        return total.divide(BigDecimal.valueOf(toInclusive - fromInclusive + 1L), 6, RoundingMode.HALF_UP);
    }

    private BigDecimal closePositionInBar(Bar bar) {
        BigDecimal denominator = bar.high.subtract(bar.low);
        if (denominator.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ONE;
        }
        return bar.close.subtract(bar.low).divide(denominator, 6, RoundingMode.HALF_UP);
    }

    private BigDecimal divide(BigDecimal numerator, BigDecimal denominator) {
        if (denominator == null || denominator.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO;
        }
        return numerator.divide(denominator, 6, RoundingMode.HALF_UP);
    }

    private BigDecimal min(BigDecimal first, BigDecimal second) {
        return first.compareTo(second) <= 0 ? first : second;
    }

    private BigDecimal decimal(double value) {
        return BigDecimal.valueOf(value);
    }

    private static class Bar {
        private final BinanceKlineDTO source;
        private final BigDecimal open;
        private final BigDecimal high;
        private final BigDecimal low;
        private final BigDecimal close;
        private final BigDecimal volume;
        private final BigDecimal range;

        private Bar(BinanceKlineDTO source) {
            this.source = source;
            this.open = new BigDecimal(source.getOpen());
            this.high = new BigDecimal(source.getHigh());
            this.low = new BigDecimal(source.getLow());
            this.close = new BigDecimal(source.getClose());
            this.volume = new BigDecimal(source.getVolume());
            this.range = divide(high.subtract(low), low);
        }

        private BigDecimal divide(BigDecimal numerator, BigDecimal denominator) {
            if (denominator.compareTo(BigDecimal.ZERO) == 0) {
                return BigDecimal.ZERO;
            }
            return numerator.divide(denominator, 6, RoundingMode.HALF_UP);
        }
    }
}
