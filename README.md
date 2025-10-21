# SchedyNabi

大学の学習管理システム（manaba）から時間割と課題情報を取得し、学生向けにダッシュボードを提供するフルスタック Web アプリケーションです。バックエンドは Spring Boot、フロントエンドは Vite + React + TypeScript で構成されています。

---

## プロジェクト概要

- **目的**: 大学ポータルにログインし、授業時間割・課題・次の授業情報を一括取得して表示する。
- **利用想定**: 個人利用を前提。資格情報は利用者自身のブラウザとバックエンドのデータベースで暗号化して管理する。
- **主要機能**:
  - 非同期スクレイピングジョブの実行と進捗ポーリング
  - 時間割／課題情報の整形・保存
  - フロントエンドでのダッシュボード表示、手動授業追加、進捗表示
  - ブラウザでの暗号化キャッシュ保存（IndexedDB + AES-GCM）

---

## システム構成

| レイヤー       | 技術                                                     | 役割                                                               |
| -------------- | -------------------------------------------------------- | ------------------------------------------------------------------ |
| フロントエンド | React 18, Vite, TypeScript                               | ログインフォーム、同期進捗表示、ダッシュボード UI                  |
| バックエンド   | Java 17, Spring Boot 3, Spring Security, Spring Data JPA | API 提供、非同期ジョブ管理、資格情報暗号化、スクレイピング呼び出し |
| スクレイピング | Selenium (ChromeDriver), Jsoup, WebDriverManager         | manaba へのログインとページ解析                                    |
| データベース   | PostgreSQL (Supabase)                                    | ユーザー資格情報とセッション Cookie の保存                         |

---

## バックエンド仕様

### パッケージ構成

- `config` : `SecurityConfig`（Spring Security 設定）、`WebConfig`（CORS 設定）
- `controller` : `SyncController`
- `service` : `JobManagerService`, `AuthService`, `ManabaScrapingOrchestrator`, `ScrapingService`, `EncryptionService`, `UniversityLoginService`
- `dto` : フロントと共有するデータ転送オブジェクト
- `entity` / `repository` : `UserCredential` と `UserCredentialRepository`

### セキュリティ設定

- `SecurityConfig`: `csrf` を無効化しつつ `ApiKeyAuthFilter` で `/api/sync/**` へのアクセスに `X-API-Key` ヘッダーを要求。他のエンドポイントは Spring Security の認証を追加して拡張できる構成。
- `WebConfig`: `http://localhost:5173` からの CORS を許可。メソッドは GET/POST/PUT/DELETE。
- `EncryptionService`: `SECURITY_MASTER_KEY`（32 文字の AES キー）から `SecretKeySpec` を生成し、AES-GCM で資格情報・Cookie を暗号化。

### 非同期ジョブの流れ

1. `POST /api/sync/start` で `JobManagerService` が `LoginJob` を生成し、バックグラウンドで `executeSyncJob` を起動。
2. `AuthService.executeSync` が以下を実行。
   - `UserCredentialRepository` から既存の Cookie を復号して取得。
   - `ManabaScrapingOrchestrator.sync` へ委譲。
   - 新しい Cookie を保存する場合はパスワードと Cookie を暗号化して `user_profiles` テーブルに保存。
3. `GET /api/sync/status/{jobId}` で `LoginJob` の状態（`QUEUED` / `IN_PROGRESS` / `MFA_REQUIRED` / `SUCCESS` / `FAILED`）を照会。成功時は `SyncResult` を返却。

### Scraping Orchestrator

- `ManabaScrapingOrchestrator` は既存 Cookie を優先。失敗時のみ `loginAndScrape` で Selenium により ID/パスワードを使用。
- `ScrapingService` が Jsoup で HTML を解析し、時間割 (`Course`)、課題 (`Assignment`) を抽出。
- `NextClassCard` の計算では授業開始時刻から次の授業を推定し、ISO 形式で返却。

### ドメインモデル

`user_profiles` テーブル (`UserCredential` エンティティ):

| 列名                            | 型              | 説明                               |
| ------------------------------- | --------------- | ---------------------------------- |
| `id`                            | UUID (PK)       | レコード識別子                     |
| `university_id`                 | VARCHAR, UNIQUE | 大学アカウント ID                  |
| `university_password_encrypted` | TEXT            | AES-GCM で暗号化されたパスワード   |
| `session_cookie_encrypted`      | TEXT            | AES-GCM で暗号化された Cookie JSON |

`SyncResult` (レスポンス):

```jsonc
{
  "username": "string",
  "syncedAt": "yyyy-MM-dd'T'HH:mm:ss",
  "timetable": [CourseEntry],
  "assignments": [AssignmentEntry],
  "nextClass": NextClassCard | null,
  "cookies": { "cookieName": "value", ... }
}
```

---

## API 仕様

| メソッド | パス                       | 概要                                                                   | 認証 |
| -------- | -------------------------- | ---------------------------------------------------------------------- | ---- |
| `POST`   | `/api/sync/start`          | 同期ジョブの開始。`{ username, password }` を受け取り `jobId` を返却。 | `X-API-Key` ヘッダー |
| `GET`    | `/api/sync/status/{jobId}` | 指定ジョブの状態と結果 (`SyncResult`) を返却。                         | `X-API-Key` ヘッダー |

### エラー仕様

- 400 Bad Request: 入力不足 (`username` 未指定など)
- 404 Not Found: `jobId` が存在しない
- 500 系: スクレイピングや暗号化での例外。`JobManagerService` が `error` フィールドに例外メッセージを格納

> `SyncResult` からはクッキー情報を除外して返却し、セッション情報はサーバー側（暗号化済み）にのみ保持します。

---

## フロントエンド仕様

- 画面構成
  - **WelcomePage**: 初回案内。`onGetStarted` でログイン画面へ遷移。
  - **LoginForm**: 大学メールアドレスとパスワードを入力。`rememberMe` でブラウザ保存の可否を選択。
  - **LoginProgressScreen**: ジョブ進捗 (`SyncJobResponse`) を段階表示。
  - **Dashboard**: 次の授業カード、課題一覧、時間割表示。手動授業追加フォームを含む。
- データ取得
  - `api/client.ts` の `sync` がバックエンドを呼び出し、ポーリングで成功・失敗を判断。
  - `ManualClassForm` 経由の授業追加や `fetchTimetable` はスタブ実装（API 未実装）。
- ローカルストレージ戦略
  - `utils/storage.ts` が sessionStorage と IndexedDB を併用。
  - Web Crypto API (AES-GCM) で暗号化し、24 時間キャッシュ。
  - Cookie やトークンの保存・削除 API を提供。

---

## 環境設定

### バックエンド環境変数

`backend/src/main/resources/application.properties` から `spring.profiles.active=local` が読み込まれ、`application-local.properties` の設定が利用されます。以下のキーを OS の環境変数または外部シークレットストアで管理してください。

| キー                    | 説明                                                                              |
| ----------------------- | --------------------------------------------------------------------------------- |
| `DATABASE_URL`          | PostgreSQL JDBC URL（例: `jdbc:postgresql://<host>:<port>/<db>?sslmode=require`） |
| `DATABASE_USERNAME`     | DB ユーザー名                                                                     |
| `DATABASE_PASSWORD`     | DB パスワード                                                                     |
| `SECURITY_MASTER_KEY`   | 32 文字のランダムな英数字（AES-256 キー）                                         |
| `SECURITY_SYNC_API_KEY` | `/api/sync/**` を保護するAPIキー。フロントエンドと共有する                       |
| `JWT_SECRET`            | 将来の JWT 署名鍵（現状未使用）                                                   |

> **重要**: 機微情報をレポジトリにコミットしないでください。`application-local.properties` をテンプレート化し、実値は環境変数・シークレットマネージャーで注入してください。

### フロントエンド環境変数

`frontend/.env.local` で API エンドポイントと API キーを指定します。

```env
VITE_API_BASE_URL=http://localhost:8080
VITE_API_SYNC_KEY=your-shared-api-key
```

---

## ローカル開発手順

1. **事前準備**

   - JDK 17 以上
   - Node.js 18 以上 / npm 9 以上
   - Google Chrome (Selenium 用)

2. **バックエンド**

   ```powershell
   cd backend
   .\mvnw spring-boot:run
   ```

   - `spring.jpa.hibernate.ddl-auto=update` により起動時にテーブルが自動作成されます。
   - ログにデバッグ情報（SQL を含む）が出力されるため、機微情報の扱いに注意してください。

3. **フロントエンド**

   ```powershell
   cd frontend
   npm install
   npm run dev
   ```

   - Vite の開発サーバーが `http://localhost:5173` で起動します。

4. **同期テスト**
   - ブラウザでフロントエンドにアクセスし、大学アカウントでログイン。
   - バックエンドのログにスクレイピング進捗が出力されます。

---

## テストとビルド

- バックエンド: `./mvnw test` で単体テスト（現状 `DemoApplicationTests` のみ）。
- フロントエンド: `npm run build` で型チェックとバンドルを実行。必要に応じて `npm run lint` の導入を検討してください。

---

## 運用上の注意

- Selenium 実行にはヘッドレス Chrome が必要です。Render などの PaaS で動作させる場合は十分なディスクと `/tmp` 権限を確保してください。
- `JOB_TTL` は 10 分で、`JobManagerService.cleanupExpiredJobs` がメモリ上のジョブを整理します。長期保存が必要なら永続ストアを検討してください。
- ログには授業名や課題名が出力されます。PII 取り扱いポリシーに従い、必要に応じてマスキングしてください。

---

## セキュリティ運用ガイドライン

- Secrets は環境変数またはシークレットマネージャーで管理し、リポジトリに含めない。
- TLS (HTTPS) 経由でのみ API を公開する。Reverse proxy (NGINX など) で TLS 終端を推奨。
- `/api/sync/**` は現状無認証のため、IP 制限・レートリミット・CAPTCHA 等の導入を検討する。
- `/api/sync/**` 用の API キーを `SECURITY_SYNC_API_KEY`・`VITE_API_SYNC_KEY` で管理し、漏洩時は即時ローテーションする。
- データベースに保存する暗号化キーのローテーション計画を用意する。
- ローカルブラウザに保存されるデータは端末利用者を信頼する前提。共有端末での利用は避けるようユーザーに周知する。

---

## ライセンス

未定義です。社内利用または個人利用ポリシーに従ってください。
