package com.wechat.ferry.service.impl;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.alibaba.fastjson2.TypeReference;
import com.wechat.ferry.entity.vo.response.WxPpWcfContactsResp;
import com.wechat.ferry.service.WeChatDllService;
import okhttp3.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Component
public class OcrServiceImpl {
    @Autowired
    WeChatDllService weChatDllService;
    private static String imagePath;

    static String serverUrl = "http://127.0.0.1:8501/ocr";
    ArrayList ocrResult = new ArrayList();

    public ArrayList getOcrResult() {
        return ocrResult;
    }

    public void setOcrResult(ArrayList ocrResult) {
        this.ocrResult = ocrResult;
    }

    public String getImagePath() {
        return imagePath;
    }

    public void setImagePath(String imagePath) {
        this.imagePath = imagePath;
    }

    public void ocr(String roomid, String roomname) {

        try {
            String response = uploadImage(imagePath, serverUrl);
//            System.out.println("OCR 结果：\n" + response);

            // 修改后的解析逻辑
            ArrayList<Map<String, Object>> structuredResult = parseToArrayList(response);
            List<String> textResults = extractTextFromResults(structuredResult); // 新增方法

            // 打印解析结果
            //printOcrResults(structuredResult); // 修改后的打印方法

            // 分析ocr的结果
            System.out.println("分析ocr的结果");
            OcrResultServiceImpl ocrResultService = new OcrResultServiceImpl();
            //ocrResultService.processOcrResults(textResults); // 直接传递文本列表

            // 回款数据 上传至 WPS
            ocrResultService.processPaymentScreenshot(roomid, roomname, textResults);

            System.out.println("OCR 已完成");

        } catch (IOException | OcrParseException e) {
            System.err.println("请求失败：" + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * 执行文件上传（等效 curl -F）
     * @param imagePath 图片文件路径
     * @param url 服务地址
     * @return 响应内容
     */
    public static String uploadImage(String imagePath, String url) throws IOException {
        OkHttpClient client = new OkHttpClient();

        // 1. 构建 Multipart 请求体
        RequestBody requestBody = new MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("image",
                new File(imagePath).getName(),
                RequestBody.create(
                    new File(imagePath),
                    MediaType.parse("application/octet-stream")
                ))
            .build();

        // 2. 构建请求对象
        Request request = new Request.Builder()
            .url(url)
            .post(requestBody)
            .build();

        // 3. 执行请求并处理响应
        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("请求失败，状态码：" + response.code());
            }
            return response.body().string();
        }
    }

    /**
     * 通用解析方法（支持多种JSON格式）
     * @param jsonResponse OCR服务返回的原始JSON字符串
     * @return 结构化结果列表
     */
    public static ArrayList<Map<String, Object>> parseToArrayList(String jsonResponse) throws OcrParseException {
        Object parsed = JSON.parse(jsonResponse);

        // 场景1：直接返回数组
        if (parsed instanceof JSONArray) {
            return parseJsonArray((JSONArray) parsed);
        }

        // 场景2：返回对象包含results字段
        if (parsed instanceof JSONObject) {
            JSONObject jsonObject = (JSONObject) parsed;

            // 尝试常见字段名
            String[] possibleArrayKeys = {"results", "data", "items", "list"};
            for (String key : possibleArrayKeys) {
                if (jsonObject.containsKey(key)) {
                    Object value = jsonObject.get(key);
                    if (value instanceof JSONArray) {
                        return parseJsonArray((JSONArray) value);
                    }
                }
            }
        }

        throw new OcrParseException("无法识别的JSON结构");
    }

    private static ArrayList<Map<String, Object>> parseJsonArray(JSONArray jsonArray) {
        // 获取泛型类型
        Type type = new TypeReference<ArrayList<Map<String, Object>>>() {}.getType();
        // 使用 parseObject 直接解析
        return JSON.parseObject(jsonArray.toString(), type);
    }

    // 新增方法：从结构化结果中提取文本
    private static List<String> extractTextFromResults(ArrayList<Map<String, Object>> results) {
        List<String> textList = new ArrayList<>();
        for (Map<String, Object> item : results) {
            if (item.containsKey("text")) {
                textList.add(item.get("text").toString());
            }
        }
        return textList;
    }

    // 修改打印方法
    private static void printOcrResults(ArrayList<Map<String, Object>> results) {
        System.out.println("===== OCR解析结果 =====");
        for (Map<String, Object> item : results) {
            item.forEach((key, value) -> {
                System.out.printf("%-15s : %s\n", key, value);
            });
            System.out.println("----------------------");
        }
    }

    public static class OcrParseException extends Exception {
        public OcrParseException(String message) {
            super(message);
        }
    }
}
