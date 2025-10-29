// src/App.tsx

import { useState, useEffect, useCallback } from "react";
import LoginForm from "./components/LoginForm";
import Dashboard from "./components/Dashboard";
import LoginProgressScreen from "./components/LoginProgressScreen";
import WelcomePage from "./components/WelcomePage"; // WelcomePageもimport
import {
  LoginPayload,
  SyncResponse,
  SyncJobResponse,
  ManualClassPayload,
} from "./types"; // 型定義をimport
import {
  sync,
  invalidateToken,
  addManualClass,
  fetchTimetable,
} from "./api/client"; // APIクライアントをimport
import getMockSession from "./mock/mockData";

// localStorageに保存するキー
const STORAGE_KEY_USER_ID = "schedyNabiUserId";

function App() {
  const [showWelcome, setShowWelcome] = useState(true); // Welcome画面の表示状態
  const [session, setSession] = useState<SyncResponse | null>(null);
  const [loading, setLoading] = useState(false); // 通常のローディング状態
  const [autoSyncLoading, setAutoSyncLoading] = useState(true); // ★ 自動同期専用のローディング状態
  const [manualLoading, setManualLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [successMessage, setSuccessMessage] = useState<string | null>(null);
  const [loginProgress, setLoginProgress] = useState<SyncJobResponse | null>(
    null
  );
  const [statusMessage, setStatusMessage] = useState("");

  const API_BASE_URL =
    import.meta.env.VITE_API_BASE_URL || "http://localhost:8080";

  // --- 自動同期処理 ---
  useEffect(() => {
    const attemptAutoSync = async () => {
      const storedUserId = localStorage.getItem(STORAGE_KEY_USER_ID);
      if (storedUserId) {
        setShowWelcome(false); // IDがあればWelcomeは表示しない
        setLoading(true); // 自動同期中を示す
        setStatusMessage(
          `保存されたID (${storedUserId}) で自動同期を試みています...`
        );
        try {
          // ★ rememberMe を true に修正 ★
          const response = await sync(
            { username: storedUserId, password: "", rememberMe: true }, // ← ここを true に変更！
            (progress) => setLoginProgress(progress)
          );
          setSession(response);
          showSuccess("自動同期に成功しました。");
        } catch (err: any) {
          // Cookieが無効などの理由で失敗した場合
          console.error("自動同期失敗:", err);
          setError("自動同期に失敗しました。再度ログインしてください。");
          localStorage.removeItem(STORAGE_KEY_USER_ID); // 失敗したらIDも消す
        } finally {
          setLoading(false);
          setLoginProgress(null);
        }
      } else {
        // IDがなければWelcomeを表示するか、ログインフォームへ
        setShowWelcome(true); // 必要に応じて調整
      }
      setAutoSyncLoading(false); // ★ 自動同期処理（または不要確認）が完了
    };

    attemptAutoSync();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []); // 初回マウント時にのみ実行

  // --- ログイン処理 (成功時にIDを保存) ---
  const handleLogin = async (payload: LoginPayload) => {
    setLoading(true);
    setError(null);
    setLoginProgress(null); // 開始時にクリア
    try {
      const response = await sync(payload, (progress) => {
        setLoginProgress(progress);
      });
      setSession(response);
      // ★ ログイン成功時にIDをlocalStorageに保存
      if (response.username) {
        // usernameが返ってくる想定
        localStorage.setItem(STORAGE_KEY_USER_ID, response.username);
      }
      showSuccess("manabaからの情報取得が完了しました。");
    } catch (err: any) {
      console.error("ログイン/同期エラー:", err);
      setError(err.message || "不明なエラーが発生しました。");
      // 必要であればここで invalidateToken や localStorage.removeItem を呼ぶ
    } finally {
      setLoading(false);
      setLoginProgress(null); // 終了時にクリア
    }
  };

  // --- モック（デモ）ログイン ---
  const handleDemoLogin = async () => {
    // 即座にモックセッションを作成して表示する
    const mock = getMockSession();
    setSession(mock);
    // demoユーザーとして保存しておく（自動同期を試したい場合）
    localStorage.setItem(STORAGE_KEY_USER_ID, mock.username);
    showSuccess("デモモードでダッシュボードを表示しています。");
  };

  // --- ログアウト処理 (IDを削除) ---
  const handleLogout = async () => {
    if (session) {
      try {
        await invalidateToken(session.username); // サーバー側トークン無効化 (もしあれば)
      } catch (err) {
        console.error("トークン無効化エラー:", err);
      }
    }
    setSession(null);
    setError(null);
    setSuccessMessage(null);
    // ★ ログアウト時にIDをlocalStorageから削除
    localStorage.removeItem(STORAGE_KEY_USER_ID);
    setShowWelcome(false); // ログアウト後はログインフォーム表示
  };

  // --- 成功メッセージ表示用 ---
  const showSuccess = useCallback((message: string) => {
    setSuccessMessage(message);
    window.setTimeout(() => setSuccessMessage(null), 4000);
  }, []);

  // --- (handleRefresh, handleManualClass は変更なしのため省略) ---
  const handleRefresh = async () => {
    /* ... */
  };
  const handleManualClass = async (payload: ManualClassPayload) => {
    /* ... */
  };

  // --- 画面表示ロジック ---

  // 自動同期が終わるまではローディング表示
  if (autoSyncLoading) {
    return <div className="loading-fullscreen">読み込み中...</div>; // 簡単なローディング表示
  }

  // Welcome画面表示判定
  if (showWelcome && !session) {
    return (
      <WelcomePage
        onGetStarted={() => setShowWelcome(false)}
        onDemo={handleDemoLogin}
      />
    );
  }

  // セッションがあればダッシュボード表示
  if (session) {
    return (
      <Dashboard
        session={session}
        loading={loading} // handleRefresh時のローディング
        manualLoading={manualLoading}
        error={error}
        successMessage={successMessage}
        onRefresh={handleRefresh}
        onLogout={handleLogout}
        onManualClass={handleManualClass}
      />
    );
  }

  // ログイン処理中の進捗画面表示
  if (loginProgress && loading) {
    return <LoginProgressScreen progress={loginProgress} />;
  }

  // 上記以外（IDなし、自動同期失敗など）はログインフォーム表示
  return (
    <main className="container">
      {" "}
      {/* LoginFormを中央寄せなどするための親要素 */}
      <LoginForm
        loading={loading} // handleLogin時のローディング
        error={error}
        onSubmit={handleLogin}
        onDemo={handleDemoLogin}
      />
      {loading && !loginProgress && <p>{statusMessage || "処理中..."}</p>}{" "}
      {/* Job開始直後など */}
    </main>
  );
}

export default App;
