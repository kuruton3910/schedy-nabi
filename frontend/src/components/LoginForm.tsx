import { FormEvent, useState } from "react";
import { LoginPayload } from "../types";
import ErrorBanner from "./common/ErrorBanner";

interface LoginFormProps {
  loading: boolean;
  error?: string | null;
  onSubmit: (payload: LoginPayload) => Promise<void> | void;
  onDemo?: () => Promise<void> | void;
}

const LoginForm = ({ loading, error, onSubmit, onDemo }: LoginFormProps) => {
  const [form, setForm] = useState<LoginPayload>({
    username: "",
    password: "",
    rememberMe: true,
  });

  const DOMAIN = "@ed.ritsumei.ac.jp";

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
          fontSize: "1.6rem",
          textAlign: "center",
          color: "var(--primary-dark)",
        }}
      >
        SchedyNabi にログイン
      </h1>
      <p className="text-muted" style={{ marginBottom: "1.5rem" }}>
        大学の学習管理システム（manaba）から時間割・課題情報を取得します。
      </p>
      {error ? <ErrorBanner message={error} /> : null}
      <form onSubmit={handleSubmit}>
        <div className="form-field">
          <label htmlFor="username">大学メールアドレス</label>
          {/* ローカル部分を入力し、ドメインは固定表示するUI */}
          <div className="login-email-input">
            <input
              id="username"
              type="text"
              inputMode="email"
              autoComplete="username"
              required
              placeholder="例: ab123"
              style={{ flex: 1 }}
              value={
                form.username && form.username.endsWith(DOMAIN)
                  ? form.username.slice(0, -DOMAIN.length)
                  : form.username
              }
              onChange={(event) => {
                const v = event.target.value;
                if (v.trim() === "") {
                  handleChange("username", "");
                } else if (v.includes("@")) {
                  // ユーザーがフルアドレスを入力した場合はそれをそのまま使う
                  handleChange("username", v);
                } else {
                  handleChange("username", v + DOMAIN);
                }
              }}
            />
            <span aria-hidden className="login-email-domain">
              {DOMAIN}
            </span>
          </div>
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
        {typeof onDemo === "function" ? (
          <button
            type="button"
            className="button button-secondary"
            style={{ width: "100%", marginTop: "0.6rem" }}
            onClick={() => onDemo()}
            disabled={loading}
          >
            デモで開始
          </button>
        ) : null}
      </form>
    </div>
  );
};

export default LoginForm;
