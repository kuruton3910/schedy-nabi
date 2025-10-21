import { AssignmentEntry } from "../types";
import { formatDeadline, isOverdue } from "../utils/format";

interface Props {
  assignments: AssignmentEntry[];
}

const AssignmentsList = ({ assignments }: Props) => {
  if (!assignments || assignments.length === 0) {
    return (
      <p className="text-muted">未提出の課題はありません。お疲れさまです！</p>
    );
  }

  const parseDeadline = (deadline?: string | null): number => {
    if (!deadline) {
      return Number.POSITIVE_INFINITY;
    }
    const timestamp = Date.parse(deadline);
    if (Number.isNaN(timestamp)) {
      return Number.POSITIVE_INFINITY;
    }
    return timestamp;
  };

  const sorted = [...assignments].sort((a, b) => {
    const aTime = parseDeadline(a.deadline);
    const bTime = parseDeadline(b.deadline);
    if (aTime === bTime) {
      return a.title.localeCompare(b.title);
    }
    return aTime - bTime;
  });

  return (
    <div className="assignment-list">
      {sorted.map((assignment) => (
        <div
          key={assignment.id}
          className={`assignment-item${
            isOverdue(assignment) ? " overdue" : ""
          }`}
        >
          <div className="assignment-meta">
            <span className="badge">{assignment.category}</span>
            <span>{assignment.courseName}</span>
          </div>
          <h3>{assignment.title}</h3>
          <div className="assignment-meta">
            <span className="deadline-text">{formatDeadline(assignment)}</span>
            {assignment.url ? (
              <a href={assignment.url} target="_blank" rel="noreferrer">
                詳細を開く
              </a>
            ) : null}
          </div>
        </div>
      ))}
    </div>
  );
};

export default AssignmentsList;
