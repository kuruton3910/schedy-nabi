package com.example.demo.service;

import com.example.demo.dto.*; // DTOパッケージをインポート
import io.github.bonigarcia.wdm.WebDriverManager;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.openqa.selenium.*;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.springframework.stereotype.Service;
import org.slf4j.Logger; // Loggerを追加
import org.slf4j.LoggerFactory; // Loggerを追加

import java.io.IOException;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;
// import java.util.NoSuchElementException; // ← ambiguous なので削除

/**
 * Selenium/Jsoupによるログイン、スクレイピング、データ整形、ビジネスロジックを統括する司令塔Service。
 * 元のManabaScrapingService.javaのロジックをSpringコンポーネントとして再実装したもの。
 * LoginProgressListenerを通じて進捗を通知する。
 */
@Service
public class ManabaScrapingOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(ManabaScrapingOrchestrator.class); // Loggerを追加

    // --- 定数定義 (省略せず全て記述) ---
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
    // --- 定数定義ここまで ---

    private final ScrapingService scrapingService;

    public ManabaScrapingOrchestrator(ScrapingService scrapingService) {
        this.scrapingService = scrapingService;
    }

    // 内部的な結果とCookieを保持するレコード (変更なし)
    public record InternalSyncOutcome(SyncResult syncResultDto, Map<String, String> cookies) {}

    /**
     * 同期処理のメインエントリーポイント。LoginProgressListenerを通じて進捗を通知する。
     */
    // ★★★ listener引数を追加 ★★★
    public InternalSyncOutcome sync(String username, String password, Map<String, String> existingCookies, LoginProgressListener listener) throws IOException {
        listener.onStatusUpdate("AUTH_START", "認証処理を開始します...");
        if (existingCookies != null && !existingCookies.isEmpty()) {
            try {
                listener.onStatusUpdate("COOKIE_AUTH", "Cookie認証を試行中...");
                return scrapeWithExistingCookies(username, existingCookies, listener); // ★ listenerを渡す
            } catch (IOException e) {
                log.warn("Cookie認証に失敗しました: {}", e.getMessage());
                listener.onStatusUpdate("COOKIE_FAIL", "Cookie認証失敗。パスワード認証に移行します。");
                // パスワード認証へフォールバック
            }
        }

        if (username == null || username.isBlank() || password == null || password.isBlank()) {
            throw new IllegalStateException("有効なCookieがありません。IDとパスワードを指定して再同期してください。");
        }

        listener.onStatusUpdate("PASSWORD_AUTH", "パスワード認証を開始します...");
        return loginAndScrape(username, password, listener); // ★ listenerを渡す
    }

    // ★★★ listener引数を追加 ★★★
    private InternalSyncOutcome scrapeWithExistingCookies(String username, Map<String, String> cookies, LoginProgressListener listener) throws IOException {
        listener.onStatusUpdate("FETCH_HOME", "ホーム画面を取得中...");
        Document homeDoc = Jsoup.connect(HOME_COURSE_URL)
                .cookies(cookies).userAgent(USER_AGENT).timeout(REQUEST_TIMEOUT_MILLIS).get();
        if (isLoginPage(homeDoc)) {
            throw new IOException("Cookieの有効期限が切れています。");
        }
        listener.onStatusUpdate("FETCH_HOME_SUCCESS", "ホーム画面の取得成功。");
        return buildInternalSyncOutcome(username, cookies, listener); // ★ listenerを渡す
    }

    // ★★★ listener引数を追加 ★★★
    private InternalSyncOutcome loginAndScrape(String username, String password, LoginProgressListener listener) throws IOException {
        WebDriverManager.chromedriver().setup();
        ChromeOptions options = new ChromeOptions();
        options.addArguments("--headless", "--disable-gpu", "--window-size=1920,1080", "--no-sandbox", "--disable-dev-shm-usage");
        WebDriver driver = null; // finallyで閉じるために外で宣言
        Map<String, String> freshCookies = Collections.emptyMap(); // 初期化

        try {
            driver = new ChromeDriver(options);
            performLogin(driver, username, password, listener); // ★ listenerを渡す
            listener.onStatusUpdate("FETCH_COOKIE_PAGE", "ログイン後のCookie取得ページにアクセス中...");
            driver.get(HOME_COURSE_URL); // Cookieを取得するためにホーム画面にアクセス
            freshCookies = extractCookies(driver);
            listener.onStatusUpdate("FETCH_COOKIE_SUCCESS", "新しいCookieを取得しました。");
        } catch (Exception e) {
            log.error("manabaへのログインまたはCookie取得中にエラーが発生しました。", e);
            throw new IOException("manabaへのログインに失敗しました: " + e.getMessage(), e);
        } finally {
            if (driver != null) {
                driver.quit();
            }
        }

        if (freshCookies.isEmpty()) {
            throw new IOException("ログイン後のCookie取得に失敗しました。");
        }
        return buildInternalSyncOutcome(username, freshCookies, listener); // ★ listenerを渡す
    }

    // ★★★ listener引数を追加 ★★★
    private InternalSyncOutcome buildInternalSyncOutcome(String username, Map<String, String> cookies, LoginProgressListener listener) throws IOException {
        listener.onStatusUpdate("SCRAPE_START", "データのスクレイピングを開始します...");
        var rawCourses = scrapingService.parseTimetableToList(cookies);
        var rawAssignments = scrapingService.getAllAssignments(cookies);
        listener.onStatusUpdate("SCRAPE_COMPLETE", "データのスクレイピングが完了しました。");

        listener.onStatusUpdate("DATA_PROCESSING", "取得データを整形中...");
        List<CourseEntry> timetable = convertCourses(rawCourses);
        List<AssignmentEntry> assignments = convertAssignments(rawAssignments);
        NextClassCard nextClass = calculateNextClass(timetable);
        String syncedAt = LocalDateTime.now(JAPAN_ZONE).format(ISO_FORMATTER); // 正しいフォーマッタを使用
        listener.onStatusUpdate("DATA_PROCESSING_COMPLETE", "データ整形完了。");

        SyncResult syncResultDto = new SyncResult(username, syncedAt, timetable, assignments, nextClass);
        return new InternalSyncOutcome(syncResultDto, cookies);
    }

    // ★★★ listener引数を追加 ★★★
    private void performLogin(WebDriver driver, String username, String password, LoginProgressListener listener) {
        WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(120)); // 必要に応じてタイムアウト調整
        listener.onStatusUpdate("ACCESS_LOGIN_PAGE", "ログインページにアクセス中...");
        driver.get(LOGIN_URL);

        listener.onStatusUpdate("INPUT_USERNAME", "ユーザー名を入力中...");
        wait.until(ExpectedConditions.visibilityOfElementLocated(By.id("i0116"))).sendKeys(username);
        listener.onStatusUpdate("CLICK_NEXT_1", "「次へ」をクリック中...");
        wait.until(ExpectedConditions.elementToBeClickable(By.id("idSIButton9"))).click();

        listener.onStatusUpdate("INPUT_PASSWORD", "パスワードを入力中...");
        wait.until(ExpectedConditions.visibilityOfElementLocated(By.id("i0118"))).sendKeys(password);
        listener.onStatusUpdate("CLICK_SIGNIN", "「サインイン」をクリック中...");
        wait.until(ExpectedConditions.elementToBeClickable(By.id("idSIButton9"))).click();
        listener.onStatusUpdate("PASSWORD_SUBMITTED", "パスワードを送信しました。");

        detectMfaPrompt(driver, listener); // ★ listenerを渡す
        handleStaySignedInPrompt(driver, listener); // ★ listenerを渡す

        listener.onStatusUpdate("WAITING_HOME", "ホーム画面への遷移を待機中...");
        wait.until(ExpectedConditions.urlContains("/ct/home"));
        listener.onStatusUpdate("LOGIN_SUCCESS", "ログイン成功を確認しました。");
    }

    // ★★★ listener引数を追加し、通知を有効化 ★★★
    private void detectMfaPrompt(WebDriver driver, LoginProgressListener listener) {
        WebDriverWait shortWait = new WebDriverWait(driver, Duration.ofSeconds(30)); // 少し長めに待つ
        try {
            log.info("MFAプロンプトが表示されるか確認中...");
            shortWait.until(ExpectedConditions.visibilityOfElementLocated(By.id("idRichContext_DisplaySign")));
            String displayCode = extractTextSafely(driver, By.id("idRichContext_DisplaySign"));
            if (displayCode != null && !displayCode.isBlank()) {
                String normalized = displayCode.trim();
                log.info("MFAコードを検出: {}", normalized);
                // ★ 通知を有効化 ★
                listener.onMfaRequired(normalized, "認証アプリで承認が必要です [" + normalized + "]");
                // ステータス更新は onMfaRequired 内で行われる想定なのでここでは不要かも
                // listener.onStatusUpdate("MFA_REQUIRED", "Authenticator アプリでの承認待ちです...");
            } else {
                 log.info("MFAコード要素は見つかったが、コードが空でした。");
            }
        } catch (TimeoutException ignored) {
            log.info("MFAプロンプトは表示されませんでした。");
            // MFA が要求されない場合は何もしない
        } catch (Exception e) {
             log.error("MFAプロンプト検出中に予期せぬエラーが発生しました。", e);
             // エラーが発生してもログイン処理自体は続行を試みる
        }
    }

    // ★★★ listener引数を追加 ★★★
    private void handleStaySignedInPrompt(WebDriver driver, LoginProgressListener listener) {
        WebDriverWait kmsiWait = new WebDriverWait(driver, Duration.ofSeconds(60));
        try {
            log.info("「サインイン状態の維持」プロンプトが表示されるか確認中...");
            kmsiWait.until(d -> {
                try {
                    WebElement button = d.findElement(By.id("idSIButton9")); // KMSI画面のボタンIDを確認
                    if (button.isDisplayed() && shouldClickStaySignedIn(button)) {
                        log.info("「サインイン状態の維持」プロンプトを「はい」でクリックします。");
                        listener.onStatusUpdate("CONFIRM_KMSI", "サインイン状態の維持を確認しています...");
                        button.click();
                        return true; // ボタンをクリックしたので待機終了
                    } else if (d.getCurrentUrl().contains("/ct/home")) {
                        log.info("すでにホーム画面に遷移済みのため、KMSI確認はスキップします。");
                        return true; // すでにホーム画面なら待機終了
                    }
                } catch (org.openqa.selenium.NoSuchElementException ignored) { // ★ 完全修飾名で指定
                    // 要素が見つからない場合、まだ表示されていないか、表示されないパターン
                    if (d.getCurrentUrl().contains("/ct/home")) {
                         log.info("すでにホーム画面に遷移済みのため、KMSI確認はスキップします。");
                         return true; // すでにホーム画面なら待機終了
                    }
                    return false; // まだ待機
                } catch (Exception e) {
                    log.warn("KMSIプロンプトの処理中にエラーが発生しましたが、続行します。", e);
                    return true; // 不明なエラーでも先に進む
                }
                 return false; // まだ待機
            });
        } catch (TimeoutException ignored) {
            log.info("「サインイン状態の維持」プロンプトは表示されませんでした。");
            // タイムアウト = プロンプトが表示されないか、ホーム画面に遷移済み
        } catch (Exception e) {
             log.error("KMSIプロンプト処理中に予期せぬエラーが発生しました。", e);
        }
    }

    // --- 他のヘルパーメソッド (shouldClickStaySignedIn, containsAffirmative, extractTextSafely, extractCookies, isLoginPage, convertCourses, convertAssignments, normalizeDeadline, calculateNextClass, nextOccurrence, parseTime, extractDigits, formatIsoDuration) は変更なし ---
    // (省略せずに全て含めてください)
     private boolean shouldClickStaySignedIn(WebElement button) {
         if (button == null) return false;
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
         } catch (org.openqa.selenium.NoSuchElementException ignored) { // ★ 完全修飾名
             return null;
         }
     }
     
     private Map<String, String> extractCookies(WebDriver driver) {
         Map<String, String> cookies = new HashMap<>();
         try {
            driver.manage().getCookies().forEach(cookie -> cookies.put(cookie.getName(), cookie.getValue()));
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
             } catch (DateTimeParseException ignored) {
             }
         }
         log.warn("不明な日付フォーマットのため正規化できませんでした: {}", deadline);
         return cleaned; // 解析できなかった場合は元の文字列(クリーニング後)を返す
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
                 if (endTime == null) endTime = startTime.plusMinutes(90); // デフォルト90分授業と仮定
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
         // 同じ日の授業で、すでに開始時刻を過ぎている場合は来週にする
         return (diff == 0 && candidate.isBefore(base)) ? candidate.plusWeeks(1) : candidate;
     }
 
     private LocalTime parseTime(String value) {
         if (value == null || value.isBlank()) return null;
         try {
             return LocalTime.parse(value); // HH:mm 形式を想定
         } catch (DateTimeParseException ignored) {
             log.warn("不正な時刻フォーマットです: {}", value);
             return null;
         }
     }
 
     private String extractDigits(String value) {
         if (value == null) return null;
         String digits = value.replaceAll("[^0-9]", "");
         return digits.isEmpty() ? value : digits; // 数字がなければ元の値を返す
     }
 
     private String formatIsoDuration(Duration duration) {
         if (duration == null || duration.isNegative()) return "PT0M"; // 負の期間は0分とする
         long days = duration.toDays();
         duration = duration.minusDays(days);
         long hours = duration.toHours();
         duration = duration.minusHours(hours);
         long minutes = duration.toMinutes();
         
         StringBuilder builder = new StringBuilder("P");
         if (days > 0) builder.append(days).append('D');
         if (hours > 0 || minutes > 0 || builder.length() == 1) { // 時間か分があるか、PしかなければTを追加
             builder.append('T');
             if (hours > 0) builder.append(hours).append('H');
             if (minutes > 0) builder.append(minutes).append('M');
         }
         // もし PT のままなら、 PT0M にする
         if (builder.toString().equals("PT")) return "PT0M"; 
         // もし P のままなら（0日の場合）、PT0M にする
         if (builder.toString().equals("P")) return "PT0M"; 
         
         return builder.toString();
     }
}