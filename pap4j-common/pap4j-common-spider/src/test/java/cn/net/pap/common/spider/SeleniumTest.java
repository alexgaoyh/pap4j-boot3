package cn.net.pap.common.spider;

import org.junit.jupiter.api.Test;
import org.openqa.selenium.*;
import org.openqa.selenium.chrome.ChromeDriver;

public class SeleniumTest {

    // @Test
    @org.junit.jupiter.api.Disabled("Requires local environment/dataset")
    public void checkPathTestWithCookie() {
        System.setProperty("webdriver.chrome.driver", "D:\\chromedriver-win64\\chromedriver.exe");

        WebDriver driver = new ChromeDriver();

        // 打开一次网页以便设置 Cookie（重要步骤）
        driver.get("https://www.baidu.com");

        // 构造你需要设置的 Cookie
        Cookie myCookie = new Cookie.Builder("pap.net.cn", "pap.net.cn")
                .domain(".baidu.com") // 注意是主域名
                .path("/")            // 通常设为根路径
                .isHttpOnly(true)     // 可选
                .isSecure(true)       // 可选
                .build();

        // 添加 Cookie 到浏览器
        driver.manage().addCookie(myCookie);

        // 重新加载页面或访问你需要验证 Cookie 的 URL
        driver.get("https://www.baidu.com");

        System.out.println("Cookie added and page loaded!");

        driver.quit();
    }

    // @Test
    @org.junit.jupiter.api.Disabled("Requires local environment/dataset")
    public void browserTest() {
        // 设置 ChromeDriver 路径
        System.setProperty("webdriver.chrome.driver", "D:\\chromedriver-win64\\chromedriver.exe");

        WebDriver driver = new ChromeDriver();

        driver.get("http://127.0.0.1:6060/business/archivers");

        try {
            Thread.sleep(3000);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }

        // 构造你需要设置的 Cookie
        driver.manage().deleteCookieNamed("JSESSIONID");
        Cookie myCookie = new Cookie.Builder("JSESSIONID", "d8f6bff2-8642-4ff5-a584-c620452f6fb0")
                .domain(".127.0.0.1:6060").path("/").isHttpOnly(true).isSecure(true).build();

        // 添加 Cookie 到浏览器
        driver.manage().addCookie(myCookie);

        // 重新加载页面或访问你需要验证 Cookie 的 URL
        driver.get("http://127.0.0.1:6060/business/archivers");

        System.out.println("Cookie added and page loaded!");

        try {
            Thread.sleep(3000);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }

        driver.quit();
    }


}
