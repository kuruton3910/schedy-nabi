package com.example.demo.service;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.example.demo.dto.SyncResult;
import com.example.demo.entity.UserCredential;
import com.example.demo.repository.UserCredentialRepository;
import com.example.demo.service.ManabaScrapingOrchestrator.InternalSyncOutcome; // ★ 追加
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.slf4j.Logger; // Loggerを追加
import org.slf4j.LoggerFactory; // Loggerを追加

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

    private static final Logger log = LoggerFactory.getLogger(AuthService.class); // Loggerを追加
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
     * @param listener     進捗通知を受け取るリスナー
     * @return スクレイピング結果 (Cookieを含まないDTO)
     * @throws Exception 処理中に発生した例外
     */
    @Transactional
    public SyncResult executeSync(String universityId, String password, boolean rememberMe, LoginProgressListener listener) throws Exception { // ★ listener引数を追加

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

        if (existingCookies.isEmpty() && (password == null || password.isBlank())) {
            throw new IllegalStateException("有効な認証情報がありません。パスワードを入力して再試行してください。");
        }

        // --- ステップ2: スクレイピング司令塔（Orchestrator）に処理を依頼 ---
        log.debug("スクレイピング処理を開始します。");
        InternalSyncOutcome outcome = scrapingOrchestrator.sync(universityId, password, existingCookies, listener);
        log.debug("スクレイピング処理が完了しました。");

        Map<String, String> newCookies = outcome.cookies();

        // --- ステップ3: 新しいCookieが取得できていれば、DBを更新 ---
        if (rememberMe && newCookies != null && !newCookies.isEmpty()) {
            if (password == null || password.isBlank()) {
                throw new IllegalStateException("rememberMe が有効な場合はパスワードが必要です。");
            }
            String encryptedCookie = encryptionService.encrypt(gson.toJson(newCookies));

            UserCredential credential = credentialOpt.orElseGet(() ->
                    new UserCredential(UUID.randomUUID(), universityId, null, null)
            );

            String encryptedPassword = encryptionService.encrypt(password);
            credential.setEncryptedPassword(encryptedPassword);
            credential.setEncryptedSessionCookie(encryptedCookie);

            userCredentialRepository.save(credential);
        } else if (!rememberMe && credentialOpt.isPresent()) {
            userCredentialRepository.delete(credentialOpt.get());
        }

        // Cookieを含まないDTOだけを返す
        return outcome.syncResultDto();
    }
}