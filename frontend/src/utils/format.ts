import { format, parse, isValid, parseISO } from "date-fns";
import { ja } from "date-fns/locale";
import { AssignmentEntry, CourseEntry, NextClassCard } from "../types";

const DAY_ORDER = ["月", "火", "水", "木", "金", "土", "日"];

export const sortCourses = (courses: CourseEntry[]): CourseEntry[] => {
  return [...courses].sort((a, b) => {
    const dayDiff = DAY_ORDER.indexOf(a.day) - DAY_ORDER.indexOf(b.day);
    if (dayDiff !== 0) {
      return dayDiff;
    }
    const periodA = parseInt(a.period.replace(/[^0-9]/g, "")) || 0;
    const periodB = parseInt(b.period.replace(/[^0-9]/g, "")) || 0;
    return periodA - periodB;
  });
};

export const groupTimetable = (courses: CourseEntry[]) => {
  const grouped = new Map<string, Map<string, CourseEntry[]>>();
  const periods = new Set<string>();

  courses.forEach((course) => {
    const dayKey = course.day;
    const periodKey = course.period;
    periods.add(periodKey);
    if (!grouped.has(periodKey)) {
      grouped.set(periodKey, new Map());
    }
    const periodMap = grouped.get(periodKey)!;
    if (!periodMap.has(dayKey)) {
      periodMap.set(dayKey, []);
    }
    periodMap.get(dayKey)!.push(course);
  });

  const sortedPeriods = Array.from(periods).sort((a, b) => {
    const periodA = parseInt(a.replace(/[^0-9]/g, "")) || 0;
    const periodB = parseInt(b.replace(/[^0-9]/g, "")) || 0;
    return periodA - periodB;
  });

  return { grouped, periods: sortedPeriods };
};

export const formatTime = (time: string | null) => {
  if (!time) {
    return "--:--";
  }
  return time.substring(0, 5);
};

export const parseLocalDateTime = (value: string): Date | null => {
  if (!value) {
    return null;
  }
  const isoCandidate = parseISO(value);
  if (isValid(isoCandidate)) {
    return isoCandidate;
  }
  const parsed = parse(value, "yyyy-MM-dd'T'HH:mm:ss", new Date());
  return isValid(parsed) ? parsed : null;
};

export const formatDateTime = (value: string | null) => {
  const parsed = value ? parseLocalDateTime(value) : null;
  if (!parsed) {
    return "---";
  }
  return format(parsed, "M月d日(E) HH:mm", { locale: ja });
};

export const formatDeadline = (assignment: AssignmentEntry) => {
  if (!assignment.deadline) {
    return "締切 未設定";
  }
  const parsed = parseLocalDateTime(assignment.deadline);
  if (!parsed) {
    return assignment.deadline.replace("T", " ");
  }
  return format(parsed, "M/d HH:mm 締切", { locale: ja });
};

export const isOverdue = (assignment: AssignmentEntry) => {
  if (!assignment.deadline) {
    return false;
  }
  const parsed = parseLocalDateTime(assignment.deadline);
  if (!parsed) {
    return false;
  }
  return parsed.getTime() < Date.now();
};

export const describeDuration = (duration: string | null | undefined) => {
  if (!duration) {
    return "";
  }
  const match = duration.match(/P(?:(\d+)D)?T?(?:(\d+)H)?(?:(\d+)M)?/);
  if (!match) {
    return duration;
  }
  const [, days, hours, minutes] = match.map((value) =>
    value ? parseInt(value, 10) : 0
  );
  const parts: string[] = [];
  if (days) {
    parts.push(`${days}日`);
  }
  if (hours) {
    parts.push(`${hours}時間`);
  }
  if (minutes) {
    parts.push(`${minutes}分`);
  }
  return parts.length > 0 ? parts.join(" ") + "後" : "もうすぐ";
};

export const summarizeNextClass = (nextClass: NextClassCard | null) => {
  if (!nextClass) {
    return {
      title: "次の授業はありません",
      detail: "時間割に授業が見つかりませんでした",
      countdown: "",
    };
  }
  const start = parseLocalDateTime(nextClass.startDateTime);
  const end = parseLocalDateTime(nextClass.endDateTime);
  const startText = start ? format(start, "HH:mm") : "--:--";
  const endText = end ? format(end, "HH:mm") : "--:--";
  return {
    title: `${nextClass.day} ${nextClass.period}限 ${nextClass.courseName}`,
    detail: `${startText} – ${endText} @ ${nextClass.location || "未設定"}`,
    countdown: describeDuration(nextClass.untilStart),
  };
};

export const summarizeAssignments = (assignments: AssignmentEntry[]) => {
  const upcoming = assignments.filter((a) => !isOverdue(a));
  return {
    total: assignments.length,
    overdue: assignments.length - upcoming.length,
    upcoming: upcoming.length,
  };
};
