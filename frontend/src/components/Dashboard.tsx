import { useState } from "react";
import { SyncResponse, ManualClassPayload } from "../types";
import { formatDateTime, summarizeAssignments } from "../utils/format";
import ErrorBanner from "./common/ErrorBanner";
import SuccessBanner from "./common/SuccessBanner";
import NextClassCard from "./NextClassCard";
import TimetableGrid from "./TimetableGrid";
import AssignmentsList from "./AssignmentsList";
import ManualClassForm from "./ManualClassForm";

interface Props {
  session: SyncResponse;
  loading: boolean;
  manualLoading: boolean;
  error?: string | null;
  successMessage?: string | null;
  onRefresh: () => Promise<void>;
  onLogout: () => void;
  onManualClass: (payload: ManualClassPayload) => Promise<void>;
}

const Dashboard = ({
  session,
  loading,
  manualLoading,
  error,
  successMessage,
  onRefresh,
  onLogout,
  onManualClass,
}: Props) => {
  // 時間割は常に表示するためトグルは不要
  const [isEditingTimetable, setIsEditingTimetable] = useState(false);

  const autoCount = session.timetable.filter(
    (course) => course.source === "AUTO"
  ).length;
  const manualCount = session.timetable.length - autoCount;
  const assignmentSummary = summarizeAssignments(session.assignments);

  return (
    <div className="dashboard-container">
      <header className="header">
        <div style={{ display: "flex", alignItems: "center" }}>
          <img
            src="/icons/SchedyNabi_icon.png"
            alt="SchedyNabi logo"
            style={{
              width: "46px",
              height: "46px",
              objectFit: "contain",
              marginRight: "0.75rem",
              marginTop: "-4px",
            }}
          />
          <div>
            <h1>SchedyNabi</h1>
            <p
              className="text-muted"
              style={{ marginTop: "0.2rem", fontSize: "0.85rem" }}
            >
              最終情報取得: {formatDateTime(session.syncedAt)}
            </p>
          </div>
        </div>
        <div className="header-actions">
          <button
            className="button button-secondary"
            onClick={onLogout}
            style={{ padding: "0.3rem 0.85rem" }}
          >
            ログアウト
          </button>
          <button
            className="button button-primary"
            onClick={onRefresh}
            disabled={loading}
            style={{ padding: "0.3rem 0.9rem" }}
          >
            {loading ? "情報取得中..." : "更新"}
          </button>
        </div>
      </header>

      {error ? <ErrorBanner message={error} /> : null}
      {successMessage ? <SuccessBanner message={successMessage} /> : null}

      <div className="dashboard-content">
        <section className="summary-section">
          <div className="card compact-card">
            <h2>次の授業</h2>
            <NextClassCard nextClass={session.nextClass} />
          </div>
          <div className="card compact-card">
            <h2>課題状況</h2>
            <div className="assignment-summary">
              <span className="assignment-count">
                {assignmentSummary.total}
              </span>
              <span className="assignment-label">件の課題</span>
            </div>
            <AssignmentsList assignments={session.assignments} />
          </div>
        </section>

        <section className="timetable-section">
          <div className="card">
            <div className="timetable-header">
              <h2>時間割表</h2>
            </div>

            <div className="timetable-content">
              <TimetableGrid courses={session.timetable} />

              {isEditingTimetable && (
                <div className="manual-class-form">
                  <h3>手動で授業を追加</h3>
                  <ManualClassForm
                    username={session.username}
                    loading={manualLoading}
                    onSubmit={onManualClass}
                  />
                </div>
              )}
            </div>
          </div>
        </section>
      </div>
    </div>
  );
};

export default Dashboard;
