package com.example.demo.service;

import com.example.demo.dto.SyncResult;
import org.slf4j.Logger; // Loggerを追加
import org.slf4j.LoggerFactory; // Loggerを追加
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.Iterator;

/**
 * 非同期のログイン・スクレイピングジョブを管理するService。
 * 元のLoginJobManagerのロジックをSpring Beanとして管理し、
 * 重たい処理をバックグラウンドで実行します。
 */
@Service
public class JobManagerService {

    private static final Logger log = LoggerFactory.getLogger(JobManagerService.class);
    private static final Duration JOB_TTL = Duration.ofMinutes(10);
    private final ExecutorService executor = Executors.newFixedThreadPool(5);
    private final ConcurrentHashMap<String, LoginJob> jobs = new ConcurrentHashMap<>();

    private final AuthService authService;

    public JobManagerService(AuthService authService) {
        this.authService = authService;
    }

    /**
     * 新しい同期ジョブを開始します。
     * @param username 大学のID
     * @param password 大学のパスワード
     * @param rememberMe パスワードを保存するかどうか
     * @return 開始されたジョブのインスタンス
     */
    // ★ rememberMe引数を追加
    public LoginJob startNewSyncJob(String username, String password, boolean rememberMe) {
        cleanupExpiredJobs();
        String jobId = UUID.randomUUID().toString();
        LoginJob job = new LoginJob(jobId, username, rememberMe);
        jobs.put(jobId, job);
    log.debug("新しい同期ジョブを開始しました: jobId={}", jobId);

        // executorを使って、重たい処理をバックグラウンドで実行
        executor.submit(() -> executeSyncJob(job, username, password));

        return job;
    }

    /**
     * 指定されたIDのジョブを取得します。
     * @param jobId ジョブID
     * @return ジョブのインスタンス、または存在しない場合はnull
     */
    public LoginJob getJob(String jobId) {
        if (jobId == null) {
            return null;
        }
        LoginJob job = jobs.get(jobId);
        if (job == null) {
             log.warn("指定されたジョブIDが見つかりません: {}", jobId);
        }
        return job;
    }

    /**
     * バックグラウンドで同期処理を実行する本体。
     * LoginProgressListenerを実装し、AuthService経由でOrchestratorに渡す。
     */
    // ★ rememberMe引数を追加
    private void executeSyncJob(LoginJob job, String username, String password) {
        job.updateStage("QUEUED", "ログインキューに登録しました");
    log.debug("ジョブ実行開始: jobId={}", job.getId());

        // ★ LoginProgressListenerの実装を作成 ★
        LoginProgressListener listener = new LoginProgressListener() {
            @Override
            public void onStatusUpdate(String stage, String message) {
                log.debug("Job {} Status Update: Stage={}, Message={}", job.getId(), stage, message);
                job.updateStage(stage, message); // Jobの状態を更新
            }
            @Override
            public void onMfaRequired(String code, String message) {
                log.info("Job {} MFA Required: Code={}", job.getId(), code);
                job.updateMfa(code, message); // JobのMFA情報を更新
            }
        };

        try {
            job.updateStatus("IN_PROGRESS", "manabaに接続しています...");
            
            // ★ AuthService経由で同期処理を実行し、リスナーを渡す ★
            SyncResult result = authService.executeSync(username, password, job.isRememberMe(), listener); 
            
            job.complete(result, "manabaからの情報取得が完了しました。");
            log.debug("ジョブ実行成功: jobId={}", job.getId());

        } catch (Exception e) {
            log.error("ジョブ実行中にエラーが発生しました: jobId={}", job.getId(), e);
            // エラー原因を特定し、より分かりやすいメッセージを返す
            String errorMessage = "予期しないエラーが発生しました: " + e.getMessage();
            if (e.getCause() != null) {
                errorMessage = "エラーが発生しました: " + e.getCause().getMessage();
            } else if (e.getMessage() != null) {
                 // 特定のエラーメッセージに対するハンドリングを追加可能
                 if (e.getMessage().contains("Cookieの有効期限が切れています")) {
                      errorMessage = "セッションの有効期限が切れました。再度ログインしてください。";
                 } else if (e.getMessage().contains("ログインに失敗しました")) {
                      errorMessage = "大学IDまたはパスワードが間違っている可能性があります。";
                 } else {
                      errorMessage = "エラーが発生しました: " + e.getMessage();
                 }
            }
            job.fail("FAILED", errorMessage);
        }
    }

    /**
     * 古いジョブをメモリから削除する。
     */
    private void cleanupExpiredJobs() {
        Instant expiration = Instant.now().minus(JOB_TTL);
        int removedCount = 0;
        Iterator<Map.Entry<String, LoginJob>> iterator = jobs.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<String, LoginJob> entry = iterator.next();
            if (entry.getValue().getUpdatedAt().isBefore(expiration)) {
                iterator.remove();
                removedCount++;
            }
        }
        if (removedCount > 0) {
            log.info("{}件の期限切れジョブを削除しました。", removedCount);
        }
    }

    // --- Inner Class: LoginJob (変更なし、ただしSyncResultのimportを確認) ---
    /**
     * 非同期処理の進捗状況と結果を保持するクラス。
     */
    public static final class LoginJob {
        private final String id;
        private final String username; // 追加: どのユーザーのジョブか識別するため
        private final boolean rememberMe;
        private volatile String status;
        private volatile String stage;
        private volatile String message;
        private volatile String mfaCode;
        private volatile String mfaMessage;
        private volatile String error;
        private volatile SyncResult result; // ★ import com.example.demo.dto.SyncResult; が必要
        private final Instant createdAt;
        private volatile Instant updatedAt;

        private LoginJob(String id, String username, boolean rememberMe) { // usernameを追加
            this.id = id;
            this.username = username; // usernameを初期化
            this.rememberMe = rememberMe;
            this.status = "QUEUED";
            this.stage = "QUEUED";
            this.createdAt = Instant.now();
            this.updatedAt = this.createdAt;
        }

        // --- Getters ---
        public String getId() { return id; }
        public String getUsername() { return username; } // usernameのGetterを追加
    public boolean isRememberMe() { return rememberMe; }
        public String getStatus() { return status; }
        public String getStage() { return stage; }
        public String getMessage() { return message; }
        public String getMfaCode() { return mfaCode; }
        public String getMfaMessage() { return mfaMessage; }
        public String getError() { return error; }
        public SyncResult getResult() { return result; }
        public Instant getUpdatedAt() { return updatedAt; }

        // --- State Update Methods (synchronizedでスレッドセーフを保証) ---
        private synchronized void updateStatus(String newStatus, String newMessage) {
            if (newStatus != null) {
                this.status = newStatus;
                this.stage = newStatus; // Statusが変わったらStageも同じにするのが基本
            }
            if (newMessage != null && !newMessage.isBlank()) {
                this.message = newMessage;
            }
            this.updatedAt = Instant.now();
        }

        private synchronized void updateStage(String stage, String newMessage) {
            if (stage != null) {
                this.stage = stage;
                // 成功/失敗/MFA要求中でなければ、進行中(IN_PROGRESS)にする
                if (!"SUCCESS".equals(status) && !"FAILED".equals(status) && !"MFA_REQUIRED".equals(status)) {
                    this.status = "IN_PROGRESS";
                }
            }
            if (newMessage != null && !newMessage.isBlank()) {
                this.message = newMessage;
            }
             // MFA関連のメッセージがクリアされるように調整
             if (!"MFA_REQUIRED".equals(stage)) {
                 this.mfaCode = null;
                 this.mfaMessage = null;
             }
            this.updatedAt = Instant.now();
        }

        private synchronized void updateMfa(String code, String message) {
            if (code != null && !code.isBlank()) {
                this.mfaCode = code;
                this.status = "MFA_REQUIRED"; // MFAが必要な場合はStatusも更新
                this.stage = "MFA_REQUIRED";
            }
            if (message != null && !message.isBlank()) {
                this.mfaMessage = message;
                this.message = message; // メインメッセージもMFAメッセージで上書き
            }
            this.updatedAt = Instant.now();
        }

        private synchronized void complete(SyncResult result, String finalMessage) {
            this.result = result;
            this.status = "SUCCESS";
            this.stage = "SUCCESS";
            this.message = finalMessage;
            this.mfaCode = null; // 成功時はMFA情報をクリア
            this.mfaMessage = null;
            this.error = null; // 成功時はエラー情報をクリア
            this.updatedAt = Instant.now();
        }

        private synchronized void fail(String status, String errorMessage) {
            this.status = (status != null && status.equals("FAILED")) ? status : "FAILED"; // 基本はFAILED
            this.stage = this.status; // Stageも合わせる
            this.error = errorMessage;
            if (errorMessage != null && !errorMessage.isBlank()) {
                this.message = errorMessage; // メインメッセージもエラーで上書き
            }
            this.result = null; // 失敗時は結果をクリア
            this.mfaCode = null; // 失敗時はMFA情報をクリア
            this.mfaMessage = null;
            this.updatedAt = Instant.now();
        }
    }
}