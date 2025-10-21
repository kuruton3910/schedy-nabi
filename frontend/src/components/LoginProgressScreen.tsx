import { SyncJobResponse } from "../types";

interface LoginProgressScreenProps {
  progress: SyncJobResponse;
}

const statusLabels: Record<string, string> = {
  QUEUED: "待機中",
  IN_PROGRESS: "処理中",
  MFA_REQUIRED: "二段階認証の承認待ち",
  SUCCESS: "完了",
  FAILED: "失敗",
};

const stageMessages: Record<string, string> = {
  QUEUED: "ログインキューに登録しました。しばらくお待ちください。",
  USING_COOKIES: "保存済みのクッキーでログイン状態を確認しています…",
  COOKIE_FALLBACK: "クッキーが無効だったため、再ログインしています…",
  LOGIN_START: "manaba に接続しています…",
  PASSWORD_SUBMITTED: "パスワードを送信しました。サインインを進めています…",
  FETCH_TIMETABLE: "時間割データを取得しています…",
  FETCH_ASSIGNMENTS: "課題データを取得しています…",
  CONFIRM_KMSI: "サインイン状態の維持を確認しています…",
  MFA_REQUIRED: "Authenticator アプリで承認してください。",
};

const LoginProgressScreen = ({ progress }: LoginProgressScreenProps) => {
  const statusLabel = statusLabels[progress.status] ?? "処理中";
  const stageMessage =
    progress.message ??
    (progress.stage ? stageMessages[progress.stage] : undefined) ??
    "manaba から情報を取得しています。しばらくお待ちください。";

  const showMfa = progress.status === "MFA_REQUIRED" && !!progress.mfaCode;
  const mfaMessage =
    progress.mfaMessage ??
    "スマホの Microsoft Authenticator アプリで、同じ番号を選択して承認してください。";

  return (
    <div className="login-progress-screen">
      <div className="login-progress card">
        <h1 className="login-progress__title">manaba にログインしています</h1>
        <div className="login-progress__badge">{statusLabel}</div>
        <div className="login-progress__spinner" aria-hidden />
        <p className="login-progress__message">{stageMessage}</p>

        {showMfa ? (
          <div className="login-progress__mfa-card">
            <p className="login-progress__mfa-heading">
              Authenticator アプリに表示された番号を選択してください
            </p>
            <div className="login-progress__mfa-code" aria-live="polite">
              {progress.mfaCode}
            </div>
            <p className="login-progress__mfa-note">{mfaMessage}</p>
          </div>
        ) : null}

        <p className="login-progress__hint">
          ブラウザはこのまま開いた状態でお待ちください。承認が完了すると自動で進みます。
        </p>
      </div>
    </div>
  );
};

export default LoginProgressScreen;
