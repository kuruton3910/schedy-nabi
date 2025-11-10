import { CourseEntry } from "../types";
import { formatTime, groupTimetable } from "../utils/format";

const ALL_DAYS = ["月", "火", "水", "木", "金", "土"];

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

  const { grouped, periods: detectedPeriods } = groupTimetable(courses);

  // determine which days/periods to show:
  const showSaturday = courses.some((c) => c.day === "土");
  const days = showSaturday ? ALL_DAYS : ALL_DAYS.slice(0, 5);

  // determine periods: always 1-5; include 6/7 only if present in data
  const maxDetectedPeriod = detectedPeriods.reduce((max, p) => {
    const n = parseInt(p.replace(/[^0-9]/g, "")) || 0;
    return Math.max(max, n);
  }, 0);
  const last = Math.max(5, Math.min(7, maxDetectedPeriod));
  const periods = Array.from({ length: last }, (_, i) => String(i + 1));

  // set a responsive minWidth: period column + day columns
  // remove forced minWidth to allow table to shrink on mobile

  return (
    <div>
      <table className="timetable-table" style={{ tableLayout: "fixed" }}>
        <thead>
          <tr>
            <th></th>
            {days.map((day) => (
              <th key={day}>{day}</th>
            ))}
          </tr>
        </thead>
        <tbody>
          {periods.map((period) => (
            <tr key={period}>
              <th className="timetable-title">{period}</th>
              {days.map((day) => {
                const entries = grouped.get(period)?.get(day) ?? [];
                return (
                  <td key={`${period}-${day}`}>
                    {entries.length === 0 ? (
                      <span className="text-muted">—</span>
                    ) : (
                      <div
                        className="grid"
                        style={{
                          display: "flex",
                          flexWrap: "wrap",
                          gap: "0.4rem",
                        }}
                      >
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
