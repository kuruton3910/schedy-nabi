package com.example.demo.service;

/**
 * 非同期スクレイピング処理の進捗状況を通知するためのインターフェース。
 * ManabaScrapingOrchestratorが実装（呼び出し側）に状態を伝えるために使用する。
 */
public interface LoginProgressListener {

    /**
     * 一般的なステータス更新を通知します。
     * @param stage   処理の段階を示す識別子 (例: "LOGIN_START", "FETCH_TIMETABLE")
     * @param message ユーザーに表示するメッセージ
     */
    void onStatusUpdate(String stage, String message);

    /**
     * 多要素認証 (MFA) が必要になったことを通知します。
     * @param code    認証アプリに表示されるコード
     * @param message ユーザーへの指示メッセージ
     */
    void onMfaRequired(String code, String message);
}