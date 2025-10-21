import { ChangeEvent, FormEvent, useState } from "react";
import { ManualClassPayload } from "../types";

const DAY_OPTIONS = ["月", "火", "水", "木", "金", "土"];
const PERIOD_OPTIONS = ["1", "2", "3", "4", "5", "6", "7" /* 7限までを想定 */];

interface Props {
  username: string;
  loading: boolean;
  onSubmit: (payload: ManualClassPayload) => Promise<void>;
}

type FormState = {
  day: string;
  period: string;
  name: string;
  location: string;
  startTime: string;
  endTime: string;
};

const ManualClassForm = ({ username, loading, onSubmit }: Props) => {
  const [form, setForm] = useState<FormState>({
    day: "月",
    period: "1",
    name: "",
    location: "",
    startTime: "",
    endTime: "",
  });

  const handleChange = <T extends keyof FormState>(
    field: T,
    value: FormState[T]
  ) => {
    setForm((prev) => ({
      ...prev,
      [field]: value,
    }));
  };

  const handleSelectChange =
    (field: keyof FormState) => (event: ChangeEvent<HTMLSelectElement>) => {
      handleChange(field, event.target.value);
    };

  const handleInputChange =
    (field: keyof FormState) => (event: ChangeEvent<HTMLInputElement>) => {
      handleChange(field, event.target.value);
    };

  const handleSubmit = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    await onSubmit({
      username,
      day: form.day,
      period: form.period,
      name: form.name,
      location: form.location || undefined,
      startTime: form.startTime || undefined,
      endTime: form.endTime || undefined,
    });
    setForm((prev) => ({ ...prev, name: "", location: "" }));
  };

  return (
    <form onSubmit={handleSubmit} className="grid" style={{ gap: "1rem" }}>
      <div
        className="grid"
        style={{
          gridTemplateColumns: "repeat(2, minmax(120px, 1fr))",
          gap: "0.75rem",
        }}
      >
        <div className="form-field">
          <label htmlFor="day">曜日</label>
          <select
            id="day"
            value={form.day}
            onChange={handleSelectChange("day")}
          >
            {DAY_OPTIONS.map((option) => (
              <option key={option} value={option}>
                {option}
              </option>
            ))}
          </select>
        </div>
        <div className="form-field">
          <label htmlFor="period">時限</label>
          <select
            id="period"
            value={form.period}
            onChange={handleSelectChange("period")}
          >
            {PERIOD_OPTIONS.map((option) => (
              <option key={option} value={option}>
                {option}限
              </option>
            ))}
          </select>
        </div>
      </div>
      <div className="form-field">
        <label htmlFor="name">授業名</label>
        <input
          id="name"
          value={form.name}
          onChange={handleInputChange("name")}
          placeholder="データサイエンス演習"
          required
        />
      </div>
      <div className="form-field">
        <label htmlFor="location">教室 (任意)</label>
        <input
          id="location"
          value={form.location}
          onChange={handleInputChange("location")}
          placeholder="AS251"
        />
      </div>
      <div
        className="grid"
        style={{
          gridTemplateColumns: "repeat(2, minmax(140px, 1fr))",
          gap: "0.75rem",
        }}
      >
        <div className="form-field">
          <label htmlFor="start">開始時刻 (任意)</label>
          <input
            id="start"
            type="time"
            value={form.startTime}
            onChange={handleInputChange("startTime")}
          />
        </div>
        <div className="form-field">
          <label htmlFor="end">終了時刻 (任意)</label>
          <input
            id="end"
            type="time"
            value={form.endTime}
            onChange={handleInputChange("endTime")}
          />
        </div>
      </div>
      <div className="manual-form-actions">
        <button
          type="submit"
          className="button button-primary"
          disabled={loading}
        >
          {loading ? "追加中..." : "手動授業を追加"}
        </button>
      </div>
    </form>
  );
};

export default ManualClassForm;
