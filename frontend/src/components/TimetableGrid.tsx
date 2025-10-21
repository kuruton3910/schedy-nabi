import { CourseEntry } from "../types";
import { formatTime, groupTimetable } from "../utils/format";

const DAYS = ["月", "火", "水", "木", "金", "土"];

interface Props {
  courses: CourseEntry[];
}

const TimetableGrid = ({ courses }: Props) => {
  if (!courses || courses.length === 0) {
    return (
      <p className="text-muted">
        時間割がまだありません。manaba との同期を行ってください。
      </p>
    );
  }

  const { grouped, periods } = groupTimetable(courses);

  return (
    <div style={{ overflowX: "auto" }}>
      <table className="timetable-table">
        <thead>
          <tr>
            <th>時限</th>
            {DAYS.map((day) => (
              <th key={day}>{day}</th>
            ))}
          </tr>
        </thead>
        <tbody>
          {periods.map((period) => (
            <tr key={period}>
              <th className="timetable-title">{period}限</th>
              {DAYS.map((day) => {
                const entries = grouped.get(period)?.get(day) ?? [];
                return (
                  <td key={`${period}-${day}`}>
                    {entries.length === 0 ? (
                      <span className="text-muted">—</span>
                    ) : (
                      <div className="grid" style={{ gap: "0.6rem" }}>
                        {entries.map((entry) => (
                          <div
                            key={entry.id}
                            className={`course-chip${
                              entry.source === "MANUAL" ? " manual" : ""
                            }`}
                          >
                            <span style={{ fontWeight: 700 }}>
                              {entry.name}
                            </span>
                            <span className="text-muted">
                              {entry.location || "教室未設定"}
                            </span>
                            <span className="text-muted">
                              {formatTime(entry.startTime)} –{" "}
                              {formatTime(entry.endTime)}
                            </span>
                          </div>
                        ))}
                      </div>
                    )}
                  </td>
                );
              })}
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
};

export default TimetableGrid;
