package com.wechat.ferry.service.impl;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.wechat.ferry.service.WeChatDllService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Service
public class NongHangServiceImpl {

    private final WeChatDllService weChatDllService;

    @Autowired
    public NongHangServiceImpl(WeChatDllService weChatDllService) {
        this.weChatDllService = weChatDllService;
    }

    public String uploadToWps(String roomId, double je, LocalDateTime huikuanTime,
                              String kexindu, String huikuanName) {
        try {
            // 修改时间格式化为 yyyy-MM-dd HH:mm:ss
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
            String formattedTime = huikuanTime.format(formatter);

            JSONObject argv = new JSONObject();
            argv.put("roomid", roomId);
            argv.put("huikuan_je", String.valueOf(je));
            argv.put("huikuan_time", formattedTime); // 使用修正后的时间格式
            argv.put("kexindu", kexindu);
            argv.put("huikuan_name", huikuanName);
            argv.put("flag", "1");

            URL url = new URL("https://www.kdocs.cn/api/v3/ide/file/286603414736/script/V2-9YlaaHCYj3E5Osy1t9LNa/sync_task");
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setRequestProperty("AirScript-Token", "5cLXqnjzArRkT3RdcOhz4m");
            conn.setDoOutput(true);


            JSONObject context = new JSONObject();
            context.put("argv", argv);

            JSONObject payload = new JSONObject();
            payload.put("Context", context);

            // 记录请求参数
            log.debug("WPS 请求参数: {}", payload.toJSONString());

            try (OutputStream os = conn.getOutputStream()) {
                os.write(payload.toJSONString().getBytes(StandardCharsets.UTF_8));
            }

            int responseCode = conn.getResponseCode();
            if (responseCode != HttpURLConnection.HTTP_OK) {
                log.error("WPS 请求失败，状态码: {}，错误信息: {}", responseCode, conn.getResponseMessage());
                return null;
            }

            StringBuilder response = new StringBuilder();
            try (BufferedReader br = new BufferedReader(
                new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
                String responseLine;
                while ((responseLine = br.readLine()) != null) {
                    response.append(responseLine.trim());
                }
            }

            // 记录完整响应
            log.debug("WPS 响应内容: {}", response);
            return response.toString();
        } catch (Exception e) {
            log.error("WPS 请求异常", e);
            return null;
        }
    }

    private void writeToWpsAndSendWx(String roomId, double je, LocalDateTime huikuanTime,
                                     String huikuanName, String kexindu) {
        try {
            String wpsResults = uploadToWps(roomId, je, huikuanTime, kexindu, huikuanName);
            if (wpsResults == null || wpsResults.isEmpty()) {
                log.error("WPS 返回空响应");
                weChatDllService.sendTextMsg("zks2007", "WPS 服务异常，请稍后重试", Collections.emptyList(), false);
                return;
            }

            JSONObject resJson = JSONObject.parseObject(wpsResults);
            if (resJson == null) {
                log.error("WPS 响应解析失败");
                return;
            }

            // 优先处理 error 字段（即使为空）
            String error = resJson.getString("error");
            if (error != null && !error.isEmpty()) {
                log.error("WPS 接口错误: {}", error);
                weChatDllService.sendTextMsg("zks2007", "WPS 错误: " + error, Collections.emptyList(), false);
                return;
            }

            String status = resJson.getString("status");
            if (!"finished".equals(status)) {
                log.error("WPS 写入失败，状态: {}", status);
                weChatDllService.sendTextMsg("zks2007", "回款数据写入失败，状态: " + status, Collections.emptyList(), false);
                return;
            }

            JSONObject data = resJson.getJSONObject("data");
            if (data == null) {
                log.error("WPS 响应缺少 data 字段");
                return;
            }

            JSONArray results = data.getJSONArray("result");
            if (results == null || results.isEmpty()) {
                log.error("WPS 返回的 result 为空");
                weChatDllService.sendTextMsg("zks2007", "WPS 返回空数据，请联系管理员", Collections.emptyList(), false);
                return;
            }

            JSONArray firstResult = results.getJSONArray(0);
            if (firstResult == null || firstResult.size() < 7) {
                log.error("result 格式错误");
                return;
            }

            // 处理备注字段（检查第7个元素是否存在）
            if (firstResult.size() > 6) {
                String remark = firstResult.getString(6);
                if ("备注不提示".equals(remark)) {
                    return;
                }
            }

            JSONArray logs = data.getJSONArray("logs");
            if (logs == null || logs.isEmpty()) {
                log.error("logs 字段为空");
                return;
            }

            // 遍历日志检查 "执行完毕"
            boolean isCompleted = false;
            for (int i = 0; i < logs.size(); i++) {
                JSONObject logEntry = logs.getJSONObject(i);
                JSONArray args = logEntry.getJSONArray("args");
                if (args != null && !args.isEmpty() && "执行完毕".equals(args.getString(0))) {
                    isCompleted = true;
                    break;
                }
            }

            if (isCompleted) {
                LocalDateTime now = LocalDateTime.now();
                if (now.getHour() >= 9 && now.getHour() < 19) {
                    // 安全拼接前三个字段
                    StringBuilder text = new StringBuilder();
                    for (int i = 0; i < 3; i++) {
                        String value = firstResult.getString(i);
                        text.append(value != null ? value : "");
                    }
//                    weChatDllService.sendTextMsg("zks2007", text.toString(), Collections.emptyList(), false);
                } else {
                    log.warn("当前时间不在发送时段（9-19点）");
                }
            }
        } catch (Exception e) {
            log.error("处理WPS写入时发生错误", e);
        }
    }

    public void getNongHangJe(JSONObject content, String roomid, String pinpai) {
        try {
            // 1. 直接解析 content 中的 msg.appmsg 字段
            JSONObject msg = content.getJSONObject("msg");
            JSONObject appmsg = msg.getJSONObject("appmsg");

            // 2. 提取标题中的金额
            String title = appmsg.getString("title");
            Pattern amountPattern = Pattern.compile("转账(\\d+)元");
            Matcher amountMatcher = amountPattern.matcher(title);

            if (amountMatcher.find()) {
                String amountStr = amountMatcher.group(1);
                double amount = Double.parseDouble(amountStr);
                log.info("提取的转账金额: {}元", amount);

                // 3. 根据品牌调用写入逻辑
                switch (pinpai) {
                    case "街头男人帮":
                        writeToWpsAndSendWx(roomid, amount, LocalDateTime.now(), "严江晏", "1");
                        break;
                    case "衣号港仔":
                        writeToWpsAndSendWx(roomid, amount, LocalDateTime.now(), "丁道法", "1");
                        break;
                    default:
                        log.error("未知品牌: {}", pinpai);
                }
            } else {
                log.warn("标题中未找到金额信息: {}", title);
            }
        } catch (Exception e) {
            log.error("解析 JSON 数据失败", e);
        }
    }
}
