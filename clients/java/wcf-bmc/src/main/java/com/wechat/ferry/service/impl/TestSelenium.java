package com.wechat.ferry.service.impl;

import io.github.bonigarcia.wdm.WebDriverManager;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;

import java.util.Collections;

public class TestSelenium {
    public static void main(String[] args) {
        // 设置 WebDriver 的系统属性
        // 注意：这里需要根据你的浏览器驱动的实际路径来设置
        System.setProperty("webdriver.chrome.driver", "C:\\Plugs\\chrome-win64\\chromedriver.exe");

        ChromeOptions options = new ChromeOptions();
        options.setBinary("C:\\Plugs\\chrome-win64\\chrome-win64\\chrome.exe"); // 完整路径
        options.addArguments("--no-sandbox");          // Linux必加
        options.addArguments("--disable-dev-shm-usage");
        options.addArguments("--remote-allow-origins=*"); // Selenium 4需要此参数
        System.setProperty("webdriver.chrome.logfile", "C:\\logs\\chromedriver.log");

        // 初始化浏览器驱动
//        WebDriverManager.chromedriver().setup();

//        ChromeOptions options = new ChromeOptions();
//        options.addArguments("--no-sandbox");             // 禁用沙盒模式
//        options.addArguments("--disable-dev-shm-usage");  // 解决共享内存问题
//        options.addArguments("--remote-allow-origins=*"); // 允许所有远程连接
//        options.addArguments("--disable-blink-features=AutomationControlled"); // 禁用自动化检测
//        options.setExperimentalOption("excludeSwitches",
//            Collections.singletonList("enable-automation")); // 隐藏自动化标记

        // 创建 WebDriver 实例
        WebDriver driver = new ChromeDriver(options);

        try {
            driver.get("https://www.baidu.com");
            System.out.println("标题：" + driver.getTitle());
            driver.quit();
        } catch (Exception e) {
            System.out.println("详细错误信息:");
            e.printStackTrace();

//            // 检查关键路径
//            String chromePath = System.getenv("LOCALAPPDATA") + "\\Google\\Chrome\\Application";
//            System.out.println("Chrome安装路径验证: " + new File(chromePath).exists());
//            System.out.println("Chrome.exe存在性验证: " + new File(chromePath + "\\chrome.exe").exists());
        }
        // 关闭浏览器
        driver.quit();
    }
}
