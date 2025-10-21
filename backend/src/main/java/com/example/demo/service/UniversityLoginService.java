package com.example.demo.service;

import io.github.bonigarcia.wdm.WebDriverManager;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

/**
 * Seleniumを使用して大学サイトへのログイン処理を行い、セッションCookieを取得する責務を持つServiceクラス。
 */
@Service
public class UniversityLoginService {

    /**
     * 指定されたIDとパスワードでログインし、セッションCookieを返します。
     * @param universityId 大学のID
     * @param password 大学のパスワード
     * @return ログイン後のセッションCookieのMap
     * @throws InterruptedException ログイン待機中に割り込みが発生した場合
     */
    public Map<String, String> loginAndGetCookies(String universityId, String password) throws InterruptedException {
        // WebDriverManagerでChromeDriverのセットアップを自動化
        WebDriverManager.chromedriver().setup();

        // Renderなどの本番環境で実行するために、ヘッドレスモードを設定
        ChromeOptions options = new ChromeOptions();
        options.addArguments("--headless");
        options.addArguments("--disable-gpu");
        options.addArguments("--window-size=1920,1080");
        options.addArguments("--no-sandbox");
        options.addArguments("--disable-dev-shm-usage");

        WebDriver driver = new ChromeDriver(options);
        // ログイン完了までの待機時間を長く設定
        WebDriverWait wait = new WebDriverWait(driver, Duration.ofMinutes(3));

        try {
            // ここに、SeleniumでIDとパスワードを自動入力してログインボタンをクリックする処理を記述します。
            // 以下は手動ログインを待つ場合の例ですが、最終的には自動化を目指します。
            
            driver.get("https://ct.ritsumei.ac.jp/ct/login");
            
            // 例: IDとパスワードを入力し、ボタンをクリック
            // driver.findElement(By.id("username")).sendKeys(universityId);
            // driver.findElement(By.id("password")).sendKeys(password);
            // driver.findElement(By.id("login-button")).click();
            
            System.out.println("ログイン処理を開始...（現在は手動ログインを待機する設定です）");

            // ログインが完了し、ホーム画面にリダイレクトされるまで待機
            wait.until(ExpectedConditions.urlContains("home"));
            
            System.out.println("ログイン成功を検知しました！");

            // Cookieを取得
            Map<String, String> cookies = new HashMap<>();
            driver.manage().getCookies().forEach(c -> cookies.put(c.getName(), c.getValue()));
            
            return cookies;

        } finally {
            if (driver != null) {
                driver.quit(); // 必ずブラウザを閉じる
            }
        }
    }
}