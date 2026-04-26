package com.mobai.alert.dto;

/**
 * Binance K 线数据传输对象。
 */
public class BinanceKlineDTO {

    /**
     * 交易对名称。
     */
    private String symbol;

    /**
     * K 线周期，例如 1m、5m。
     */
    private String interval;

    /**
     * 开始时间，毫秒时间戳。
     */
    private Long startTime;

    /**
     * 结束时间，毫秒时间戳。
     */
    private Long endTime;

    /**
     * 时区参数。
     */
    private String timeZone;

    /**
     * 返回条数限制，默认 500，最大 1000。
     */
    private Integer limit;

    /**
     * 最低价。
     */
    private String low;

    /**
     * 最高价。
     */
    private String high;

    /**
     * 开盘价。
     */
    private String open;

    /**
     * 收盘价。
     */
    private String close;

    /**
     * 成交量。
     */
    private String amount;

    /**
     * 成交额。
     */
    private String volume;

    /**
     * 开盘时间文本。
     */
    private String openTime;

    /**
     * 收盘时间文本。
     */
    private String closeTime;

    public String getSymbol() {
        return symbol;
    }

    public void setSymbol(String symbol) {
        this.symbol = symbol;
    }

    public String getInterval() {
        return interval;
    }

    public void setInterval(String interval) {
        this.interval = interval;
    }

    public Long getStartTime() {
        return startTime;
    }

    public void setStartTime(Long startTime) {
        this.startTime = startTime;
    }

    public Long getEndTime() {
        return endTime;
    }

    public void setEndTime(Long endTime) {
        this.endTime = endTime;
    }

    public String getTimeZone() {
        return timeZone;
    }

    public void setTimeZone(String timeZone) {
        this.timeZone = timeZone;
    }

    public Integer getLimit() {
        return limit;
    }

    public void setLimit(Integer limit) {
        this.limit = limit;
    }

    public String getLow() {
        return low;
    }

    public void setLow(String low) {
        this.low = low;
    }

    public String getHigh() {
        return high;
    }

    public void setHigh(String high) {
        this.high = high;
    }

    public String getOpen() {
        return open;
    }

    public void setOpen(String open) {
        this.open = open;
    }

    public String getClose() {
        return close;
    }

    public void setClose(String close) {
        this.close = close;
    }

    public String getAmount() {
        return amount;
    }

    public void setAmount(String amount) {
        this.amount = amount;
    }

    public String getVolume() {
        return volume;
    }

    public void setVolume(String volume) {
        this.volume = volume;
    }

    public String getOpenTime() {
        return openTime;
    }

    public void setOpenTime(String openTime) {
        this.openTime = openTime;
    }

    public String getCloseTime() {
        return closeTime;
    }

    public void setCloseTime(String closeTime) {
        this.closeTime = closeTime;
    }
}
