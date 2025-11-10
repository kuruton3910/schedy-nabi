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
    * @param userProfileId ユーザープロファイルのUUID（任意）
    * @param universityId 大学のID
     * @param password     大学のパスワード
     * @param rememberMe   パスワードを保存するかどうか
     * @param listener     進捗通知を受け取るリスナー (ManabaScrapingOrchestratorの型)
     * @return スクレイピング結果 (Cookieを含まないDTO)
     * @throws Exception 処理中に発生した例外
     */
    @Transactional
    // ★★★ listener引数の型を ManabaScrapingOrchestrator.LoginProgressListener に修正 ★★★
    public SyncResult executeSync(String userProfileId, String universityId, String password, boolean rememberMe, LoginProgressListener listener) throws Exception {

        UUID profileUuid = null;
        Optional<UserCredential> credentialOpt = Optional.empty();

        if (userProfileId != null && !userProfileId.isBlank()) {
            try {
                profileUuid = UUID.fromString(userProfileId);
                credentialOpt = userCredentialRepository.findById(profileUuid);
            } catch (IllegalArgumentException e) {
                log.warn("無効なユーザープロファイルIDが指定されました: {}", userProfileId);
            }
        }

        if (credentialOpt.isEmpty() && universityId != null && !universityId.isBlank()) {
            credentialOpt = userCredentialRepository.findByUniversityId(universityId);
            if (credentialOpt.isPresent()) {
                profileUuid = credentialOpt.get().getId();
            }
        }

        if (credentialOpt.isEmpty() && (universityId == null || universityId.isBlank())) {
            throw new IllegalStateException("ユーザーを特定できませんでした。大学IDを指定してください。");
        }

        Map<String, String> existingCookies = Collections.emptyMap();
        if (credentialOpt.isPresent() && credentialOpt.get().getEncryptedSessionCookie() != null) {
            log.debug("既存のセッションCookieを復号します。");
            try {
                String decryptedCookieJson = encryptionService.decrypt(credentialOpt.get().getEncryptedSessionCookie());
                existingCookies = gson.fromJson(decryptedCookieJson, COOKIE_MAP_TYPE);
                log.debug("Cookieの復号に成功しました。");
            } catch (Exception e) {
                log.warn("保存済みCookieの復号に失敗しました。Cookieなしで続行します。", e);
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

        String effectiveUniversityId = universityId;
        if ((effectiveUniversityId == null || effectiveUniversityId.isBlank()) && credentialOpt.isPresent()) {
            effectiveUniversityId = credentialOpt.get().getUniversityId();
        }

        if (effectiveUniversityId == null || effectiveUniversityId.isBlank()) {
            throw new IllegalStateException("大学IDを特定できませんでした。再度ログインしてください。");
        }

        log.debug("スクレイピング処理を開始します。");
        InternalSyncOutcome outcome;
        try {
            outcome = scrapingOrchestrator.sync(effectiveUniversityId, password, existingCookies, listener);
            log.debug("スクレイピング処理が完了しました。");
        } catch (IOException e) {
            log.error("スクレイピング処理中にエラーが発生しました。", e);
            listener.onStatusUpdate("ERROR", "データの取得に失敗しました: " + e.getMessage());
            throw e;
        } catch (Exception e) {
            log.error("予期せぬエラーが発生しました。", e);
            listener.onStatusUpdate("ERROR", "予期せぬエラーが発生しました: " + e.getMessage());
            throw e;
        }

        Map<String, String> newCookies = outcome.cookies();

        UserCredential credentialToUpdate;
        if (credentialOpt.isPresent()) {
            credentialToUpdate = credentialOpt.get();
        } else {
            UUID newId = profileUuid != null ? profileUuid : UUID.randomUUID();
            credentialToUpdate = new UserCredential(newId, effectiveUniversityId, null, null);
        }

        String responseUserId = credentialOpt.map(UserCredential::getId).map(UUID::toString).orElse(null);

        if (rememberMe) {
            if (password == null || password.isBlank()) {
                if (credentialToUpdate.getEncryptedPassword() == null) {
                    log.error("rememberMe=trueですが、有効なパスワードがありません。");
                    throw new IllegalStateException("パスワードを保存するには、有効なパスワードが必要です。");
                }
                log.debug("rememberMe=trueですがパスワード入力なし。DBの暗号化パスワードを維持します。");
            } else {
                String encryptedPassword = encryptionService.encrypt(password);
                credentialToUpdate.setEncryptedPassword(encryptedPassword);
                log.debug("新しいパスワードを暗号化して保存します。");
            }

            credentialToUpdate.setUniversityId(effectiveUniversityId);

            if (newCookies != null && !newCookies.isEmpty()) {
                String encryptedCookie = encryptionService.encrypt(gson.toJson(newCookies));
                credentialToUpdate.setEncryptedSessionCookie(encryptedCookie);
                log.debug("新しいCookieを暗号化して保存します。");
            } else {
                credentialToUpdate.setEncryptedSessionCookie(null);
                log.warn("新しいCookieが取得できなかったため、DBのCookieをクリアします。");
            }

            UserCredential saved = userCredentialRepository.save(credentialToUpdate);
            log.info("ユーザー資格情報 (ID: {}) を保存しました。", saved.getId());
            responseUserId = saved.getId().toString();

        } else {
            if (credentialOpt.isPresent()) {
                userCredentialRepository.delete(credentialOpt.get());
                log.info("rememberMe=false のため、ユーザー資格情報 (ID: {}) を削除しました。", credentialOpt.get().getId());
                responseUserId = null;
            } else {
                log.debug("rememberMe=false で、DBにも情報がないため、削除処理はスキップします。");
            }
        }

        SyncResult rawResult = outcome.syncResultDto();
        return new SyncResult(
                responseUserId,
                rawResult.username(),
                rawResult.syncedAt(),
                rawResult.timetable(),
                rawResult.assignments(),
                rawResult.nextClass()
        );
    }

    @Transactional
    public void refreshSessionOnly(UUID userProfileId, LoginProgressListener listener) throws Exception {
        Optional<UserCredential> credentialOpt = userCredentialRepository.findById(userProfileId);
        if (credentialOpt.isEmpty()) {
            throw new IllegalStateException("指定されたユーザーIDの資格情報が見つかりません: " + userProfileId);
        }

        Map<String, String> existingCookies = Collections.emptyMap();
        String password = null;

        UserCredential credential = credentialOpt.get();
        if (credential.getEncryptedSessionCookie() != null) {
            try {
                String decryptedCookieJson = encryptionService.decrypt(credential.getEncryptedSessionCookie());
                existingCookies = gson.fromJson(decryptedCookieJson, COOKIE_MAP_TYPE);
            } catch (Exception e) {
                log.warn("保存済みCookieの復号に失敗しました。Cookieなしでセッション更新を試みます。");
            }
        }

        if (credential.getEncryptedPassword() != null) {
            try {
                password = encryptionService.decrypt(credential.getEncryptedPassword());
            } catch (Exception e) {
                log.error("パスワードの復号に失敗しました。", e);
                throw new IllegalStateException("有効な認証情報がありません。パスワードを再入力してください。");
            }
        }

        Map<String, String> refreshedCookies;
        try {
            refreshedCookies = scrapingOrchestrator.refreshSessionOnly(credential.getUniversityId(), password, existingCookies, listener);
        } catch (IOException e) {
            log.error("セッション更新中にエラーが発生しました。", e);
            listener.onStatusUpdate("ERROR", "セッション更新に失敗しました: " + e.getMessage());
            throw e;
        }

        if (refreshedCookies == null || refreshedCookies.isEmpty()) {
            listener.onStatusUpdate("SKIP", "有効なCookieがなかったためセッション更新をスキップしました。");
            log.info("ユーザーID {} のセッション更新はスキップされました (新しいCookieなし)", credential.getId());
            return;
        }

        String encryptedCookie = encryptionService.encrypt(gson.toJson(refreshedCookies));
        credential.setEncryptedSessionCookie(encryptedCookie);
        userCredentialRepository.save(credential);
        log.info("ユーザー資格情報 (ID: {}) のセッションCookieを更新しました。", credential.getId());
    }
}