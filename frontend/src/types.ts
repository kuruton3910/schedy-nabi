export type CourseSource = "AUTO" | "MANUAL";

export interface CourseEntry {
  id: string;
  day: string;
  period: string;
  name: string;
  location: string;
  startTime: string | null;
  endTime: string | null;
  source: CourseSource;
}

export interface AssignmentEntry {
  id: string;
  courseName: string;
  category: string;
  title: string;
  deadline: string | null;
  url: string;
}

export interface NextClassCard {
  courseName: string;
  day: string;
  period: string;
  location: string;
  startDateTime: string;
  endDateTime: string;
  untilStart: string;
}

export interface SyncResponse {
  userId?: string | null;
  username: string;
  syncedAt: string;
  nextClass: NextClassCard | null;
  timetable: CourseEntry[];
  assignments: AssignmentEntry[];
}

export interface LoginPayload {
  userId?: string;
  username: string;
  password: string;
  rememberMe: boolean;
}

export interface TokenResponse {
  token: string;
  expiresAt: string;
}

export interface ManualClassPayload {
  username: string;
  day: string;
  period: string;
  name: string;
  location?: string;
  startTime?: string;
  endTime?: string;
}

export interface ApiError {
  timestamp: string;
  status: number;
  error: string;
  message: string;
  details?: string[];
}

export type SyncJobStatus =
  | "QUEUED"
  | "IN_PROGRESS"
  | "MFA_REQUIRED"
  | "SUCCESS"
  | "FAILED";

export interface SyncJobResponse {
  jobId: string;
  status: SyncJobStatus;
  stage?: string | null;
  message?: string | null;
  mfaCode?: string | null;
  mfaMessage?: string | null;
  updatedAt: string;
  userId?: string | null;
  username?: string | null;
  result?: SyncResponse;
  error?: string | null;
}
