import { FormEvent, useState } from "react";
import { LoginPayload } from "../types";
import ErrorBanner from "./common/ErrorBanner";

interface LoginFormProps {
  loading: boolean;
  error?: string | null;
  onSubmit: (payload: LoginPayload) => Promise<void> | void;
}

const LoginForm = ({ loading, error, onSubmit }: LoginFormProps) => {
  const [form, setForm] = useState<LoginPayload>({
    username: "",
    password: "",
    rememberMe: true,
  });

  const handleChange = (field: keyof LoginPayload, value: string | boolean) => {
    setForm((prev) => ({
      ...prev,
      [field]: value,
    }));
  };

  const handleSubmit = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    await onSubmit(form);
  };

  return (
    <div className="login-wrapper card">
      <h1
        style={{
          marginBottom: "1.5rem",
          fontSize: "1.8rem",
          textAlign: "center",
          color: "var(--primary-dark)",
        }}
      >
        SchedyNabi にログイン
      </h1>
      <p className="text-muted" style={{ marginBottom: "1.5rem" }}>
        大学の学習管理システム（manaba）から時間割・課題情報を取得します。
        <br />
        「ログイン状態を記録する」がオンの場合、この端末のブラウザに暗号化した
        認証トークンを保存し、再同期時に自動利用します（サーバー側には保存しません）。
        <br />
        共有端末ではオフにしてください。
      </p>
      {error ? <ErrorBanner message={error} /> : null}
      <form onSubmit={handleSubmit}>
        <div className="form-field">
          <label htmlFor="username">大学メールアドレス</label>
          <input
            id="username"
            type="email"
            inputMode="email"
            autoComplete="email"
            required
            placeholder="user@ed.ritsumei.ac.jp"
            value={form.username}
            onChange={(event) => handleChange("username", event.target.value)}
          />
        </div>
        <div className="form-field">
          <label htmlFor="password">パスワード</label>
          <input
            id="password"
            type="password"
            autoComplete="current-password"
            required
            placeholder="********"
            value={form.password}
            onChange={(event) => handleChange("password", event.target.value)}
          />
        </div>
        <label className="checkbox-field" htmlFor="remember">
          <input
            id="remember"
            type="checkbox"
            checked={form.rememberMe}
            onChange={(event) =>
              handleChange("rememberMe", event.target.checked)
            }
          />
          ログイン状態を記録する
        </label>
        <button
          type="submit"
          className="button button-primary"
          style={{ width: "100%" }}
          disabled={loading}
        >
          {loading ? "情報取得中..." : "ログインして情報を取得"}
        </button>
      </form>
    </div>
  );
};

export default LoginForm;
