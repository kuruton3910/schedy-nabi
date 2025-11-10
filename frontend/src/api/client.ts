// src/api/client.ts

import { LoginPayload, SyncJobResponse, SyncResponse } from "../types";

const API_BASE_URL =
  import.meta.env.VITE_API_BASE_URL || "http://localhost:8080";
const API_KEY = import.meta.env.VITE_API_SYNC_KEY || "";

const buildHeaders = (withJson: boolean): HeadersInit => {
  const headers: Record<string, string> = {};
  if (withJson) {
    headers["Content-Type"] = "application/json";
  }
  if (!API_KEY) {
    console.warn(
      "VITE_API_SYNC_KEY が設定されていません。API へのアクセスは拒否されます。"
    );
  } else {
    headers["X-API-Key"] = API_KEY;
  }
  return headers;
};

/**
 * 非同期で同期処理を実行し、進捗をコールバックで通知する新しいsync関数
 * @param payload ログイン情報 (rememberMeを含む)
 * @param onProgress 進捗更新時に呼び出されるコールバック関数
 * @returns 成功時の同期結果 (Cookieは含まない)
 */
export async function sync(
  payload: LoginPayload,
  onProgress: (progress: SyncJobResponse) => void
): Promise<SyncResponse> {
  // --- 1. ジョブを開始して "jobId" を取得 ---
  const startResponse = await fetch(`${API_BASE_URL}/api/sync/start`, {
    method: "POST",
    headers: buildHeaders(true),
    body: JSON.stringify({
      userId: payload.userId,
      username: payload.username,
      password: payload.password,
      // ★ rememberMe も送信するように追加
      rememberMe: payload.rememberMe,
    }),
  });

  if (!startResponse.ok) {
    // 認証失敗(401)か、他のサーバーエラーかを判定
    if (startResponse.status === 401) {
      throw new Error("APIキーが無効です。環境変数を確認してください。");
    }
    throw new Error(
      `サーバーエラー: ジョブの開始に失敗しました (HTTP ${startResponse.status})`
    );
  }

  const { jobId } = await startResponse.json();
  if (!jobId) {
    throw new Error("サーバーから有効なJob IDを取得できませんでした。");
  }

  // --- 2. "jobId" を使ってステータスをポーリング（繰り返し確認） ---
  for (let i = 0; i < 60; i++) {
    // 最大60回 (約2分) 確認
    const statusResponse = await fetch(
      `${API_BASE_URL}/api/sync/status/${jobId}`,
      {
        method: "GET",
        headers: buildHeaders(false),
      }
    );

    if (!statusResponse.ok) {
      // 認証失敗(401)か、ジョブが見つからない(404)か、他のエラーか
      if (statusResponse.status === 401) {
        throw new Error("APIキーが無効です。環境変数を確認してください。");
      } else if (statusResponse.status === 404) {
        throw new Error("指定されたジョブIDが見つかりません。");
      }
      throw new Error(
        `サーバーエラー: ステータスの確認に失敗しました (HTTP ${statusResponse.status})`
      );
    }

    const jobStatus: SyncJobResponse = await statusResponse.json();

    // App.tsxに進捗を通知する
    onProgress(jobStatus);

    if (jobStatus.status === "SUCCESS") {
      // ★ 成功！ resultを返して処理を終了
      // resultにはCookieが含まれていないことを想定
      return jobStatus.result as SyncResponse;
    }

    if (jobStatus.status === "FAILED") {
      // ★ 失敗！ エラーメッセージを投げて処理を終了
      throw new Error(jobStatus.error || "不明なエラーが発生しました。");
    }

    // 2秒待ってから、再度ステータスを確認
    await new Promise((resolve) => setTimeout(resolve, 2000));
  }

  // ループが終わっても完了しなかった場合はタイムアウト
  throw new Error(
    "処理がタイムアウトしました。サーバーからの応答がありません。"
  );
}

// --- 以下の関数は変更なし ---

export async function invalidateToken(username: string) {
  console.log(`Token invalidated for ${username}`);
  // TODO: 必要に応じて、サーバー側にトークン無効化APIを実装し呼び出す
  return Promise.resolve();
}

export async function addManualClass(payload: any) {
  console.log("Adding manual class:", payload);
  // TODO: 必要に応じて、サーバー側に手動クラス追加APIを実装し呼び出す
  return Promise.resolve();
}

export async function fetchTimetable(username: string) {
  console.log(`Fetching timetable for ${username}`);
  // TODO: 必要に応じて、サーバー側に時間割再取得APIを実装し呼び出す
  // (現在の設計ではsyncを再実行することになるかもしれません)
  return Promise.resolve([]);
}
