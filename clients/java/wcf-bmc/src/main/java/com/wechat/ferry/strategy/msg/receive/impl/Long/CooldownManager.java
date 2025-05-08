package com.wechat.ferry.strategy.msg.receive.impl.Long;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 冷却时间管理器（按群ID控制）
 */
public class CooldownManager {
    // 线程安全的缓存：Key=群ID, Value=最后一次处理时间
    private static final Map<String, LocalDateTime> COOLDOWN_MAP = new ConcurrentHashMap<>();

    /**
     * 检查是否在冷却期内
     * @param roomId 群ID
     * @param cooldownSeconds 冷却时间（秒）
     * @return true=在冷却期内，false=可执行
     */
    public static boolean isInCooldown(String roomId, int cooldownSeconds) {
        LocalDateTime lastTime = COOLDOWN_MAP.get(roomId);
        if (lastTime == null) {
            return false; // 从未执行过，允许执行
        }
        long secondsPassed = java.time.Duration.between(lastTime, LocalDateTime.now()).getSeconds();
        return secondsPassed < cooldownSeconds;
    }

    /**
     * 更新最后一次执行时间
     * @param roomId 群ID
     */
    public static void updateCooldown(String roomId) {
        COOLDOWN_MAP.put(roomId, LocalDateTime.now());
    }
}
