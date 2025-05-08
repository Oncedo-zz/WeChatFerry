package com.wechat.ferry.config;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;

/**
 * 接龙功能配置类
 * 说明：所有配置参数在此修改，重启生效
 */
public class JielongConfig {
    // 日期时间格式（示例：2023-08-20 15:30:00）
    public static final DateTimeFormatter DATE_FORMAT =
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    // 每个群最大历史记录数
    public static final int MAX_HISTORY = 7;

    // 触发接龙的关键词（支持文字和表情）
    public static final List<String> KEY_WORDS =
        Arrays.asList("龙", "🐲", "🐉", "🐍");

    // 功能开放时间段（09:00-20:00）
    public static final LocalTime ACTIVE_START = LocalTime.parse("09:00");
    public static final LocalTime ACTIVE_END = LocalTime.parse("23:50");

    // 系统提示时间段（每日两次）
    public static final LocalTime NOTIFY_START1 = LocalTime.parse("11:00");
    public static final LocalTime NOTIFY_END1 = LocalTime.parse("12:00");
    public static final LocalTime NOTIFY_START2 = LocalTime.parse("20:00");
    public static final LocalTime NOTIFY_END2 = LocalTime.parse("21:00");
}
