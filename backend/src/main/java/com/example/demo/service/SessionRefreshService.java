package com.example.demo.service;

import com.example.demo.entity.UserCredential;
import com.example.demo.repository.UserCredentialRepository;
import com.example.demo.service.ManabaScrapingOrchestrator.LoginProgressListener; // ★ LoginProgressListenerをインポート
import org.springframework.scheduling.annotation.Scheduled; // ★ Springのスケジューラー
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * バックグラウンドで定期的に全ユーザーのmanabaセッション(A)を
 * 更新（再取得）するためのスケジューラー。
 * サーバーのメモリクラッシュを防ぐため、
 * ユーザー1人ずつの処理を「順番に」実行（直列処理）する。
 */
@Service
public class SessionRefreshService {

    private static final Logger log = LoggerFactory.getLogger(SessionRefreshService.class);
    private final UserCredentialRepository userCredentialRepository;
    private final AuthService authService;
    private final LoginProgressListener dummyListener; // ★ LoginProgressListener 型に変更

    public SessionRefreshService(UserCredentialRepository userCredentialRepository, AuthService authService) {
        this.userCredentialRepository = userCredentialRepository;
        this.authService = authService;
        
        // ★★★ エラー（functional interface）の修正 ★★★
        // lambda式ではなく、2つのメソッドを持つインターフェースを
        // 匿名の内部クラスとして正しく実装します。
        this.dummyListener = new ManabaScrapingOrchestrator.LoginProgressListener() {
            
            /**
             * 通常の進捗ログをサーバーコンソールに出力します。
             */
            @Override
            public void onStatusUpdate(String status, String message) {
                // onStatusUpdate が呼ばれたらログに出す
                log.info("[BackgroundRefresh] {} - {}", status, message);
            }

            /**
             * バックグラウンド実行中にMFAが要求された場合、
             * サーバーコンソールにエラーとして記録します。
             */
            @Override
            public void onMfaRequired(String mfaCode, String message) {
                // バックグラウンド実行中にMFAが要求されたら、エラーログとして出す
                log.error("[BackgroundRefresh] !!! MFAが要求されました !!! コード: {}, メッセージ: {}", mfaCode, message);
                // (ここでSlack通知などを将来的に実装することも可能です)
            }
        };
    }

    /**
     * 80分ごと（4,800,000ミリ秒）に実行するスケジューラー。
     * （manabaのセッションが90分で切れるため、その前に実行する）
     * * fixedDelay は、前のタスクが完了してから次のタスクが始まるまでの待機時間。
     * これにより、処理が80分以上かかってもジョブが重複起動しません。
     */
    @Scheduled(fixedDelay = 4800000) 
    public void refreshAllUserSessions() {
        log.info("--- バックグラウンド セッション更新ジョブを開始します (90分有効期限のため) ---");
        
        // 1. パスワードをDBに保存している全ユーザー（rememberMe=trueのユーザー）を取得
        // (★注意: UserCredentialRepository.java に findByEncryptedPasswordIsNotNull() の定義が必要です)
        List<UserCredential> usersToRefresh = userCredentialRepository.findByEncryptedPasswordIsNotNull();
        
        if (usersToRefresh.isEmpty()) {
            log.info("更新対象のユーザー（パスワード保存済）がいません。ジョブを終了します。");
            return;
        }

        log.info("{} 人のユーザーセッションを「順番に」更新します。", usersToRefresh.size());

        // 2. ★★★ 1人ずつ、順番に実行する ★★★
        // (メモリクラッシュを防ぐため、並列実行は絶対にしない)
        for (UserCredential user : usersToRefresh) {
            String userId = user.getUniversityId();
            try {
                log.info("[{}] のセッション更新処理を開始...", userId);
                
                // 3. 既存のAuthService.executeSyncを呼び出す
                // これにより、(A)のセッションが有効ならJsoupで高速に終わり、
                // (A)が切れていたらSelenium (performLogin) で再取得が実行される。
                authService.refreshSessionOnly(userId, dummyListener);

                log.info("[{}] のセッションCookie更新に成功しました。", userId);

            } catch (Exception e) {
                // 一人の更新が失敗しても、次の人のためにループは止めない
                log.error("[{}] のセッション更新中にエラーが発生しました: {}", userId, e.getMessage(), e);
            }
        }
        log.info("--- バックグラウンド セッション更新ジョブが完了しました ---");
    }
}