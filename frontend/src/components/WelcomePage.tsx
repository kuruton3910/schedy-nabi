import React from "react";

interface Props {
  onGetStarted: () => void;
  onDemo?: () => void;
}

const WelcomePage = ({ onGetStarted, onDemo }: Props) => {
  return (
    <div className="welcome-container">
      <div className="welcome-card">
        <div className="welcome-header">
          <h1 className="welcome-title">SchedyNabi へようこそ</h1>
          <p className="welcome-subtitle">
            大学の学習管理システムと同期して、時間割と課題を一元管理
          </p>
        </div>

        <div className="welcome-content">
          <div className="feature-section">
            <h2>📅 主な機能</h2>
            <div className="feature-grid">
              <div className="feature-item">
                <div className="feature-icon">🔄</div>
                <h3>自動同期</h3>
                <p>manabaから時間割と課題情報を自動取得</p>
              </div>
              <div className="feature-item">
                <div className="feature-icon">📋</div>
                <h3>時間割管理</h3>
                <p>次の授業や空きコマを一目で確認</p>
              </div>
              <div className="feature-item">
                <div className="feature-icon">⚡</div>
                <h3>課題追跡</h3>
                <p>期限切れや進行中の課題を整理</p>
              </div>
              <div className="feature-item">
                <div className="feature-icon">✏️</div>
                <h3>手動編集</h3>
                <p>授業の手動追加・編集が可能</p>
              </div>
            </div>
          </div>

          <div className="info-section">
            <h2>🔒 プライバシーについて</h2>
            <div className="info-card">
              <p>
                <strong>ログイン情報の取り扱い：</strong>
                あなたの大学アカウント情報は、学習管理システムとの同期にのみ使用されます。
                「ログイン状態を記録する」をチェックした場合、ブラウザのクッキーに保存され、
                次回の利用時に自動でログインできます。
              </p>
              <p>
                <strong>データの保存：</strong>
                取得した時間割と課題情報は、このブラウザ内でのみ管理され、
                外部サーバーに送信されることはありません。
              </p>
            </div>
          </div>

          <div className="usage-section">
            <h2>🚀 使い方</h2>
            <div className="step-list">
              <div className="step-item">
                <span className="step-number">1</span>
                <div>
                  <h4>ログイン</h4>
                  <p>大学のメールアドレスとパスワードを入力</p>
                </div>
              </div>
              <div className="step-item">
                <span className="step-number">2</span>
                <div>
                  <h4>自動同期</h4>
                  <p>manabaから時間割と課題を自動取得</p>
                </div>
              </div>
              <div className="step-item">
                <span className="step-number">3</span>
                <div>
                  <h4>管理・編集</h4>
                  <p>ダッシュボードで情報を確認・編集</p>
                </div>
              </div>
            </div>
          </div>
        </div>

        <div className="welcome-footer">
          <button
            className="button button-primary welcome-button"
            onClick={onGetStarted}
          >
            はじめる
          </button>
          {/* {onDemo ? (
            <button
              className="button button-secondary welcome-button"
              onClick={onDemo}
              style={{ marginLeft: "0.6rem" }}
            >
              デモで開始
            </button>
          ) : null} */}
        </div>
      </div>
    </div>
  );
};

export default WelcomePage;
