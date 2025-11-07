package com.example.demo.service;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.example.demo.dto.SyncResult;
import com.example.demo.entity.UserCredential;
import com.example.demo.repository.UserCredentialRepository;
// ★★★ ManabaScrapingOrchestrator の内部インターフェースをインポート ★★★
import com.example.demo.service.ManabaScrapingOrchestrator.InternalSyncOutcome;
import com.example.demo.service.ManabaScrapingOrchestrator.LoginProgressListener; // ★★★ これを追加 ★★★

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException; // ★★★ IOExceptionを追加 ★★★
import java.lang.reflect.Type;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * ユーザー資格情報の永続化（DBとのやり取り）と、
 * スクレイピング実行の司令塔（Orchestrator）の呼び出しを担当するService。
 * JobManagerServiceからバックグラウンドで呼び出されることを想定している。
 */
@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);
    private final UserCredentialRepository userCredentialRepository;
    private final EncryptionService encryptionService;
    private final ManabaScrapingOrchestrator scrapingOrchestrator;
    private final Gson gson = new Gson();
    private static final Type COOKIE_MAP_TYPE = new TypeToken<Map<String, String>>() {}.getType();

    public AuthService(
            UserCredentialRepository userCredentialRepository,
            EncryptionService encryptionService,
            ManabaScrapingOrchestrator scrapingOrchestrator
    ) {
        this.userCredentialRepository = userCredentialRepository;
        this.encryptionService = encryptionService;
        this.scrapingOrchestrator = scrapingOrchestrator;
    }

    /**
     * JobManagerServiceから呼び出される同期処理の本体。
     * 1. DBから既存Cookieを読み込み
     * 2. スクレイピングを実行 (リスナー経由で進捗通知)
     * 3. 結果をDBに保存
     * の一連の流れをトランザクション管理下で行う。
     *
     * @param universityId 大学のID
     * @param password     大学のパスワード
     * @param rememberMe   パスワードを保存するかどうか
     * @param listener     進捗通知を受け取るリスナー (ManabaScrapingOrchestratorの型)
     * @return スクレイピング結果 (Cookieを含まないDTO)
     * @throws Exception 処理中に発生した例外
     */
    @Transactional
    // ★★★ listener引数の型を ManabaScrapingOrchestrator.LoginProgressListener に修正 ★★★
    public SyncResult executeSync(String universityId, String password, boolean rememberMe, LoginProgressListener listener) throws Exception {

        // --- ステップ1: DBから既存のCookieを読み込む試み ---
        Optional<UserCredential> credentialOpt = userCredentialRepository.findByUniversityId(universityId);
        Map<String, String> existingCookies = Collections.emptyMap();

        if (credentialOpt.isPresent() && credentialOpt.get().getEncryptedSessionCookie() != null) {
            log.debug("既存のセッションCookieを復号します。");
            try {
                String decryptedCookieJson = encryptionService.decrypt(credentialOpt.get().getEncryptedSessionCookie());
                existingCookies = gson.fromJson(decryptedCookieJson, COOKIE_MAP_TYPE);
                log.debug("Cookieの復号に成功しました。");
            } catch (Exception e) {
                log.warn("保存済みCookieの復号に失敗しました。Cookieなしで続行します。", e);
                // 復号に失敗した場合は、古いCookieとして扱わず、空のままで続行
            }
        }

        if (password == null || password.isBlank()) {
            if (credentialOpt.isPresent() && credentialOpt.get().getEncryptedPassword() != null) {
                try {
                    password = encryptionService.decrypt(credentialOpt.get().getEncryptedPassword());
                    log.debug("DBからパスワードを復号しました。");
                } catch (Exception e) {
                    log.error("DBのパスワード復号に失敗しました。", e);
                    throw new IllegalStateException("有効な認証情報がありません。パスワードを再入力してください。");
                }
            } else if (existingCookies.isEmpty()) {
                throw new IllegalStateException("有効な認証情報がありません。パスワードを入力して再試行してください。");
            }
        }


        // --- ステップ2: スクレイピング司令塔（Orchestrator）に処理を依頼 ---
        log.debug("スクレイピング処理を開始します。");
        InternalSyncOutcome outcome; // tryの外で宣言
        try {
            // ★★★ ここで渡す listener は、引数で受け取った正しい型の listener ★★★
            outcome = scrapingOrchestrator.sync(universityId, password, existingCookies, listener);
            log.debug("スクレイピング処理が完了しました。");
        } catch (IOException e) { // ★ IOExceptionをキャッチする可能性
             log.error("スクレイピング処理中にエラーが発生しました。", e);
             // リスナーにエラーを通知することも検討
             listener.onStatusUpdate("ERROR", "データの取得に失敗しました: " + e.getMessage());
             throw e; // エラーを再スローしてトランザクションをロールバックさせる
        } catch (Exception e) { // その他の予期せぬエラー
             log.error("予期せぬエラーが発生しました。", e);
             listener.onStatusUpdate("ERROR", "予期せぬエラーが発生しました: " + e.getMessage());
             throw e;
        }


        Map<String, String> newCookies = outcome.cookies();

        // --- ステップ3: 新しいCookieが取得できていれば、DBを更新 ---
        // DBから再度取得して、最新の状態で更新する (トランザクション分離レベルによっては必要)
        UserCredential credentialToUpdate = userCredentialRepository.findByUniversityId(universityId)
                .orElseGet(() -> new UserCredential(UUID.randomUUID(), universityId, null, null));

        if (rememberMe) {
            if (password == null || password.isBlank()) {
                // rememberMe=trueなのにパスワードがない場合、DBから復号を試みるか、エラーにする
                if (credentialToUpdate.getEncryptedPassword() == null) {
                     log.error("rememberMe=trueですが、有効なパスワードがありません。");
                     throw new IllegalStateException("パスワードを保存するには、有効なパスワードが必要です。");
                }
                // password変数がnullでも、DBに暗号化パスワードがあれば更新は可能
                log.debug("rememberMe=trueですがパスワード入力なし。DBの暗号化パスワードを維持します。");
            } else {
                 // 新しいパスワードが提供された場合のみ暗号化して保存
                 String encryptedPassword = encryptionService.encrypt(password);
                 credentialToUpdate.setEncryptedPassword(encryptedPassword);
                 log.debug("新しいパスワードを暗号化して保存します。");
            }

            // Cookieは必ず更新 (新しいCookieがなくてもnullで上書きされる)
            if (newCookies != null && !newCookies.isEmpty()) {
                String encryptedCookie = encryptionService.encrypt(gson.toJson(newCookies));
                credentialToUpdate.setEncryptedSessionCookie(encryptedCookie);
                log.debug("新しいCookieを暗号化して保存します。");
            } else {
                // 新しいCookieが取得できなかった場合（ログイン失敗など）はnullをセット
                credentialToUpdate.setEncryptedSessionCookie(null);
                log.warn("新しいCookieが取得できなかったため、DBのCookieをクリアします。");
            }
            userCredentialRepository.save(credentialToUpdate);
            log.info("ユーザー資格情報 (ID: {}) を保存しました。", universityId);

        } else {
            // rememberMeがfalseの場合、DBから資格情報を削除する
            if (credentialOpt.isPresent()) {
                userCredentialRepository.delete(credentialToUpdate);
                log.info("rememberMe=false のため、ユーザー資格情報 (ID: {}) を削除しました。", universityId);
            } else {
                log.debug("rememberMe=false で、DBにも情報がないため、削除処理はスキップします。");
            }
        }


        // Cookieを含まないDTOだけを返す
        return outcome.syncResultDto();
    }
}