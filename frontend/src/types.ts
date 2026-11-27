// A backend válaszainak típusai (hu.bplusn.portal.* rekordok tükre).

export interface PortalUser {
  key: string;
  username: string;
  displayName: string;
  email: string | null;
  admin: boolean;
}

export interface ProjectPreset {
  label: string;
  projects: string;
}

export interface SessionInfo {
  user: PortalUser;
  jiraBaseUrl: string;
  projectPresets: ProjectPreset[];
  hoursPerDay: number;
}

/* ---------- Dashboard ---------- */

export type IssueReason = "responsible" | "tester" | "resolvedRecently" | "subtask";

export interface Issue {
  key: string;
  summary: string;
  project: string;
  projectName: string;
  issueType: string;
  subtask: boolean;
  parentKey: string | null;
  parentSummary: string | null;
  status: string;
  statusCategory: "new" | "indeterminate" | "done" | string | null;
  priority: string | null;
  resolution: string | null;
  assignee: string | null;
  responsible: string[];
  tester: string[];
  created: string | null;
  updated: string | null;
  resolved: string | null;
  dueDate: string | null;
  reasons: IssueReason[];
}

export interface IssuesResponse {
  fieldNames: { responsible: string; tester: string } | null;
  total: number | null;
  limit: number | null;
  issues: Issue[];
  jql: string | null;
  executionMs: number | null;
}

/* ---------- Worklog ---------- */

export interface WorklogRow {
  date: string;
  issue: string;
  issueSummary: string | null;
  project: string;
  seconds: number;
  comment: string;
  worklogId: string;
}

export interface DayTotal {
  date: string;
  hours: number;
  seconds: number;
  workday: boolean;
  leave: boolean;
}

export interface GroupTotal {
  key: string;
  label: string;
  hours: number;
  seconds: number;
  worklogCount: number;
}

export interface Summary {
  totalSeconds: number;
  totalHours: number;
  worklogCount: number;
  issueCount: number;
  dailyAverage: number | null;
  averageWorkingDays: number;
  leaveDays: number;
  daily: DayTotal[];
  projects: GroupTotal[];
  issues: GroupTotal[];
  rows: WorklogRow[];
}

export interface MonthStatus {
  yearMonth: string;
  label: string;
  startDate: string;
  endDate: string;
  currentMonth: boolean;
  workingDays: number;
  elapsedWorkingDays: number;
  leaveDays: number;
  expectedHours: number;
  expectedToDateHours: number;
  loggedHours: number;
  remainingHours: number;
  differenceToDateHours: number;
  completionPercent: number;
  toDateCompletionPercent: number;
  daily: DayTotal[];
}

export interface MonthOverview {
  yearMonth: string;
  label: string;
  loggedHours: number;
  expectedHours: number;
  completionPercent: number;
  differenceHours: number;
  workingDays: number;
  leaveDays: number;
  worklogCount: number;
  issueCount: number;
  currentMonth: boolean;
}

export interface Target {
  username: string;
  displayName: string;
  self: boolean;
}

export interface Diagnostics {
  jql: string | null;
  scope: string | null;
  searchedIssueCount: number | null;
  scannedWorklogCount: number | null;
  jiraTimings: Record<string, number> | null;
  jiraExecutionMs: number | null;
  requestMs: number;
  queriedDays: number;
}

export interface WorklogReport {
  view: "period" | "month";
  startDate: string;
  endDate: string;
  today: string;
  days: number;
  target: Target;
  projects: string[];
  todayHours: number;
  summary: Summary;
  month: MonthStatus;
  diagnostics: Diagnostics;
}

export interface OverviewReport {
  target: Target;
  today: string;
  months: MonthOverview[];
  diagnostics: Diagnostics;
}

export interface SavedPerson {
  username: string;
  displayName: string;
}

export interface AuthOptions {
  jiraOAuth: boolean;
  passwordLogin: boolean;
}

export type LeaveType = "VACATION" | "SICK" | "OTHER";

export interface Leave {
  date: string;
  type: LeaveType;
  note: string;
}
