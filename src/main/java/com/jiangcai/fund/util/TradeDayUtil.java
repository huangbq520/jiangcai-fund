package com.jiangcai.fund.util;

import org.springframework.stereotype.Component;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Set;

/**
 * 交易日判断工具类
 * 用于判断当前是否为A股交易时间
 */
@Component
public class TradeDayUtil {
    
    // A股交易时间：9:30 - 11:30, 13:00 - 15:00
    private static final LocalTime MORNING_START = LocalTime.of(9, 30);
    private static final LocalTime MORNING_END = LocalTime.of(11, 30);
    private static final LocalTime AFTERNOON_START = LocalTime.of(13, 0);
    private static final LocalTime AFTERNOON_END = LocalTime.of(15, 0);
    
    // 预设的2024-2025年部分节假日（实际应用中应连接数据库或外部API获取完整数据）
    // 这里简化处理，实际项目建议使用专门的节假日数据源
    private static final Set<LocalDate> HOLIDAYS = Set.of(
        // 2024年元旦
        LocalDate.of(2024, 1, 1),
        // 2024年春节
        LocalDate.of(2024, 2, 10), LocalDate.of(2024, 2, 11), LocalDate.of(2024, 2, 12),
        LocalDate.of(2024, 2, 13), LocalDate.of(2024, 2, 14), LocalDate.of(2024, 2, 15), LocalDate.of(2024, 2, 16),
        // 2024年清明
        LocalDate.of(2024, 4, 4), LocalDate.of(2024, 4, 5), LocalDate.of(2024, 4, 6),
        // 2024年劳动节
        LocalDate.of(2024, 5, 1), LocalDate.of(2024, 5, 2), LocalDate.of(2024, 5, 3), LocalDate.of(2024, 5, 4), LocalDate.of(2024, 5, 5),
        // 2024年端午节
        LocalDate.of(2024, 6, 10),
        // 2024年中秋节
        LocalDate.of(2024, 9, 17),
        // 2024年国庆节
        LocalDate.of(2024, 10, 1), LocalDate.of(2024, 10, 2), LocalDate.of(2024, 10, 3),
        LocalDate.of(2024, 10, 4), LocalDate.of(2024, 10, 5), LocalDate.of(2024, 10, 6), LocalDate.of(2024, 10, 7),
        // 2025年元旦
        LocalDate.of(2025, 1, 1),
        // 2025年春节
        LocalDate.of(2025, 1, 28), LocalDate.of(2025, 1, 29), LocalDate.of(2025, 1, 30),
        LocalDate.of(2025, 1, 31), LocalDate.of(2025, 2, 1), LocalDate.of(2025, 2, 2), LocalDate.of(2025, 2, 3),
        // 2025年清明
        LocalDate.of(2025, 4, 4), LocalDate.of(2025, 4, 5), LocalDate.of(2025, 4, 6),
        // 2025年劳动节
        LocalDate.of(2025, 5, 1), LocalDate.of(2025, 5, 2), LocalDate.of(2025, 5, 3),
        // 2025年端午节
        LocalDate.of(2025, 5, 31),
        // 2025年中秋节
        LocalDate.of(2025, 10, 6),
        // 2025年国庆节
        LocalDate.of(2025, 10, 1), LocalDate.of(2025, 10, 2), LocalDate.of(2025, 10, 3),
        LocalDate.of(2025, 10, 4), LocalDate.of(2025, 10, 5), LocalDate.of(2025, 10, 6), LocalDate.of(2025, 10, 7)
    );
    
    /**
     * 判断是否为交易日
     */
    public boolean isTradeDay(LocalDate date) {
        // 周末不是交易日
        DayOfWeek dayOfWeek = date.getDayOfWeek();
        if (dayOfWeek == DayOfWeek.SATURDAY || dayOfWeek == DayOfWeek.SUNDAY) {
            return false;
        }
        // 节假日不是交易日
        if (HOLIDAYS.contains(date)) {
            return false;
        }
        return true;
    }
    
    /**
     * 判断当前是否为交易日
     */
    public boolean isTradeDay() {
        return isTradeDay(LocalDate.now());
    }
    
    /**
     * 判断是否为休市状态
     * 休市状态：非交易日、或交易日 9:30 前 / 15:00 后
     */
    public boolean isMarketClosed() {
        return isMarketClosed(LocalDateTime.now());
    }
    
    /**
     * 判断指定时间是否为休市状态
     */
    public boolean isMarketClosed(LocalDateTime dateTime) {
        // 非交易日
        if (!isTradeDay(dateTime.toLocalDate())) {
            return true;
        }
        // 交易日内判断时间
        LocalTime time = dateTime.toLocalTime();
        // 9:30前休市
        if (time.isBefore(MORNING_START)) {
            return true;
        }
        // 11:30-13:00休市
        if (time.isAfter(MORNING_END) && time.isBefore(AFTERNOON_START)) {
            return true;
        }
        // 15:00后休市
        if (time.isAfter(AFTERNOON_END)) {
            return true;
        }
        return false;
    }
    
    /**
     * 判断是否为开盘状态
     * 开盘状态：交易日 9:30-15:00
     */
    public boolean isMarketOpen() {
        return !isMarketClosed();
    }
    
    /**
     * 获取上一个交易日
     */
    public LocalDate getLastTradeDay() {
        return getLastTradeDay(LocalDate.now());
    }
    
    /**
     * 获取指定日期的上一个交易日
     */
    public LocalDate getLastTradeDay(LocalDate date) {
        LocalDate result = date.minusDays(1);
        while (!isTradeDay(result)) {
            result = result.minusDays(1);
        }
        return result;
    }
    
    /**
     * 获取当前市场状态描述
     */
    public String getMarketStatus() {
        return isMarketClosed() ? "CLOSED" : "OPEN";
    }
}