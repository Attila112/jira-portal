import { localIsoDate } from "../format";
import type { Issue, IssueReason } from "../types";

/* ------------------------------------------------------------------ */
/* Színpaletta (a Tailwind miatt minden osztály szó szerint szerepel)  */
/* ------------------------------------------------------------------ */

export type ColorName =
  | "slate" | "blue" | "sky" | "indigo" | "violet" | "teal" | "emerald" | "amber" | "orange" | "rose";

export interface ColorClasses {
  dot: string;
  header: string;
  border: string;
  soft: string;
  label: string;
}

export const PALETTE: Record<ColorName, ColorClasses> = {
  slate: { dot: "bg-slate-500", header: "bg-slate-100 text-slate-800", border: "border-l-slate-400", soft: "bg-slate-50", label: "Szürke" },
  blue: { dot: "bg-blue-500", header: "bg-blue-50 text-blue-900", border: "border-l-blue-500", soft: "bg-blue-50/60", label: "Kék" },
  sky: { dot: "bg-sky-500", header: "bg-sky-50 text-sky-900", border: "border-l-sky-500", soft: "bg-sky-50/60", label: "Világoskék" },
  indigo: { dot: "bg-indigo-500", header: "bg-indigo-50 text-indigo-900", border: "border-l-indigo-500", soft: "bg-indigo-50/60", label: "Indigó" },
  violet: { dot: "bg-violet-500", header: "bg-violet-50 text-violet-900", border: "border-l-violet-500", soft: "bg-violet-50/60", label: "Lila" },
  teal: { dot: "bg-teal-500", header: "bg-teal-50 text-teal-900", border: "border-l-teal-500", soft: "bg-teal-50/60", label: "Türkiz" },
  emerald: { dot: "bg-emerald-500", header: "bg-emerald-50 text-emerald-900", border: "border-l-emerald-500", soft: "bg-emerald-50/60", label: "Zöld" },
  amber: { dot: "bg-amber-500", header: "bg-amber-50 text-amber-900", border: "border-l-amber-500", soft: "bg-amber-50/60", label: "Sárga" },
  orange: { dot: "bg-orange-500", header: "bg-orange-50 text-orange-900", border: "border-l-orange-500", soft: "bg-orange-50/60", label: "Narancs" },
  rose: { dot: "bg-rose-500", header: "bg-rose-50 text-rose-900", border: "border-l-rose-500", soft: "bg-rose-50/60", label: "Piros" },
};

export const COLOR_NAMES = Object.keys(PALETTE) as ColorName[];

/* ------------------------------------------------------------------ */
/* Csoportosítás                                                       */
/* ------------------------------------------------------------------ */

export type GroupBy = "status" | "category" | "role" | "due" | "priority" | "project";

export const GROUP_BY_LABEL: Record<GroupBy, string> = {
  status: "Státusz",
  category: "Állapot (teendő / folyamatban / kész)",
  role: "Szerepem",
  due: "Határidő",
  priority: "Prioritás",
  project: "Projekt",
};

export interface IssueGroup {
  key: string;
  label: string;
  order: number;
  defaultColor: ColorName;
  issues: Issue[];
}

const CATEGORY_RANK: Record<string, number> = { new: 0, indeterminate: 1, done: 2 };

const PRIORITY_RANK: Record<string, number> = {
  kritikus: 0, highest: 0, blocker: 0, critical: 0,
  magas: 1, high: 1, major: 1,
  közepes: 2, medium: 2,
  alacsony: 3, low: 3, minor: 3,
  lowest: 4, trivial: 4,
};

export function priorityRank(priority: string | null): number {
  return PRIORITY_RANK[(priority ?? "").toLowerCase()] ?? 9;
}

export function isDone(issue: Issue) {
  return issue.statusCategory === "done";
}

/** Státusz alapszíne: a név és a Jira státuszkategória alapján. */
function statusColor(issue: Issue): ColorName {
  const name = issue.status.toLowerCase();
  if (issue.statusCategory === "done") return name.includes("sikertelen") ? "rose" : "emerald";
  if (/teszt|test|review|felülvizsg|ellenőr/.test(name)) return "amber";
  if (/vár|wait|blokk|block|függ|hold/.test(name)) return "orange";
  if (issue.statusCategory === "new") return "slate";
  return "blue";
}

const REASON_ORDER: IssueReason[] = ["responsible", "tester", "subtask", "resolvedRecently"];
export const REASON_LABEL: Record<IssueReason, string> = {
  responsible: "Felelős vagyok",
  tester: "Tesztelő vagyok",
  subtask: "Részfeladat",
  resolvedRecently: "Lezárva 7 napon belül",
};
const REASON_COLOR: Record<IssueReason, ColorName> = {
  responsible: "indigo",
  tester: "amber",
  subtask: "slate",
  resolvedRecently: "emerald",
};

function hashColor(value: string): ColorName {
  const choices: ColorName[] = ["blue", "teal", "violet", "sky", "indigo", "orange", "emerald", "rose"];
  let hash = 0;
  for (const char of value) hash = (hash * 31 + char.charCodeAt(0)) | 0;
  return choices[Math.abs(hash) % choices.length];
}

function addDays(base: Date, days: number) {
  const copy = new Date(base);
  copy.setDate(copy.getDate() + days);
  return localIsoDate(copy);
}

interface GroupInfo {
  key: string;
  label: string;
  order: number;
  color: ColorName;
}

function groupOf(issue: Issue, by: GroupBy): GroupInfo {
  switch (by) {
    case "status": {
      const rank = CATEGORY_RANK[issue.statusCategory ?? ""] ?? 1;
      return { key: issue.status, label: issue.status, order: rank, color: statusColor(issue) };
    }
    case "category": {
      const category = issue.statusCategory ?? "indeterminate";
      const label = category === "new" ? "Teendő" : category === "done" ? "Kész" : "Folyamatban";
      const color: ColorName = category === "new" ? "slate" : category === "done" ? "emerald" : "blue";
      return { key: category, label, order: CATEGORY_RANK[category] ?? 1, color };
    }
    case "role": {
      const reason = REASON_ORDER.find((r) => issue.reasons.includes(r)) ?? "subtask";
      return { key: reason, label: REASON_LABEL[reason], order: REASON_ORDER.indexOf(reason), color: REASON_COLOR[reason] };
    }
    case "priority": {
      const rank = priorityRank(issue.priority);
      const color: ColorName = rank === 0 ? "rose" : rank === 1 ? "orange" : rank === 2 ? "amber" : rank <= 4 ? "sky" : "slate";
      return { key: issue.priority ?? "—", label: issue.priority ?? "Nincs prioritás", order: rank, color };
    }
    case "project":
      return { key: issue.project, label: `${issue.project} · ${issue.projectName}`, order: 0, color: hashColor(issue.project) };
    case "due": {
      const today = localIsoDate();
      const due = issue.dueDate;
      if (isDone(issue)) return { key: "done", label: "Lezárva", order: 5, color: "emerald" };
      if (!due) return { key: "none", label: "Nincs határidő", order: 4, color: "slate" };
      if (due < today) return { key: "overdue", label: "Lejárt", order: 0, color: "rose" };
      if (due <= addDays(new Date(), 1)) return { key: "soon", label: "Ma / holnap", order: 1, color: "orange" };
      if (due <= addDays(new Date(), 7)) return { key: "week", label: "7 napon belül", order: 2, color: "amber" };
      return { key: "later", label: "Később", order: 3, color: "sky" };
    }
  }
}

export function groupIssues(issues: Issue[], by: GroupBy): IssueGroup[] {
  const groups = new Map<string, IssueGroup>();
  for (const issue of issues) {
    const info = groupOf(issue, by);
    let group = groups.get(info.key);
    if (!group) {
      group = { key: info.key, label: info.label, order: info.order, defaultColor: info.color, issues: [] };
      groups.set(info.key, group);
    }
    group.issues.push(issue);
  }
  return [...groups.values()].sort((a, b) => a.order - b.order || a.label.localeCompare(b.label, "hu"));
}

/* ------------------------------------------------------------------ */
/* Felhasználói beállítások (böngészőben, felhasználónként)            */
/* ------------------------------------------------------------------ */

export type ViewMode = "grouped" | "board" | "list";

export interface DashboardPrefs {
  view: ViewMode;
  groupBy: GroupBy;
  colors: Partial<Record<GroupBy, Record<string, ColorName>>>;
  collapsed: Partial<Record<GroupBy, string[]>>;
  hideDone: boolean;
}

export const DEFAULT_PREFS: DashboardPrefs = {
  view: "grouped",
  groupBy: "status",
  colors: {},
  collapsed: {},
  hideDone: false,
};

export function loadPrefs(userKey: string): DashboardPrefs {
  try {
    const raw = localStorage.getItem(`jira-portal.dashboard.${userKey}`);
    return raw ? { ...DEFAULT_PREFS, ...(JSON.parse(raw) as Partial<DashboardPrefs>) } : DEFAULT_PREFS;
  } catch {
    return DEFAULT_PREFS;
  }
}

export function savePrefs(userKey: string, prefs: DashboardPrefs) {
  try {
    localStorage.setItem(`jira-portal.dashboard.${userKey}`, JSON.stringify(prefs));
  } catch {
    /* privát mód / tiltott tárhely: nem gond */
  }
}

export function groupColor(prefs: DashboardPrefs, group: IssueGroup): ColorClasses {
  const chosen = prefs.colors[prefs.groupBy]?.[group.key];
  return PALETTE[chosen && chosen in PALETTE ? chosen : group.defaultColor];
}
