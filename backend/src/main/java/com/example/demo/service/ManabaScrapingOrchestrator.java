package com.example.demo.service;

import com.example.demo.dto.*; 
import io.github.bonigarcia.wdm.WebDriverManager;
import org.jsoup.Connection; // 追加
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.openqa.selenium.*;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.slf4j.Logger; 
import org.slf4j.LoggerFactory; 
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;

/**
 * Selenium/Jsoupによるログイン、スクレイピング、データ整形、ビジネスロジックを統括する司令塔Service。
 * 元のManabaScrapingService.javaのロジックをSpringコンポーネントとして再実装したもの。
 * LoginProgressListenerを通じて進捗を通知する。
 */
@Service
public class ManabaScrapingOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(ManabaScrapingOrchestrator.class);

    // --- 定数定義 ---
    private static final String LOGIN_URL = "https://ct.ritsumei.ac.jp/ct/login";
    private static final String HOME_COURSE_URL = "https://ct.ritsumei.ac.jp/ct/home_course";
    private static final String USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120 Safari/537.36";
    private static final int REQUEST_TIMEOUT_MILLIS = 30_000;
    private static final ZoneId JAPAN_ZONE = ZoneId.of("Asia/Tokyo");
    private static final DateTimeFormatter ISO_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");

    private record PeriodTime(String start, String end) {}

    private static final Map<String, PeriodTime> PERIOD_TIME_TABLE = Map.ofEntries(
            Map.entry("1", new PeriodTime("09:00", "10:35")),
            Map.entry("2", new PeriodTime("10:45", "12:20")),
            Map.entry("3", new PeriodTime("13:10", "14:45")),
            Map.entry("4", new PeriodTime("14:55", "16:30")),
            Map.entry("5", new PeriodTime("16:40", "18:15")),
            Map.entry("6", new PeriodTime("18:25", "20:00")),
            Map.entry("7", new PeriodTime("20:10", "21:45"))
    );

    private static final Map<String, DayOfWeek> DAY_OF_WEEK_MAP = Map.ofEntries(
            Map.entry("月", DayOfWeek.MONDAY),
            Map.entry("火", DayOfWeek.TUESDAY),
            Map.entry("水", DayOfWeek.WEDNESDAY),
            Map.entry("木", DayOfWeek.THURSDAY),
            Map.entry("金", DayOfWeek.FRIDAY),
            Map.entry("土", DayOfWeek.SATURDAY),
            Map.entry("日", DayOfWeek.SUNDAY)
    );

    private static final DateTimeFormatter[] DEADLINE_PATTERNS = new DateTimeFormatter[]{
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm", Locale.JAPANESE),
            DateTimeFormatter.ofPattern("yyyy/MM/dd(EEE) HH:mm", Locale.JAPANESE),
            DateTimeFormatter.ofPattern("yyyy/MM/dd(EEE) H:mm", Locale.JAPANESE),
            DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm", Locale.JAPANESE),
            DateTimeFormatter.ofPattern("yyyy/MM/dd H:mm", Locale.JAPANESE)
    };

    private final ScrapingService scrapingService;

    public ManabaScrapingOrchestrator(ScrapingService scrapingService) {
        this.scrapingService = scrapingService;
    }

    // 内部的な結果とCookieを保持するレコード
    public record InternalSyncOutcome(SyncResult syncResultDto, Map<String, String> cookies) {}

    /**
     * 同期処理のメインエントリーポイント。LoginProgressListenerを通じて進捗を通知する。
     */
    public InternalSyncOutcome sync(String username, String password, Map<String, String> existingCookies, LoginProgressListener listener) throws IOException {
        listener.onStatusUpdate("AUTH_START", "認証処理を開始します...");
        
        // 1. Cookie認証 (Jsoup) の試行
        if (existingCookies != null && !existingCookies.isEmpty()) {
            try {
                listener.onStatusUpdate("COOKIE_AUTH", "Cookie認証を試行中...");
                return scrapeWithExistingCookies(username, existingCookies, listener);
            } catch (IOException e) {
                log.warn("Cookie認証に失敗しました: {}", e.getMessage());
                listener.onStatusUpdate("COOKIE_FAIL", "Cookie認証失敗。パスワード認証に移行します。");
                // 失敗したら下へ進む (Seleniumへフォールバック)
            }
        }

        // 2. パスワード認証 (Selenium) の実行
        if (username == null || username.isBlank() || password == null || password.isBlank()) {
            throw new IllegalStateException("有効なCookieがなく、パスワードも指定されていません。");
        }

        listener.onStatusUpdate("PASSWORD_AUTH", "パスワード認証を開始します...");
        return loginAndScrape(username, password, listener);
    }

    /**
     * セッション更新のみを行うメソッド (AuthServiceで使う可能性があるため残す)
     */
    public Map<String, String> refreshSessionOnly(String username, String password, Map<String, String> existingCookies, LoginProgressListener listener) throws IOException {
        listener.onStatusUpdate("AUTH_START", "セッション更新を開始します...");
        if (existingCookies != null && !existingCookies.isEmpty()) {
            try {
                listener.onStatusUpdate("COOKIE_AUTH", "保存済みCookieでアクセスを確認中...");
                return refreshCookiesWithExisting(username, existingCookies, listener);
            } catch (IOException e) {
                log.warn("Cookieによるセッション確認に失敗しました: {}", e.getMessage());
                listener.onStatusUpdate("COOKIE_FAIL", "保存済みCookieが無効です。セッション更新をスキップします。");
                return Collections.emptyMap();
            }
        }
        listener.onStatusUpdate("COOKIE_FAIL", "セッションCookieが存在しないため更新をスキップします。");
        return Collections.emptyMap();
    }

    private InternalSyncOutcome scrapeWithExistingCookies(String username, Map<String, String> cookies, LoginProgressListener listener) throws IOException {
        Map<String, String> refreshedCookies = refreshCookiesWithExisting(username, cookies, listener);
        return buildInternalSyncOutcome(username, refreshedCookies, listener);
    }

    private Map<String, String> refreshCookiesWithExisting(String username, Map<String, String> cookies, LoginProgressListener listener) throws IOException {
        listener.onStatusUpdate("FETCH_HOME", "ホーム画面を取得中(Jsoup)...");

        // ★最適化: .execute() を使ってレスポンスヘッダ(Set-Cookie)も取得可能にする
        Connection.Response response = Jsoup.connect(HOME_COURSE_URL)
                .cookies(cookies)
                .userAgent(USER_AGENT)
                .timeout(REQUEST_TIMEOUT_MILLIS)
                .followRedirects(true)
                .execute();
        
        Document homeDoc = response.parse();

        if (isLoginPage(homeDoc)) {
            throw new IOException("Cookieの有効期限が切れています (ログインページを検知)。");
        }
        listener.onStatusUpdate("FETCH_HOME_SUCCESS", "ホーム画面の取得成功。");

        // レスポンスでCookieが更新されていた場合マージする
        Map<String, String> updatedCookies = new HashMap<>(cookies);
        if (response.cookies() != null) {
            updatedCookies.putAll(response.cookies());
        }

        return updatedCookies;
    }

    private InternalSyncOutcome loginAndScrape(String username, String password, LoginProgressListener listener) throws IOException {
        Map<String, String> freshCookies = loginAndFetchCookies(username, password, listener);
        return buildInternalSyncOutcome(username, freshCookies, listener);
    }

    private Map<String, String> loginAndFetchCookies(String username, String password, LoginProgressListener listener) throws IOException {
        WebDriverManager.chromedriver().setup();
        ChromeOptions options = new ChromeOptions();
        
        // ★最適化: 高速化・軽量化設定
        options.setBinary("/opt/google/chrome/chrome");
        options.addArguments("--headless=new");
        options.addArguments("--disable-gpu");
        options.addArguments("--no-sandbox");
        options.addArguments("--disable-dev-shm-usage");
        options.addArguments("--blink-settings=imagesEnabled=false"); // 画像オフ
        options.setPageLoadStrategy(PageLoadStrategy.EAGER); // 読み込み完了を待たない

        WebDriver driver = null;
        Map<String, String> freshCookies = Collections.emptyMap();

        try {
            log.info("ChromeDriverを初期化します...");
            driver = new ChromeDriver(options);
            
            performLogin(driver, username, password, listener);
            
            listener.onStatusUpdate("FETCH_COOKIE_PAGE", "Cookie取得のためホーム画面へ遷移中...");
            
            // 現在のURLがホームでなければ移動 (performLoginで遷移しているはずだが念のため)
            if (!driver.getCurrentUrl().contains("/ct/home")) {
                driver.get(HOME_COURSE_URL);
            }

            freshCookies = extractCookies(driver);
            listener.onStatusUpdate("FETCH_COOKIE_SUCCESS", "新しいCookieを取得しました。");

        } catch (Exception e) {
            log.error("Seleniumによるログイン処理中にエラーが発生しました。", e);
            throw new IOException("ログイン処理に失敗しました: " + e.getMessage(), e);
        } finally {
            if (driver != null) {
                log.info("WebDriverを終了します...");
                driver.quit();
            }
        }

        if (freshCookies.isEmpty()) {
            throw new IOException("ログイン後のCookie取得に失敗しました (Cookieが空です)。");
        }
        return freshCookies;
    }

    private InternalSyncOutcome buildInternalSyncOutcome(String username, Map<String, String> cookies, LoginProgressListener listener) throws IOException {
        listener.onStatusUpdate("SCRAPE_START", "データのスクレイピングを開始します...");
        
        // ScrapingService (Jsoup) を呼び出し
        var rawCourses = scrapingService.parseTimetableToList(cookies);
        var rawAssignments = scrapingService.getAllAssignments(cookies);
        
        listener.onStatusUpdate("SCRAPE_COMPLETE", "データのスクレイピングが完了しました。");

        listener.onStatusUpdate("DATA_PROCESSING", "取得データを整形中...");
        List<CourseEntry> timetable = convertCourses(rawCourses);
        List<AssignmentEntry> assignments = convertAssignments(rawAssignments);
        NextClassCard nextClass = calculateNextClass(timetable);
        String syncedAt = LocalDateTime.now(JAPAN_ZONE).format(ISO_FORMATTER);
        
        listener.onStatusUpdate("DATA_PROCESSING_COMPLETE", "データ整形完了。");

        SyncResult syncResultDto = new SyncResult(null, username, syncedAt, timetable, assignments, nextClass);
        return new InternalSyncOutcome(syncResultDto, cookies);
    }

    // --- Seleniumでのログイン操作 ---
    private void performLogin(WebDriver driver, String username, String password, LoginProgressListener listener) throws IOException {
        // 高速化のためタイムアウトを60秒に設定
        WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(60));

        try {
            listener.onStatusUpdate("ACCESS_LOGIN_PAGE", "ログインページにアクセス中...");
            driver.get(LOGIN_URL);

            listener.onStatusUpdate("INPUT_USERNAME", "ユーザー名を入力中...");
            wait.until(ExpectedConditions.visibilityOfElementLocated(By.id("i0116"))).sendKeys(username);
            
            listener.onStatusUpdate("CLICK_NEXT_1", "「次へ」をクリック中...");
            wait.until(ExpectedConditions.elementToBeClickable(By.id("idSIButton9"))).click();

            // ユーザー名エラーチェック
            if (isElementDisplayed(driver, By.id("usernameError"))) {
                throw new IOException("ユーザー名が間違っています。");
            }

            listener.onStatusUpdate("INPUT_PASSWORD", "パスワードを入力中...");
            wait.until(ExpectedConditions.visibilityOfElementLocated(By.id("i0118"))).sendKeys(password);
            
            listener.onStatusUpdate("CLICK_SIGNIN", "「サインイン」をクリック中...");
            wait.until(ExpectedConditions.elementToBeClickable(By.id("idSIButton9"))).click();
            listener.onStatusUpdate("PASSWORD_SUBMITTED", "パスワードを送信しました。");

            // パスワードエラーチェック
            if (isElementDisplayed(driver, By.id("passwordError"))) {
                throw new IOException("パスワードが間違っています。");
            }

            detectMfaPrompt(driver, listener);
            handleStaySignedInPrompt(driver, listener, wait);

            listener.onStatusUpdate("WAITING_HOME", "ホーム画面への遷移を待機中...");
            wait.until(ExpectedConditions.urlContains("/ct/home"));
            listener.onStatusUpdate("LOGIN_SUCCESS", "ログイン成功を確認しました。");

        } catch (TimeoutException e) {
            throw new IOException("ログイン操作がタイムアウトしました。", e);
        }
    }

    // --- ヘルパーメソッド ---

    private void detectMfaPrompt(WebDriver driver, LoginProgressListener listener) {
        try {
            WebDriverWait mfaWait = new WebDriverWait(driver, Duration.ofSeconds(5)); // 短い待機
            mfaWait.until(ExpectedConditions.visibilityOfElementLocated(By.id("idRichContext_DisplaySign")));
            
            String displayCode = extractTextSafely(driver, By.id("idRichContext_DisplaySign"));
            if (displayCode != null && !displayCode.isBlank()) {
                String code = displayCode.trim();
                log.info("MFAコード検出: {}", code);
                listener.onMfaRequired(code, "認証アプリで承認が必要です [" + code + "]");
                
                // MFA承認待ち (最大60秒)
                new WebDriverWait(driver, Duration.ofSeconds(60))
                    .until(ExpectedConditions.invisibilityOfElementLocated(By.id("idRichContext_DisplaySign")));
            }
        } catch (TimeoutException ignored) {
            // MFAなし
        }
    }

    private void handleStaySignedInPrompt(WebDriver driver, LoginProgressListener listener, WebDriverWait wait) {
        try {
            // KMSIまたはホーム画面が出るまで待つ
            wait.until(d -> isElementDisplayed(d, By.id("idSIButton9")) || d.getCurrentUrl().contains("/ct/home"));

            if (driver.getCurrentUrl().contains("/ct/home")) return;

            WebElement button = driver.findElement(By.id("idSIButton9"));
            if (button.isDisplayed() && shouldClickStaySignedIn(button)) {
                listener.onStatusUpdate("CONFIRM_KMSI", "サインイン状態の維持を確認しています...");
                button.click();
            }
        } catch (Exception ignored) {
            // 無視して進む
        }
    }

    private boolean shouldClickStaySignedIn(WebElement button) {
        String text = button.getText();
        String value = button.getAttribute("value");
        return containsAffirmative(text) || containsAffirmative(value);
    }

    private boolean containsAffirmative(String value) {
        if (value == null) return false;
        String normalized = value.toLowerCase(Locale.ROOT);
        return normalized.contains("はい") || normalized.contains("yes") || normalized.contains("続行");
    }

    private String extractTextSafely(WebDriver driver, By locator) {
        try {
            return driver.findElement(locator).getText();
        } catch (Exception ignored) {
            return null;
        }
    }

    private boolean isElementDisplayed(WebDriver driver, By locator) {
        try {
            WebElement element = driver.findElement(locator);
            return element != null && element.isDisplayed();
        } catch (Exception ignored) {
            return false;
        }
    }

    // ★最適化: 現在のドメインのクッキーをすべて抽出する
    private Map<String, String> extractCookies(WebDriver driver) {
        Map<String, String> cookies = new HashMap<>();
        try {
            Set<Cookie> seleniumCookies = driver.manage().getCookies();
            for (Cookie c : seleniumCookies) {
                // すべてのクッキーを保存してセッション切れを防ぐ
                cookies.put(c.getName(), c.getValue());
            }
        } catch (Exception e) {
            log.error("Cookieの抽出中にエラーが発生しました。", e);
        }
        return cookies;
    }

    private boolean isLoginPage(Document document) {
        if (document == null) return true;
        String title = document.title();
        if (title != null) {
            String lower = title.toLowerCase(Locale.ROOT);
            if (lower.contains("sign in") || lower.contains("login") || title.contains("サインイン")) {
                return true;
            }
        }
        return document.selectFirst("form[action*='login']") != null;
    }

    private List<CourseEntry> convertCourses(List<com.example.demo.dto.Course> rawCourses) {
        List<CourseEntry> entries = new ArrayList<>();
        if (rawCourses == null) return entries;
        for (var course : rawCourses) {
            String periodKey = extractDigits(course.period());
            PeriodTime period = PERIOD_TIME_TABLE.get(periodKey);
            String start = (period != null) ? period.start() : null;
            String end = (period != null) ? period.end() : null;
            entries.add(new CourseEntry(
                    "course-" + UUID.randomUUID().toString().replaceAll("-", ""),
                    course.day(), course.period(), course.name(), course.location(),
                    start, end, "AUTO"
            ));
        }
        return entries;
    }

    private List<AssignmentEntry> convertAssignments(List<com.example.demo.dto.Assignment> assignments) {
        List<AssignmentEntry> converted = new ArrayList<>();
        if (assignments == null) return converted;
        for (var assignment : assignments) {
            converted.add(new AssignmentEntry(
                    "assignment-" + UUID.randomUUID().toString().replaceAll("-", ""),
                    assignment.courseName(), assignment.category(), assignment.title(),
                    normalizeDeadline(assignment.deadline()), assignment.url()
            ));
        }
        return converted;
    }

    private String normalizeDeadline(String deadline) {
        if (deadline == null) return null;
        String cleaned = deadline.replace('\u3000', ' ').replace("締切", "").replace("まで", "").trim();
        if (cleaned.isEmpty()) return null;
        for (DateTimeFormatter formatter : DEADLINE_PATTERNS) {
            try {
                LocalDateTime parsed = LocalDateTime.parse(cleaned, formatter);
                return parsed.format(ISO_FORMATTER);
            } catch (DateTimeParseException ignored) {}
        }
        log.warn("不明な日付フォーマットのため正規化できませんでした: {}", deadline);
        return cleaned;
    }

    private NextClassCard calculateNextClass(List<CourseEntry> timetable) {
        if (timetable == null || timetable.isEmpty()) return null;
        LocalDateTime now = LocalDateTime.now(JAPAN_ZONE);
        NextClassCard bestCard = null;
        Duration bestDuration = null;

        for (CourseEntry course : timetable) {
            DayOfWeek targetDay = DAY_OF_WEEK_MAP.get(course.day());
            if (targetDay == null) continue;
            LocalTime startTime = parseTime(course.startTime());
            if (startTime == null) continue;

            LocalDateTime startDateTime = nextOccurrence(now, targetDay, startTime);
            Duration untilStart = Duration.between(now, startDateTime);
            if (untilStart.isNegative()) continue;

            if (bestDuration == null || untilStart.compareTo(bestDuration) < 0) {
                bestDuration = untilStart;
                LocalTime endTime = parseTime(course.endTime());
                if (endTime == null) endTime = startTime.plusMinutes(90);
                LocalDateTime endDateTime = LocalDateTime.of(startDateTime.toLocalDate(), endTime);

                bestCard = new NextClassCard(
                        course.name(), course.day(), course.period(), course.location(),
                        startDateTime.format(ISO_FORMATTER),
                        endDateTime.format(ISO_FORMATTER),
                        formatIsoDuration(untilStart)
                );
            }
        }
        return bestCard;
    }

    private LocalDateTime nextOccurrence(LocalDateTime base, DayOfWeek targetDay, LocalTime startTime) {
        int diff = (targetDay.getValue() - base.getDayOfWeek().getValue() + 7) % 7;
        LocalDate targetDate = base.toLocalDate().plusDays(diff);
        LocalDateTime candidate = LocalDateTime.of(targetDate, startTime);
        return (diff == 0 && candidate.isBefore(base)) ? candidate.plusWeeks(1) : candidate;
    }

    private LocalTime parseTime(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            return LocalTime.parse(value);
        } catch (DateTimeParseException ignored) {
            return null;
        }
    }

    private String extractDigits(String value) {
        return value == null ? null : value.replaceAll("[^0-9]", "");
    }

    private String formatIsoDuration(Duration duration) {
        if (duration == null || duration.isNegative()) return "PT0M";
        long days = duration.toDays();
        duration = duration.minusDays(days);
        long hours = duration.toHours();
        duration = duration.minusHours(hours);
        long minutes = duration.toMinutes();

        StringBuilder builder = new StringBuilder("P");
        if (days > 0) builder.append(days).append('D');
        if (hours > 0 || minutes > 0 || builder.length() == 1) {
            builder.append('T');
            if (hours > 0) builder.append(hours).append('H');
            if (minutes > 0) builder.append(minutes).append('M');
        }
        if (builder.toString().equals("PT")) return "PT0M";
        if (builder.toString().equals("P")) return "PT0M";
        return builder.toString();
    }

    public interface LoginProgressListener {
        void onStatusUpdate(String status, String message);
        void onMfaRequired(String mfaCode, String message);
    }
}