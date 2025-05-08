package com.wechat.ferry.service.impl;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.wechat.ferry.entity.vo.response.WxPpWcfContactsResp;
import com.wechat.ferry.entity.vo.response.WxPpWcfGroupMemberResp;
import com.wechat.ferry.service.WeChatDllService;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.sound.midi.Soundbank;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;



/**
 * 微信 OCR 处理类（Java 8 实现版本）
 * 主要功能：处理微信回款截图 OCR 识别，解析金额、时间等信息并上传到 WPS
 */
@Slf4j
@Component
public class OcrResultServiceImpl {
    @Autowired
    private WeChatDllService weChatDllService;

    // 常量定义
    private static final String BANK = "台州银行";
    private static final List<String> PAYEES = Arrays.asList(
        "严江晏", "丁道法", "严*晏", "*江晏", "丁*法", "*道法"
    );
    private static final List<String> TRANSACTION_DATE_KEYS = Arrays.asList(
        "交易时间", "交易日期", "收款时间", "到账时间", "创建时间", "付款时间"
    );
    private static final List<String> AMOUNT_KEYS = Arrays.asList(
        "转账金额", "交易金额", "收款金额"
    );

    // HTTP 相关配置
    private static final String WPS_API_URL = "https://www.kdocs.cn/api/v3/ide/file/286603414736/script/V2-9YlaaHCYj3E5Osy1t9LNa/sync_task";
    private static final String AIR_SCRIPT_TOKEN = "5cLXqnjzArRkT3RdcOhz4m";

    // 正则表达式预编译（提升性能）
    private static final Pattern DATE_PATTERN = Pattern.compile(
        "(\\d{4}[-/年.]\\d{2}[-/月.]\\d{2}[日]?)\\s*(\\d{2}[:.]\\d{2}([:.]\\d{2})?)"
    );
    private static final Pattern AMOUNT_PATTERN = Pattern.compile(
        "[-+]?[0-9]*\\.?[0-9]+"
    );

    /**
     * 上传回款信息到 WPS
     * @param roomId 微信群ID
     * @param amount 金额
     * @param paymentTime 支付时间
     * @param confidence 可信度
     * @param payerName 付款人姓名
     * @return 服务器响应
     */
    public String uploadToWps(String roomId, double amount, String paymentTime, int confidence, String payerName) {
        OkHttpClient client = new OkHttpClient();

        // 构建 JSON 请求体
        JsonObject context = new JsonObject();
        JsonObject argv = new JsonObject();
        argv.addProperty("roomid", roomId);
        argv.addProperty("huikuan_je", String.valueOf(amount));
        argv.addProperty("huikuan_time", paymentTime);
        argv.addProperty("kexindu", confidence);
        argv.addProperty("huikuan_name", payerName);
        argv.addProperty("flag", "1");

        context.add("argv", argv);
        JsonObject payload = new JsonObject();
        payload.add("Context", context);

        RequestBody body = RequestBody.create(
            MediaType.parse("application/json"),
            payload.toString()
        );

        Request request = new Request.Builder()
            .url(WPS_API_URL)
            .post(body)
            .addHeader("Content-Type", "application/json")
            .addHeader("AirScript-Token", AIR_SCRIPT_TOKEN)
            .build();

        try (Response response = client.newCall(request).execute()) {
            return response.body().string();
        } catch (IOException e) {
            e.printStackTrace();
            return "{\"status\":\"error\"}";
        }
    }

    /**
     * 处理 OCR 识别结果核心逻辑
     * @param ocrResults OCR 识别结果列表
     * @return 包含金额和时间的 Map
     */
    public Map<String, Object> processOcrResults(List<String> ocrResults, String roomname) {
        Map<String, Object> result = new HashMap<>();
        System.out.println("-----开始解析OCR结果-----");

        // 1. 识别付款人
//        System.out.println("OCR结果解析---识别付款人");
        String payer = identifyPayer(ocrResults);
        if (payer.isEmpty()) {
            result.put("status", "not_payment_screenshot");
            return result;
        }
//        System.out.println("付款人：" + payer);

        // 2. 提取金额
//        System.out.println("OCR结果解析---提取金额");
        double amount = extractAmount(ocrResults);
//        System.out.println("回款金额：" + amount);

        // 3. 提取交易时间
//        System.out.println("OCR结果解析---提取交易时间");
        String paymentTime = extractPaymentTime(ocrResults);
//        System.out.println("交易时间：" + paymentTime);

        // 4. 计算可信度
//        System.out.println("OCR结果解析---计算可信度");
        int confidence = calculateConfidence(paymentTime);
//        System.out.println("可信度：" + confidence);

        String s = "店铺名称：" + roomname + "\n";
        s += "收款账号：" + payer + "\n";
        s += "回款金额：" + amount + "\n";
        s += "回款时间：" + paymentTime + "\n";
        s += "可信度：" + confidence + "\n";
        System.out.println(s);

        result.put("payer", payer);
        result.put("amount", amount);
        result.put("paymentTime", paymentTime);
        result.put("confidence", confidence);
        result.put("status", "success");
        return result;
    }

    /**
     * 识别付款人
     * @param ocrResults OCR 结果列表
     * @return 付款人姓名（如果匹配不到返回空字符串）
     */
    private String identifyPayer(List<String> ocrResults) {
        for (String text : ocrResults) {
            for (String payee : PAYEES) {
                if (text.contains(payee.replace("*", ""))) {
                    return payee.contains("晏") ? "严江晏" : "丁道法";
                }
            }
        }
        return "";
    }

    /**
     * 从 OCR 结果提取金额（合并文本版本）
     * @param ocrResults OCR 结果列表
     * @return 识别到的金额（未找到返回 0.0）
     */
    private double extractAmount(List<String> ocrResults) {
        String fullText = String.join(" ", ocrResults);
        log.debug("OCR_Result：" + fullText);

        // 排除模式：匹配需要跳过的金额模式（保持原样）
        final Pattern BALANCE_PATTERN = Pattern.compile(
            "(可用余额|余额)[:：]?\\s*([￥¥]?\\s*\\d{1,3}(?:,\\d{3})*\\.\\d{2})"
        );
        String filteredText = BALANCE_PATTERN.matcher(fullText).replaceAll("");

        // --- 新增：优先处理"转给：严江晏"场景 ---
        final Pattern YAN_PAYMENT_PATTERN = Pattern.compile(
            "(转给|向)[:：]\\s*严江晏[^¥￥]*?[-+]?\\s*[￥¥]\\s*((?:\\d{1,3}\\.)*\\d{1,3})(\\.\\d{2})?"  // 非贪婪匹配[^¥]*?
        );
        Matcher yanMatcher = YAN_PAYMENT_PATTERN.matcher(fullText);
        if (yanMatcher.find()) {
            String amountWhole = yanMatcher.group(2).replace(".", ""); // 移除千分位点（1.485 → 1485）
            String amountDecimal = yanMatcher.group(3) != null ? yanMatcher.group(3) : ".00";
            String formattedAmount = amountWhole + amountDecimal;
            log.debug("匹配成功，金额: {}", formattedAmount);
            return parseAmount(formattedAmount);
        }

        // 优化阶段1正则表达式：支持整数金额和符号位置变化
        final Pattern KEY_AFTER_AMOUNT = Pattern.compile(
            "(转给[:：]\\s*严江晏|收款金额|交易金额|转账凭证专用章|转账成功)" +
                "[\\s:：]*" +
                "([-+¥￥]\\s*-)?" +
                "(\\d{1,3}(?:,\\d{3})+(\\.\\d{2})?|" +
                "\\d{4,}(\\.\\d{2})?|" +
                "\\d+\\.\\d{2}|" +
                "\\d{1,3})" +
                "(?:\\s*[￥¥])?"
        );

        // 优先处理关键词后的金额
        Matcher afterMatcher = KEY_AFTER_AMOUNT.matcher(filteredText);
        if (afterMatcher.find()) {
            log.debug("[DEBUG] 完整匹配内容：" + afterMatcher.group(0));
            log.debug("[DEBUG] 关键词部分：" + afterMatcher.group(1));
            log.debug("[DEBUG] 符号部分：" + afterMatcher.group(2));
            log.debug("[DEBUG] 金额主体：" + afterMatcher.group(3));
            return parseAmountFromMatcher(afterMatcher, 3);
        }

        // 新增逻辑：处理金额在关键词前的情况（用于应对示例数据）
        final Pattern AMOUNT_BEFORE_KEY = Pattern.compile(
            "(\\d{1,3}(?:,\\d{3})*(?:\\.\\d{2})?)\\s*[￥¥]?" +  // 金额
                "\\s*(转账凭证专用章|转账成功|转出成功)"
        );
        Matcher beforeKeyMatcher = AMOUNT_BEFORE_KEY.matcher(filteredText);
        if (beforeKeyMatcher.find()) {
            log.debug("匹配到关键词前金额：" + beforeKeyMatcher.group(1));
            return parseAmountFromMatcher(beforeKeyMatcher, 1);
        }

        // 阶段2：匹配交易成功前的金额（使用原始文本）
        final Pattern KEY_BEFORE_AMOUNT = Pattern.compile(
            "((?!.*余额)[-+]?\\s*[￥¥]?\\s*(\\d{1,3}(?:,\\d{3})*\\.\\d{2}))" + // 排除余额后的金额
                "\\s*\\d{4}[年./]\\d{1,2}[月./]\\d{1,2}[日]?\\s*\\d{1,2}[:.]\\d{1,2}" +
                "\\s*(转出成功|转账成功|交易成功)"
        );

        Matcher beforeMatcher = KEY_BEFORE_AMOUNT.matcher(fullText);
        List<Double> successPayments = new ArrayList<>();
        while (beforeMatcher.find()) {
            successPayments.add(parseAmountFromMatcher(beforeMatcher, 1));
        }

        if (!successPayments.isEmpty()) {
            return successPayments.get(successPayments.size()-1);
        }

        // 保底逻辑：在过滤后的文本中取最大值
        return findMaxAmountInText(filteredText);
    }

    // 增强版金额解析（处理带千分位）
    // 修改后的金额解析方法（支持整数）
    private double parseAmountFromMatcher(Matcher matcher, int groupIndex) {
        try {
            String amountStr = matcher.group(groupIndex)
                .replaceAll("[^0-9.]", "") // 保留数字和点号
                .replace(",", ""); // 处理千分位

            // 处理无小数点情况
            if (!amountStr.contains(".")) {
                amountStr += ".00";
            }
            // 处理不完整小数
            String[] parts = amountStr.split("\\.");
            if (parts.length == 1) {
                amountStr += ".00";
            } else if (parts[1].length() == 1) {
                amountStr += "0";
            }

            return formatDecimal(Double.parseDouble(amountStr));
        } catch (Exception e) {
            log.error("金额解析失败：" + matcher.group(0));
            return 0.0;
        }
    }

    // 辅助方法：文本中查找最大金额
    private double findMaxAmountInText(String text) {
        List<Double> candidates = new ArrayList<>();
        Matcher matcher = Pattern.compile(
            "([+-]?\\s*[￥¥]?\\s*(\\d{1,3}(?:,\\d{3})*|\\d+)\\.\\d{2})"
        ).matcher(text.replace(",", ""));

        while (matcher.find()) {
            try {
                candidates.add(Double.parseDouble(matcher.group(1)
                    .replaceAll("[^0-9.]", "")));
            } catch (NumberFormatException ignored) {}
        }
        return candidates.stream()
            .max(Double::compare)
            .map(this::formatDecimal)
            .orElse(0.0);
    }

    // 强制保留两位小数（四舍五入）
    private double formatDecimal(double value) {
        return BigDecimal.valueOf(value)
            .setScale(2, RoundingMode.HALF_UP)
            .doubleValue();
    }

    private double parseAmount(String amountStr) {
        try {
            // 清理所有非数字字符并修复千分位
            String normalized = amountStr.replaceAll("[^0-9.]", "")
                .replaceAll("(?<=\\d)\\.(?=\\d{3})", ""); // 1.485 → 1485
            return Double.parseDouble(normalized);
        } catch (NumberFormatException e) {
            log.error("金额解析失败: {}", amountStr);
            return 0.0;
        }
    }
    /**
     * 提取交易时间
     * @param ocrResults OCR 结果列表
     * @return 格式化后的时间字符串（未找到返回空字符串）
     */
    private String extractPaymentTime(List<String> ocrResults) {
        StringBuilder fullText = new StringBuilder();
        for (String text : ocrResults) {
            fullText.append(text).append(" ");
        }
        String textToProcess = fullText.toString();

        //System.out.println("OCR_Result（时间提取）：" + textToProcess);

        String paymentTime = "";

        // 新增：匹配第一个“转账成功”前的日期时间
        int firstSuccessIndex = textToProcess.indexOf("转账成功");
        if (firstSuccessIndex != -1) {
            String textBeforeSuccess = textToProcess.substring(0, firstSuccessIndex);
            log.debug("第一个“转账成功”前的文本：" + textBeforeSuccess);

            Matcher matcher = DATE_PATTERN.matcher(textBeforeSuccess);
            if (matcher.find()) {
                paymentTime = formatDateTime(matcher.group(1), matcher.group(2));
                log.debug("在第一个“转账成功”前匹配到的日期时间: " + paymentTime);
                return paymentTime;
            } else {
                log.debug("在第一个“转账成功”前未匹配到日期时间");
            }
        }

        // 原有逻辑：匹配包含时间关键词的文本
        for (String text : ocrResults) {
            if (TRANSACTION_DATE_KEYS.stream().anyMatch(text::contains)) {
//                log.debug("匹配包含时间关键词的文本：" + text);
                Matcher matcher = DATE_PATTERN.matcher(text);
                if (matcher.find()) {
                    paymentTime = formatDateTime(matcher.group(1), matcher.group(2));
//                    log.debug("匹配到包含时间关键词的日期时间: " + paymentTime);
                    return paymentTime;
                } else {
                    log.debug("未匹配到包含时间关键词的日期时间");
                }
            }
        }

        // 原有逻辑：通用时间格式匹配
        for (String text : ocrResults) {
            log.debug("通用匹配尝试：" + text);
            Matcher matcher = DATE_PATTERN.matcher(text);
            if (matcher.find()) {
                paymentTime = formatDateTime(matcher.group(1), matcher.group(2));
                log.debug("通用匹配到的日期时间: " + paymentTime);
                return paymentTime;
            }
        }

        // 保底逻辑：在完整文本中查找所有匹配项，取第一个
        Matcher matcher = DATE_PATTERN.matcher(textToProcess);
        if (matcher.find()) {
            paymentTime = formatDateTime(matcher.group(1), matcher.group(2));
            log.debug("在完整文本中匹配到的第一个日期时间: " + paymentTime);
            return paymentTime;
        } else {
            log.error("未匹配到任何日期时间");
        }

        return paymentTime;
    }

    /**
     * 格式化日期时间字符串
     * @param datePart 日期部分
     * @param timePart 时间部分
     * @return 标准格式的日期时间字符串
     */
    private String formatDateTime(String datePart, String timePart) {
        try {
            // 统一替换分隔符
            // 将日期部分的点替换为减号
            String normalizedDate = datePart
                .replace("年", "-").replace("月", "-").replace("日", "")
                .replace("/", "-").replace(".", "-");

            // 处理时间部分
            String normalizedTime = timePart.replace(".", ":");

            log.debug("格式化前的日期部分：" + normalizedDate);
            log.debug("格式化前的时间部分：" + normalizedTime);

            // 解析日期时间
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
            LocalDateTime dt = LocalDateTime.parse(
                normalizedDate + " " + normalizedTime,
                DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm[:ss]")
            );
            return dt.format(formatter);
        } catch (DateTimeParseException e) {
            log.error("日期时间格式化失败: " + e.getMessage());
            return "";
        }
    }

    /**
     * 计算时间可信度
     * @param paymentTime 支付时间字符串
     * @return 可信度等级（0-10）
     */
    private int calculateConfidence(String paymentTime) {
        LocalDateTime now = LocalDateTime.now();

        // 如果未识别到时间
        if (paymentTime.isEmpty()) {
            return (now.getHour() >= 18) ? 4 : 2;
        }

        try {
            LocalDateTime paymentDateTime = LocalDateTime.parse(
                paymentTime,
                DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
            );

            // 判断是否在有效时间范围内
            if (now.getHour() >= 18) { // 18点之后
                return paymentDateTime.toLocalDate().equals(now.toLocalDate()) ? 1 : 10;
            } else { // 18点之前
                LocalDateTime yesterday18 = now.minusDays(1).withHour(18).withMinute(0);
                return paymentDateTime.isAfter(yesterday18) ? 1 : 5;
            }
        } catch (DateTimeParseException e) {
            return 0;
        }
    }

    /**
     * 完整处理流程示例
     */
    public void processPaymentScreenshot(String roomId, String roomname, List<String> ocrResults) {
        Map<String, Object> ocrData = processOcrResults(ocrResults, roomname);

        if ("success".equals(ocrData.get("status"))) {
            String response = uploadToWps(
                roomId,
                (Double) ocrData.get("amount"),
                (String) ocrData.get("paymentTime"),
                (Integer) ocrData.get("confidence"),
                (String) ocrData.get("payer")
            );

            // 处理 WPS 响应
            JsonObject jsonResponse = new Gson().fromJson(response, JsonObject.class);
            if ("finished".equals(jsonResponse.get("status").getAsString())) {
                System.out.println("数据成功写入 WPS");
                // TODO: 发送微信通知
            } else {
                System.out.println("WPS 写入失败");
            }
        }
    }
}
