package com.wechat.ferry.strategy.msg.receive.impl.Long;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONWriter;
import com.alibaba.fastjson2.TypeReference;
import com.wechat.ferry.config.JielongConfig;
import com.wechat.ferry.config.WeChatFerryProperties;
import com.wechat.ferry.entity.vo.response.WxPpWcfGroupMemberResp;
import com.wechat.ferry.service.WeChatDllService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.*;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;

/**
 * 接龙核心处理模块
 * 功能：处理消息、管理历史记录、与微信服务交互
 */
@Component
public class GetLongModule {
    private static final Logger log = LoggerFactory.getLogger(GetLongModule.class);

    @Autowired
    private WeChatFerryProperties weChatFerryProperties;

    // 依赖注入微信服务（用于发送消息、查询群成员）
    private final WeChatDllService weChatDllService;

    // 文件操作锁（防止多线程同时写文件导致数据损坏）
    private final ReentrantLock fileLock = new ReentrantLock();

    // 内存缓存：存储各群的接龙历史记录（Key=群ID，Value=记录列表）
    private final Map<String, List<Map<String, String>>> history = new HashMap<>();

    // 构造函数注入依赖（必须添加@Autowired）
    @Autowired
    public GetLongModule(WeChatDllService weChatDllService) {
        this.weChatDllService = weChatDllService;
        loadAllHistory(); // 启动时自动加载历史记录
    }

    // ------------------------ 核心入口方法 ------------------------

    /**
     * 处理消息的主入口
     * @param msg 微信消息对象
     * @return 是否已处理该消息
     */
    public boolean processMessage(WxMsg msg) throws InterruptedException {
        // 忽略机器人自己发送的消息
        if (msg.getIsSelf()) {
            log.debug("[安全机制] 忽略机器人自己发送的消息");
            return false;
        }

        log.info("[消息处理] 收到消息 - 发送者: {}, 群ID: {}", msg.getSender(), msg.getRoomId());

        // 判断是否为触发关键词（如"龙"）
        if (isTriggerMessage(msg)) {
            handleTriggerMessage(msg);
            return true;
        } else {
            // 处理普通消息（可能是接龙内容）
            log.debug("处理普通消息");
            return handleNormalMessage(msg);
        }
    }

    // ------------------------ 触发消息处理 ------------------------

    /**
     * 判断是否为触发消息（关键词）
     */
    private boolean isTriggerMessage(WxMsg msg) {
        return JielongConfig.KEY_WORDS.contains(msg.getContent().trim());
    }

    /**
     * 处理触发关键词消息（如用户发送"龙"）
     */
    private void handleTriggerMessage(WxMsg msg) throws InterruptedException {
        LocalTime currentTime = LocalTime.now();
        String roomId = msg.getRoomId();

        if (isActiveTime(currentTime, roomId)) {
            log.info("[触发处理] 在有效时段内处理请求");
            if (!CooldownManager.isInCooldown(roomId, 60)) {
                processActiveTrigger(msg);
                CooldownManager.updateCooldown(msg.getRoomId()); // 更新冷却时间
            } else {
                log.info("[触发处理] 在冷却期内，忽略请求");
            }

        } else if (isNotifyTime(currentTime)) {
            log.info("[触发处理] 发送时段提示");
            sendTimeReminder(roomId);
        } else {
            log.info("[触发处理] 非活跃时段，忽略请求");
        }
    }

    /**
     * 处理有效时段内的触发请求
     */
    private void processActiveTrigger(WxMsg msg) {
        String roomId = msg.getRoomId();
        Map<String, String> lastRecord = getLastRecord(roomId);
        LocalDate recordDate = parseDate(lastRecord.get("date"));

        if (recordDate == null || !isSameDay(recordDate)) {
            handleNewRecord(roomId, msg); // 新记录处理
        } else {
            handleExistingRecord(lastRecord.get("text"), roomId); // 已有记录处理
        }
    }

    // ------------------------ 普通消息处理 ------------------------

    /**
     * 处理普通消息（可能是接龙内容）
     */
    private boolean handleNormalMessage(WxMsg msg) {
        String content = msg.getContent();
        if (isJielongContent(content)) {
            log.info("[接龙保存] 检测到接龙格式内容");
            saveRecord(msg.getRoomId(), sanitizeContent(content));
            return true;
        }
        return false;
    }

    // ------------------------ 工具方法 ------------------------

    /**
     * 处理新接龙请求（当天第一次接龙）
     * @param roomId 群ID
     * @param msg 消息对象
     */
    private void handleNewRecord(String roomId, WxMsg msg) {
        try {
            log.info("[新接龙] 检测到新接龙请求，发送者: {}", msg.getSender());

            // 1. 查询群成员信息（用于获取用户昵称）
            List<WxPpWcfGroupMemberResp> members =
                weChatDllService.queryGroupMemberList(msg.getRoomId());

            // 2. 查找发送者的群昵称
            String nickname = members.stream()
                .filter(m -> Objects.equals(m.getWeChatUid(), msg.getSender()))
                .findFirst()
                .map(WxPpWcfGroupMemberResp::getGroupNickName)
                .orElse("未知用户"); // 兜底处理

            // 2. 查找发送者的群昵称
            String nickname_dong = members.stream()
                .filter(m -> Objects.equals(m.getWeChatUid(), "rlcckeke"))
                .findFirst()
                .map(WxPpWcfGroupMemberResp::getGroupNickName)
                .orElse("未知用户"); // 兜底处理

            // 3. 构建提醒消息并发送
            String reminder = String.format(
                "@%s 东哥，%s 请你召唤神龙！",
                nickname_dong,
                nickname
            );

            sendMessage(roomId, reminder);

            // 4. 随机延迟（模拟人类操作）
            Thread.sleep(500 + (long) (Math.random() * 700));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("[新接龙] 处理被中断", e);
        } catch (Exception e) {
            log.error("[新接龙] 处理失败", e);
        }
    }

// ------------------------ 已有记录处理 ------------------------

    /**
     * 处理已有接龙记录（当天已有接龙）
     * @param lastText 上一次接龙内容
     * @param roomId 群ID
     */
    private void handleExistingRecord(String lastText, String roomId) {
        try {
            log.info("[已有接龙] 处理群 {} 的现有记录", roomId);

            // 1. 过滤特殊内容（示例：移除包含"松松的小跟班"的行）
            String processedText = Arrays.stream(lastText.split("\n"))
                .filter(line -> !line.contains("松松的小跟班"))
                .collect(Collectors.joining("\n"));

            // 2. 发送处理后的接龙内容
            sendJielongContent(roomId, processedText);

            // 3. 可选：添加新序号（示例自动追加新条目）
//            String newEntry = "\n" + (processedText.split("\n").length + 1) + ". [自动追加]";
//            saveRecord(roomId, processedText + newEntry);
        } catch (Exception e) {
            log.error("[已有接龙] 处理失败", e);
        }
    }

    /**
     * 判断是否是接龙格式内容
     */
    private boolean isJielongContent(String content) {
        System.out.println("判断是否是接龙消息");
        System.out.println("content.contains(\"#接龙\")" + content.contains("#接龙"));
        System.out.println("(content.contains(\"1.\") || content.contains(\".1\"))" + (content.contains("1.") || content.contains(".1")));
        return content.contains("#接龙") && (content.contains("1.") || content.contains(".1"));
    }
    // 检查冷却时间（1分钟 = 60秒）

    /**
     * 检查当前时间是否在有效时段内
     */
    private boolean isActiveTime(LocalTime currentTime, String roomid) {
        return !currentTime.isBefore(JielongConfig.ACTIVE_START) &&
            !currentTime.isAfter(JielongConfig.ACTIVE_END);
    }

    /**
     * 检查是否在提示时段
     */
    private boolean isNotifyTime(LocalTime currentTime) {
        return (currentTime.isAfter(JielongConfig.NOTIFY_START1) &&
            currentTime.isBefore(JielongConfig.NOTIFY_END1)) ||
            (currentTime.isAfter(JielongConfig.NOTIFY_START2) &&
                currentTime.isBefore(JielongConfig.NOTIFY_END2));
    }

    // ------------------------ 记录管理 ------------------------

    /**
     * 获取指定群的最后一条记录
     */
    private Map<String, String> getLastRecord(String roomId) {
        // 首次加载历史记录
        if (history.isEmpty()) {
            log.info("[记录加载] 初始化加载历史数据");
            loadAllHistory();
        }

        List<Map<String, String>> records = history.getOrDefault(roomId, new ArrayList<>());
        return records.isEmpty() ?
            createEmptyRecord() :
            records.get(records.size() - 1); // 取最后一条
    }

    /**
     * 创建空记录模板
     */
    private Map<String, String> createEmptyRecord() {
        Map<String, String> record = new HashMap<>();
        record.put("text", "暂无历史记录");
        record.put("date", "");
        return record;
    }

    /**
     * 保存记录到内存并持久化到文件
     */
    private synchronized void saveRecord(String roomId, String text) {
        Map<String, String> newRecord = new HashMap<>();
        newRecord.put("date", LocalDateTime.now().format(JielongConfig.DATE_FORMAT));
        newRecord.put("text", text);

        List<Map<String, String>> records = history.computeIfAbsent(
            roomId,
            k -> new ArrayList<>(JielongConfig.MAX_HISTORY + 1)
        );

        records.add(newRecord);

        // 限制历史记录数量
        if (records.size() > JielongConfig.MAX_HISTORY) {
            records = records.subList(
                records.size() - JielongConfig.MAX_HISTORY,
                records.size()
            );
            history.put(roomId, new ArrayList<>(records));
        }

        saveToFile(); // 立即保存到文件
        log.info("[记录保存] 群 {} 保存成功，当前记录数: {}", roomId, records.size());
    }

    // ------------------------ 文件操作 ------------------------

    /**
     * 保存数据到JSON文件
     */
    private void saveToFile() {
        fileLock.lock(); // 加锁保证线程安全
        try {
            File targetDir = new File(weChatFerryProperties.getFileSavePath() + "\\SupportFiles");
            if (!targetDir.exists() && !targetDir.mkdirs()) {
                log.error("[文件操作] 无法创建目录: {}", targetDir.getAbsolutePath());
                return;
            }

            File file = new File(targetDir, "last_jielong_content.json");
            try (FileWriter writer = new FileWriter(file)) {
                String json = JSON.toJSONString(history, JSONWriter.Feature.PrettyFormat);
                writer.write(json);
                log.debug("[文件操作] 数据保存成功，路径: {}", file.getAbsolutePath());
            }
        } catch (Exception e) {
            log.error("[文件操作] 保存文件失败", e);
        } finally {
            fileLock.unlock(); // 确保释放锁
        }
    }

    /**
     * 加载历史记录文件
     */
    private void loadAllHistory() {
        File file = new File(resourcePath("SupportFiles"), "last_jielong_content.json");
        if (!file.exists()) {
            log.warn("[文件操作] 历史文件不存在，跳过加载");
            return;
        }

        fileLock.lock();
        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String content = reader.lines().collect(Collectors.joining());
            if (content.isEmpty()) {
                log.warn("[文件操作] 历史文件为空");
                return;
            }

            Map<String, List<Map<String, String>>> allData =
                JSON.parseObject(content, new TypeReference<Map<String, List<Map<String, String>>>>() {});

            history.clear();
            if (allData != null) {
                history.putAll(allData);
                log.info("[文件操作] 成功加载历史记录，群数量: {}", allData.size());
            }
        } catch (Exception e) {
            log.error("[文件操作] 加载历史记录失败", e);
        } finally {
            fileLock.unlock();
        }
    }

    // ------------------------ 消息发送 ------------------------

    /**
     * 发送时段提示消息
     */
    private void sendTimeReminder(String roomId) {
        String reminder = "【温馨提示】接龙功能开放时间：09:00-20:00";
        sendMessage(roomId, reminder);
    }

    /**
     * 发送接龙内容
     */
    private void sendJielongContent(String roomId, String content) {
        if (content == null || content.trim().isEmpty()) {
            log.error("[消息发送] 内容为空，取消发送");
            return;
        }
        sendMessage(roomId, content);
    }

    /**
     * 通用消息发送方法
     */
    private void sendMessage(String roomId, String content) {
        try {
            weChatDllService.sendTextMsg(
                roomId,
                content,
                Collections.emptyList(),
                false
            );
            log.info("[消息发送] 发送成功 -> 群: {}", roomId);
        } catch (Exception e) {
            log.error("[消息发送] 发送失败", e);
        }
    }

    // ------------------------ 其他辅助方法 ------------------------

    /**
     * 解析日期字符串
     */
    private LocalDate parseDate(String dateStr) {
        try {
            return dateStr.isEmpty() ?
                null :
                LocalDateTime.parse(dateStr, JielongConfig.DATE_FORMAT).toLocalDate();
        } catch (Exception e) {
            log.warn("[日期解析] 格式错误: {}", dateStr);
            return null;
        }
    }

    /**
     * 判断是否是当天
     */
    private boolean isSameDay(LocalDate recordDate) {
        return recordDate != null && recordDate.equals(LocalDate.now());
    }

    /**
     * 净化消息内容（移除非法字符）
     */
    private String sanitizeContent(String text) {
        return text.replaceAll("[\\x00-\\x1F&&[^\\n]]", "") // 移除控制字符（保留换行）
            .replaceAll("[ \\t]{2,}", " "); // 合并连续空格
    }

    /**
     * 获取资源路径（兼容开发和生产环境）
     */
    private String resourcePath(String relativePath) {
        String basePath = System.getProperty("user.dir");
        return basePath + File.separator + relativePath;
    }
}
