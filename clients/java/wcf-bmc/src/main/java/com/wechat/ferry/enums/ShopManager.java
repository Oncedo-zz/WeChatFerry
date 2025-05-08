package com.wechat.ferry.enums;

import com.wechat.ferry.config.WeChatFerryProperties;
import org.json.JSONArray;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * 店铺管理系统
 * 功能：自动获取最新店铺数据，分类存储，并提供查询接口
 * 使用说明：
 * 1. 直接运行main方法测试
 * 2. 调用静态方法获取数据（如ShopManager.getDpArea1()）
 * 3. 数据会自动缓存到项目目录/SupportFiles/rooms_info.json
 */
@Component
public class ShopManager {
    private static String FILE_SAVE_PATH;

    @Autowired
    public void setWeChatFerryProperties(WeChatFerryProperties weChatFerryProperties) {
        // 注入配置时同步设置静态路径
        FILE_SAVE_PATH = weChatFerryProperties.getFileSavePath();
    }

    //======================== 数据存储区 ========================
    // 区域店铺列表
    private static final List<String> dp_area1 = new ArrayList<>();   // 云南一区所有店铺
    private static final List<String> dp_area1B = new ArrayList<>();  // 云南一区B所有店铺

    // 品牌分类列表
    private static final List<String> dp_area1_jtnrb = new ArrayList<>();  // 云南一区-街头男人帮
    private static final List<String> dp_area1_yhgz = new ArrayList<>();   // 云南一区-衣号港仔
    private static final List<String> dp_area1B_jtnrb = new ArrayList<>(); // 云南一区B-街头男人帮
    private static final List<String> dp_area1B_yhgz = new ArrayList<>();  // 云南一区B-衣号港仔

    // 全局数据
    private static final List<String> dps_jtnrb = new ArrayList<>();  // 所有街头男人帮店铺（全区域）
    private static final List<String> dps_yhgz = new ArrayList<>();   // 所有衣号港仔店铺（全区域）
    private static final Map<String, String> dps_roomid = new HashMap<>();     // 店铺ID → 店铺名称
    private static final Map<String, List<String>> dps_boss = new HashMap<>(); // 店铺ID → 负责人列表

    // 正常营业数据
    private static final List<String> all_dps_roomid_normal = new ArrayList<>(); // 所有正常营业店铺
    private static final Map<String, Object> all_dps_normal = new HashMap<>();   // 完整营业数据
    private static final Logger log = LoggerFactory.getLogger(ShopManager.class);

    //======================== 系统控制区 ========================
    private static volatile boolean isDataLoaded = false;  // 数据加载状态
    private static final Object loadLock = new Object();   // 双重校验锁对象

    //======================== 公开接口 ========================
    /**
     * 获取云南一区店铺列表
     * (首次调用会自动加载数据)
     */
    public static List<String> getDpArea1() {
        checkAndLoadData();
        return Collections.unmodifiableList(dp_area1);
    }

    public static List<String> getDps_jtnrb() {
        return Collections.unmodifiableList(dps_jtnrb);
    }

    public static List<String> getDps_yhgz() {
        return Collections.unmodifiableList(dps_yhgz);
    }

    public static Map<String, String> getDps_roomid() {
        return Collections.unmodifiableMap(dps_roomid);
    }

    public static Map<String, List<String>> getDps_boss() {
        return Collections.unmodifiableMap(dps_boss);
    }

    public static Map<String, Object> getAll_dps_normal() {
        return Collections.unmodifiableMap(all_dps_normal);
    }

    /**
     * 检查是否为有效店铺群
     * @param roomId 需要检查的群ID
     */
    public static boolean isDp(String roomId) {
        checkAndLoadData();
        return dps_roomid.containsKey(roomId);
    }

    /**
     * 检查是否为有效店铺群
     * @param sender 需要检查的发送人的ID
     */
    public static boolean isHuiKuanRen(String sender) {
        checkAndLoadData();

        for (Map.Entry<String, List<String>> entry : dps_boss.entrySet()) {
            //System.out.println("dps_boss: " + entry.getValue());
            for (String s : entry.getValue()) {
                if (s.equals(sender)) {
                    return true;
                }
            }
        }
        return false;
    }

    //======================== 核心逻辑 ========================
    /**
     * 双重校验锁确保数据加载
     */
    private static void checkAndLoadData() {
        if (!isDataLoaded) {
            synchronized (loadLock) {
                if (!isDataLoaded) {
                    updateData();
                    isDataLoaded = true;
                }
            }
        }
    }

    /**
     * 全量更新数据（同步方法保证线程安全）
     */
    public static synchronized void updateData() {
        try {
            clearData();  // 清空旧数据
            JSONArray data = fetchData();  // 获取数据
            processData(data);             // 处理数据
            buildResults();                // 构建结果
        } catch (Exception e) {
            handleError(e);  // 异常处理
        }
    }

    //======================== 数据获取 ========================
    /**
     * 获取数据（本地优先，失败时使用网络请求）
     */
    private static JSONArray fetchData() {
        JSONArray localData = loadFromFile();

        // 优先使用有效的本地数据
        if (isLocalDataValid(localData)) {
//            log.info("ℹ️ 使用本地缓存数据");
            return localData;
        }

        // 本地数据无效时尝试网络请求
        log.warn("⚠️ 本地数据无效或为空，尝试网络请求");
        try {
            String jsonStr = requestFromAPI();
            JSONObject response = new JSONObject(jsonStr);

            if ("finished".equals(response.optString("status"))) {
                JSONArray networkData = response.getJSONObject("data").getJSONArray("result");
                saveToFile(networkData);  // 保存新鲜数据到本地
                return networkData;
            }
        } catch (Exception e) {
            log.error("⚠️ 网络请求失败: " + e.getMessage());
        }

        // 完全失败时返回空数据集
        return new JSONArray();
    }

    /**
     * 增强的本地数据有效性验证
     */
    private static boolean isLocalDataValid(JSONArray data) {
        // 基础检查：数据不为空且包含至少一个有效店铺
        if (data == null || data.isEmpty()) return false;

        // 深度检查：验证第一条数据是否包含必要字段
        try {
            JSONObject firstItem = data.getJSONObject(0);
            String firstKey = firstItem.keys().next();
            JSONObject shopInfo = firstItem.getJSONObject(firstKey);
            return shopInfo.has("dpmc") && shopInfo.has("dp_yingye_status");
        } catch (Exception e) {
            log.error("⚠️ 本地数据格式异常: " + e.getMessage());
            return false;
        }
    }

    /**
     * 从WPS API获取数据
     */
    private static String requestFromAPI() throws IOException {
        URL url = new URL("https://www.kdocs.cn/api/v3/ide/file/286603414736/script/V2-3Xo6tc4lReld11vH0fHfsX/sync_task");
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();

        // 配置请求参数
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setRequestProperty("AirScript-Token", "5cLXqnjzArRkT3RdcOhz4m");
        conn.setDoOutput(true);
        conn.setConnectTimeout(5000);  // 5秒连接超时
        conn.setReadTimeout(10000);    // 10秒读取超时

        // 发送请求体
        try (OutputStream os = conn.getOutputStream()) {
            String requestBody = "{\"Context\":{\"argv\":{\"rec_id\": \"success\"}}}";
            os.write(requestBody.getBytes(StandardCharsets.UTF_8));
        }

        // 检查响应状态
        if (conn.getResponseCode() != 200) {
            throw new IOException("HTTP错误码: " + conn.getResponseCode());
        }

        // 读取响应数据
        StringBuilder response = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(
            new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                response.append(line);
            }
        }
        return response.toString();
    }

    //======================== 数据处理 ========================
    /**
     * 处理原始数据
     */
    private static void processData(JSONArray data) {
        if (data == null || data.isEmpty()) {
            System.out.println("⚠️ 没有可处理的数据");
            return;
        }

        for (int i = 0; i < data.length(); i++) {
            JSONObject item = data.optJSONObject(i);
            if (item == null) continue;

            // 获取动态键名（店铺ID）
            String roomId = item.keys().next();
            if (roomId == null || "undefined".equals(roomId)) continue;

            JSONObject info = item.optJSONObject(roomId);
            if (info == null) continue;

            // 提取关键字段
            String status = info.optString("dp_yingye_status", "");
            String wechatId = info.optString("dd_wechat_id", "");
            String brand = info.optString("dp_pinpai", "");
            String shopName = info.optString("dpmc", "未知店铺");
            String payers = info.optString("dp_huikuan_ren", "");

            // 存储基础信息
            dps_roomid.put(roomId, shopName);
            processBossInfo(roomId, payers);

            // 处理区域分类
            processRegion(roomId, wechatId, brand);

            // 处理正常营业店铺
            if ("正常营业".equals(status) || "0态".equals(status)) {
                processNormalShop(roomId, wechatId, brand);
            }
        }
    }

    /**
     * 处理负责人信息
     */
    private static void processBossInfo(String roomId, String payers) {
        List<String> bossList = new ArrayList<>();
        if (!payers.isEmpty()) {
            String[] arr = payers.replace(" ", "").split(",");
            for (String boss : arr) {
                if (!boss.isEmpty()) {
                    bossList.add(boss);
                }
            }
        }
        dps_boss.put(roomId, Collections.unmodifiableList(bossList));
    }

    /**
     * 区域分类处理
     */
    private static void processRegion(String roomId, String wechatId, String brand) {
        /* 督导微信ID对应关系：
           wxid_fma1qud702r522 -> 云南一区B
           wxid_yqot05pawzo322 -> 云南一区 */
        if ("wxid_fma1qud702r522".equals(wechatId)) {
            dp_area1B.add(roomId);  // 添加到云南一区B
            classifyBrand(brand, roomId, dp_area1B_jtnrb, dp_area1B_yhgz);
        } else if ("wxid_yqot05pawzo322".equals(wechatId)) {
            dp_area1.add(roomId);    // 添加到云南一区
            classifyBrand(brand, roomId, dp_area1_jtnrb, dp_area1_yhgz);
        } else {
//            System.out.println("⚠️ 特殊店铺督导ID: " + wechatId);
        }
    }

    /**
     * 处理正常营业店铺
     */
    private static void processNormalShop(String roomId, String wechatId, String brand) {
        all_dps_roomid_normal.add(roomId);
        if ("wxid_fma1qud702r522".equals(wechatId)) {
            classifyBrand(brand, roomId, dp_area1B_jtnrb, dp_area1B_yhgz);
        } else if ("wxid_yqot05pawzo322".equals(wechatId)) {
            classifyBrand(brand, roomId, dp_area1_jtnrb, dp_area1_yhgz);
        }
    }

    /**
     * 品牌分类工具方法
     */
    private static void classifyBrand(String brand, String roomId,
                                      List<String> jtnrbList, List<String> yhgzList) {
        if ("街头男人帮".equals(brand)) {
            jtnrbList.add(roomId);
            if (!dps_jtnrb.contains(roomId)) {  // 避免重复
                dps_jtnrb.add(roomId);
            }
        } else if ("衣号港仔".equals(brand)) {
            yhgzList.add(roomId);
            if (!dps_yhgz.contains(roomId)) {   // 避免重复
                dps_yhgz.add(roomId);
            }
        }
    }

    //======================== 辅助方法 ========================
    /**
     * 清空所有数据
     */
    private static void clearData() {
        dp_area1.clear();
        dp_area1B.clear();
        dp_area1_jtnrb.clear();
        dp_area1_yhgz.clear();
        dp_area1B_jtnrb.clear();
        dp_area1B_yhgz.clear();
        dps_jtnrb.clear();
        dps_yhgz.clear();
        dps_roomid.clear();
        dps_boss.clear();
        all_dps_roomid_normal.clear();
        all_dps_normal.clear();
    }

    /**
     * 构建最终结果集
     */
    private static void buildResults() {
        all_dps_normal.put("all_shops", Collections.unmodifiableList(all_dps_roomid_normal));
        all_dps_normal.put("area1", Collections.unmodifiableList(dp_area1));
        all_dps_normal.put("area1B", Collections.unmodifiableList(dp_area1B));
        all_dps_normal.put("jtnrb_all", Collections.unmodifiableList(dps_jtnrb));
        all_dps_normal.put("yhgz_all", Collections.unmodifiableList(dps_yhgz));
    }

    /**
     * 异常处理
     */
    private static void handleError(Exception e) {
        log.error("‼️ 数据加载异常: " + e.getMessage());
        e.printStackTrace();
        clearData();  // 异常时清空数据
    }

    //======================== 文件操作 ========================
    /**
     * 保存数据到文件
     */
    private static void saveToFile(JSONArray data) {
        File file = new File(getFilePath());
        try {
            if (!file.getParentFile().exists()) {
                file.getParentFile().mkdirs();
            }
            try (FileWriter writer = new FileWriter(file)) {
                writer.write(data.toString(2));  // 缩进2格格式化
                log.info("✅ Shop数据已保存到: " + file.getAbsolutePath());
            }
        } catch (IOException e) {
            log.warn("⚠️ Shop文件保存失败: " + e.getMessage());
        }
    }

    /**
     * 增强版本地数据加载
     */
    private static JSONArray loadFromFile() {
        File file = new File(getFilePath());
        if (!file.exists()) {
            log.warn("ℹ️ 本地缓存文件不存在: " + file.getAbsolutePath());
            return null;
        }

        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            StringBuilder content = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                content.append(line);
            }
            return new JSONArray(content.toString());
        } catch (Exception e) {
            log.warn("⚠️ 本地缓存文件损坏: " + e.getMessage());
            return null;
        }
    }

    private static String getFilePath() {
        return new File(FILE_SAVE_PATH, "SupportFiles/rooms_info.json").getAbsolutePath();
    }

    //======================== 测试入口 ========================
//    public static void main(String[] args) {
//        // 首次访问触发数据加载
//        System.out.println("云南一区店铺数量: " + getDpArea1().size());
//        System.out.println("所有正常营业店铺: " + all_dps_roomid_normal);
//
//        // 示例：检查某个店铺是否存在
//        String testRoomId = "123456";
//        System.out.println(testRoomId + " 是否有效: " + isDp(testRoomId));
//    }
}
