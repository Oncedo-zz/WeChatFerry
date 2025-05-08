package com.wechat.ferry.service.impl;

import org.springframework.stereotype.Component;

@Component
public class TestOCR {
    public void executeOcr(String imagePath, String roomId, String roomname) {
        OcrServiceImpl ocrService = new OcrServiceImpl();

        // 设置截图文件路径
        ocrService.setImagePath(imagePath);

        try {
            // 执行OCR处理
            ocrService.ocr(roomId, roomname);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void main(String[] args) {
        TestOCR test = new TestOCR();

        for (int i = 17; i <= 17; i ++){
            // 设置参数
            String imagePath = "C:\\Users\\Administrator\\Desktop\\ocr_pic\\截图" + i + ".jpg";
            String roomId = "57158394575@chatroom";
            String roomname = "zks2007";

            // 执行OCR服务
//            test.executeOcr(imagePath, roomId, roomname);
        }

    }
}


