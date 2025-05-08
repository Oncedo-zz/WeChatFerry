package com.wechat.ferry.service.impl.BaiSheng;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.wechat.ferry.config.WeChatFerryProperties;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.CloseableHttpResponse;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.http.io.entity.StringEntity;
import org.apache.poi.hssf.usermodel.HSSFRow;
import org.apache.poi.hssf.usermodel.HSSFSheet;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.openqa.selenium.*;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.interactions.Actions;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.TimeoutException;

import io.github.bonigarcia.wdm.WebDriverManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * 业绩报表自动生成工具
 * 功能：
 * 1. 自动登录两个业务系统
 * 2. 抓取销售数据
 * 3. 生成Excel报表
 * 4. 同步数据到WPS云文档
 */

@Component
public class GetRealTimeYjServiceImpl {
    @Autowired
    private static WeChatFerryProperties weChatFerryProperties;
    // 常量定义
    private static final String DESKTOP_PATH = "C:\\Users\\Administrator\\Desktop\\Performance\\";
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ISO_DATE;
    private static final int TIMEOUT_SECONDS = 10;

    /**
     * 主入口方法
     */
    public static void main(String[] args) {
        try {
            // 初始化浏览器驱动
            WebDriverManager.chromedriver().setup();

            // 生成当日文件名
            String today = LocalDate.now().format(DATE_FORMATTER);
            String filePath = DESKTOP_PATH + "18点微信发送" + today + ".xls";

            // 检查文件是否存在
            if (checkFileExists(filePath)) {
                return;
            }

            // 抓取数据并生成报表
            List<List<String>> dataAll = fetchDataFromSystems();
            writeToExcel(dataAll, filePath);

            // 同步到WPS云文档
            processWpsData();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * 检查文件是否存在且在5分钟内修改过
     */
    private static boolean checkFileExists(String filePath) throws Exception {
        Path path = Paths.get(filePath);
        if (!Files.exists(path)) return false;

        // 获取文件修改时间
        LocalDateTime modTime = Files.getLastModifiedTime(path).toInstant()
            .atZone(ZoneId.systemDefault()).toLocalDateTime();
        LocalDateTime halfHourAgo = LocalDateTime.now().minusMinutes(5);

        if (modTime.isAfter(halfHourAgo)) {
            System.out.println("检测到近期修改过的文件");
            boolean input = InputUtils.getInputWithTimeout(
                "输入1继续获取数据（5秒超时）：", 5);
            if (!input) {
                System.out.println("操作已取消");
                return true;
            }
        }
        return false;
    }

    /**
     * 从两个业务系统抓取数据
     */
    private static List<List<String>> fetchDataFromSystems() {
        // 配置浏览器参数
        ChromeOptions options = new ChromeOptions();
        options.addArguments("--no-sandbox");             // 禁用沙盒模式
        options.addArguments("--disable-dev-shm-usage");  // 解决共享内存问题
        options.addArguments("--remote-allow-origins=*"); // 允许所有远程连接
        options.addArguments("--disable-blink-features=AutomationControlled"); // 禁用自动化检测
        options.setExperimentalOption("excludeSwitches",
            Collections.singletonList("enable-automation")); // 隐藏自动化标记

//        System.setProperty("webdriver.chrome.driver", weChatFerryProperties.getFileSavePath() + "\\chromedriver-win64\\chromedriver.exe");
//        System.setProperty("webdriver.chrome.driver", "C:\\Users\\Administrator\\Desktop\\WeChatBot\\Java\\app\\Assets\\chromedriver-win64\\chromedriver.exe");
        WebDriver driver = new ChromeDriver(options);
        List<List<String>> dataAll = new ArrayList<>();

        try {
            // 第一个系统：男人帮
            loginSystem(driver, "http://47.97.230.6/ipos/web/manage/privilege.php",
                "YNDD001", "JTNRByn");
            dataAll.addAll(fetchManbangData(driver));

            // 第二个系统：港仔
            loginSystem(driver, "http://47.97.230.6/ipos_2022/web/manage/index.php",
                "Y1FGS", "JTNRByn");
            dataAll.addAll(fetchGangzaiData(driver));

        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            driver.quit(); // 确保浏览器关闭
        }
        return dataAll;
    }

    /**
     * 系统登录通用方法
     */
    private static void loginSystem(WebDriver driver, String url,
                                    String user, String pass) {
        driver.get(url);

        // 显式等待元素加载
        WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(TIMEOUT_SECONDS));

        // 输入机构代码
        WebElement orgField = wait.until(ExpectedConditions.visibilityOfElementLocated(By.id("orgname")));
        orgField.sendKeys("000");

        // 输入用户名
        WebElement userField = driver.findElement(By.id("username"));
        userField.sendKeys(user);

        // 输入密码
        WebElement passField = driver.findElement(By.id("password"));
        passField.sendKeys(pass);

        // 点击登录
        WebElement submitBtn = driver.findElement(By.id("sub"));
        submitBtn.click();

        System.out.println("成功登录系统: " + url);
    }

    /**
     * 抓取男人帮系统数据
     */
    private static List<List<String>> fetchManbangData(WebDriver driver) {
        List<List<String>> data = new ArrayList<>();

        try {
            // 切换iframe
            driver.switchTo().defaultContent();
            driver.switchTo().frame("frame_details_0");

            // 点击业绩统计链接
            WebElement link = new WebDriverWait(driver, Duration.ofSeconds(TIMEOUT_SECONDS))
                .until(ExpectedConditions.elementToBeClickable(
                    By.xpath("/html/body/div[2]/div[2]/div[2]/div[2]/table/tbody/tr[2]/td[3]/a/span")));
            link.click();

            // 切换回主文档
            driver.switchTo().defaultContent();
            driver.switchTo().frame("frame_details_1");

            // 设置每页显示条目
            WebElement pageSize = driver.findElement(By.id("pageSize"));
            new Actions(driver)
                .sendKeys(pageSize, "0")
                .sendKeys(Keys.ENTER)
                .perform();

            Thread.sleep(1000); // 等待数据加载

            // 提取表格数据
            List<WebElement> rows = driver.findElements(By.cssSelector("#tr-"));
            for (WebElement row : rows) {
                List<String> rowData = new ArrayList<>();
                rowData.add(row.findElement(By.className("pm")).getText());       // 排名
                rowData.add(row.findElement(By.className("khmc")).getText());     // 店铺名称
                rowData.add(row.findElement(By.className("yyzt")).getText());     // 营业状态
                rowData.add(row.findElement(By.className("kysj")).getText());     // 开业时间
                rowData.add(row.findElement(By.className("xssl")).getText());     // 销售数量
                rowData.add(row.findElement(By.className("sjje")).getText());     // 实际金额
                rowData.add(row.findElement(By.className("xsje_mn")).getText());  // 当月业绩
                rowData.add("街头男人帮");                                        // 品牌
                data.add(rowData);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return data;
    }

    /**
     * 抓取港仔系统数据
     */
    private static List<List<String>> fetchGangzaiData(WebDriver driver) {
        List<List<String>> data = new ArrayList<>();

        try {
            // 切换iframe并点击报表菜单
            driver.switchTo().frame("frame_header");
            WebElement reportBtn = new WebDriverWait(driver, Duration.ofSeconds(TIMEOUT_SECONDS))
                .until(ExpectedConditions.elementToBeClickable(
                    By.xpath("//*[@id=\"nav_bar\"]/li[7]/a")));
            reportBtn.click();

            // 切换菜单iframe
            driver.switchTo().defaultContent();
            driver.switchTo().frame("frame_menu");

            // 点击具体菜单项
            WebElement menuItem = new WebDriverWait(driver, Duration.ofSeconds(TIMEOUT_SECONDS))
                .until(ExpectedConditions.elementToBeClickable(
                    By.xpath("/html/body/div/div[1]/div[2]/ul/li[2]/span[1]/a")));
            menuItem.click();

            // 切换数据iframe
            driver.switchTo().defaultContent();
            driver.switchTo().frame("frame_details_1");

            // 设置显示条目数
            WebElement pageSize = driver.findElement(By.id("pageSize"));
            new Actions(driver)
                .sendKeys(pageSize, "0")
                .sendKeys(Keys.ENTER)
                .perform();

            Thread.sleep(3000); // 延长等待时间

            // 提取表格数据
            List<WebElement> rows = driver.findElements(By.cssSelector("#tr-"));
            for (WebElement row : rows) {
                List<String> rowData = new ArrayList<>();
                rowData.add(row.findElement(By.className("pm")).getText());
                rowData.add(row.findElement(By.className("khmc")).getText());
                rowData.add(row.findElement(By.className("yyzt")).getText());
                rowData.add(row.findElement(By.className("kysj")).getText());
                rowData.add(row.findElement(By.className("xssl")).getText());
                rowData.add(row.findElement(By.className("sjje")).getText());
                rowData.add(row.findElement(By.className("xsje_mn")).getText());
                rowData.add("衣号港仔");
                data.add(rowData);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return data;
    }

    /**
     * 写入Excel文件
     */
    private static void writeToExcel(List<List<String>> data, String path) {
        try (HSSFWorkbook workbook = new HSSFWorkbook()) {
            HSSFSheet sheet = workbook.createSheet("销售业绩统计");

            // 设置列宽（单位：1/256字符）
            sheet.setColumnWidth(0, 140 * 256);  // 排名
            sheet.setColumnWidth(1, 240 * 256);  // 店铺名称
            sheet.setColumnWidth(2, 180 * 256);  // 营业状态
            sheet.setColumnWidth(3, 160 * 256);  // 开业时间
            sheet.setColumnWidth(4, 260 * 256);  // 销售数量
            sheet.setColumnWidth(5, 240 * 256);  // 实际金额
            sheet.setColumnWidth(6, 220 * 256);  // 当月业绩
            sheet.setColumnWidth(7, 200 * 256);  // 品牌

            // 创建标题行
            String[] headers = {"业绩排名", "店铺名称", "营业状态", "今日开业时间",
                "销售数量", "实际金额", "当月业绩", "品牌"};
            HSSFRow headerRow = sheet.createRow(0);
            for (int i = 0; i < headers.length; i++) {
                headerRow.createCell(i).setCellValue(headers[i]);
            }

            // 填充数据行
            for (int rowNum = 0; rowNum < data.size(); rowNum++) {
                HSSFRow row = sheet.createRow(rowNum + 1);
                List<String> rowData = data.get(rowNum);
                for (int colNum = 0; colNum < rowData.size(); colNum++) {
                    row.createCell(colNum).setCellValue(rowData.get(colNum));
                }
            }

            // 确保目录存在
            new File(DESKTOP_PATH).mkdirs();

            // 写入文件
            try (FileOutputStream fos = new FileOutputStream(path)) {
                workbook.write(fos);
                System.out.println("Excel文件生成成功: " + path);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * 与WPS云文档同步数据
     */
    private static void processWpsData() {
        try (CloseableHttpClient client = HttpClients.createDefault()) {
            HttpPost post = new HttpPost("https://www.kdocs.cn/api/v3/ide/file/286603414736/script/V2-7ooJlLdT4BGsACgKfNjbGW/sync_task");

            // 设置请求头
            post.setHeader("Content-Type", "application/json");
            post.setHeader("AirScript-Token", "5cLXqnjzArRkT3RdcOhz4m");

            // 构建请求体
            String jsonBody = "{\"Context\":{\"argv\":{}}}";
            post.setEntity(new StringEntity(jsonBody, ContentType.parse("UTF-8")));

            // 发送请求并处理响应
            try (CloseableHttpResponse response = client.execute(post)) {
                String jsonStr = EntityUtils.toString(response.getEntity(), "UTF-8");
                JSONObject resultJson = JSONObject.parseObject(jsonStr);
                JSONArray resultData = resultJson.getJSONObject("data").getJSONArray("result");

                // 解析数据
                JSONArray completionRates = resultData.getJSONArray(0);
                JSONArray zeroSalesShops = resultData.getJSONArray(2);
                String totalTarget = resultData.getString(4);
                String achieved = resultData.getString(3);

                // 构建统计报告
                StringBuilder report = new StringBuilder();
                report.append("=== 当日销售统计 ===\n")
                    .append("统计时间: ").append(LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)).append("\n")
                    .append("总目标: ").append(totalTarget).append("\n")
                    .append("已完成: ").append(achieved).append("\n")
                    .append("完成率: ").append(String.format("%.2f%%",
                        Double.parseDouble(achieved) / Double.parseDouble(totalTarget) * 100));

                System.out.println(report.toString());
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}

/**
 * 输入工具类
 */
class InputUtils {
    /**
     * 带超时的控制台输入
     * @param prompt 提示信息
     * @param timeoutSeconds 超时时间（秒）
     * @return 用户输入是否为"1"
     */
    public static boolean getInputWithTimeout(String prompt, int timeoutSeconds) {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            System.out.print(prompt);
            Future<String> future = executor.submit(() ->
                new BufferedReader(new InputStreamReader(System.in)).readLine()
            );
            return "1".equals(future.get(timeoutSeconds, TimeUnit.SECONDS));
        } catch (TimeoutException e) {
            System.out.println("\n输入超时，使用默认值");
            return false;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        } finally {
            executor.shutdownNow();
        }
    }
}
