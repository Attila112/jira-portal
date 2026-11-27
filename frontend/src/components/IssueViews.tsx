import { useEffect, useRef, useState } from "react";
import { date, localIsoDate, relative } from "../format";
import type { Issue, IssueReason } from "../types";
import {
  COLOR_NAMES,
  PALETTE,
  groupColor,
  isDone,
  priorityRank,
  type ColorName,
  type DashboardPrefs,
  type IssueGroup,
} from "./issueGroups";

export type SortKey = "key" | "status" | "priority" | "updated" | "dueDate";
export interface SortState {
  key: SortKey;
  desc: boolean;
}

export function sortIssues(issues: Issue[], sort: SortState): Issue[] {
  const value = (issue: Issue): string | number => {
    switch (sort.key) {
      case "key": {
        const [project, number = ""] = issue.key.split("-");
        return `${project}-${number.padStart(8, "0")}`;
      }
      case "status":
        return `${{ new: 0, indeterminate: 1, done: 2 }[issue.statusCategory ?? ""] ?? 3}${issue.status}`;
      case "priority":
        return priorityRank(issue.priority);
      case "dueDate":
        return issue.dueDate ?? "9999";
      default:
        return issue.updated ?? "";
    }
  };
  return [...issues].sort((a, b) => {
    const left = value(a);
    const right = value(b);
    const result = left < right ? -1 : left > right ? 1 : 0;
    return sort.desc ? -result : result;
  });
}

/* ------------------------------------------------------------------ */
/* Apró elemek                                                         */
/* ------------------------------------------------------------------ */

const REASON_BADGE: Record<IssueReason, [string, string]> = {
  responsible: ["Felelős", "bg-indigo-50 text-indigo-700"],
  tester: ["Tesztelő", "bg-amber-50 text-amber-800"],
  resolvedRecently: ["Lezárt 7 napon belül", "bg-emerald-50 text-emerald-700"],
  subtask: ["Részfeladat", "bg-slate-100 text-slate-600"],
};

function ReasonBadges({ reasons }: { reasons: IssueReason[] }) {
  return (
    <div className="flex flex-wrap gap-1">
      {reasons.map((reason) => (
        <span key={reason} className={`rounded px-1.5 py-0.5 text-[11px] font-medium ${REASON_BADGE[reason]?.[1] ?? ""}`}>
          {REASON_BADGE[reason]?.[0] ?? reason}
        </span>
      ))}
    </div>
  );
}

function StatusBadge({ issue }: { issue: Issue }) {
  const cls =
    issue.statusCategory === "done"
      ? "bg-emerald-100 text-emerald-800"
      : issue.statusCategory === "indeterminate"
        ? "bg-sky-100 text-sky-800"
        : "bg-slate-200 text-slate-700";
  return <span className={`rounded-md px-2 py-0.5 text-xs font-medium whitespace-nowrap ${cls}`}>{issue.status}</span>;
}

function PriorityDot({ priority }: { priority: string | null }) {
  const rank = priorityRank(priority);
  const cls = rank === 0 ? "bg-rose-500" : rank === 1 ? "bg-orange-500" : rank === 2 ? "bg-amber-400" : rank <= 4 ? "bg-sky-400" : "bg-slate-300";
  return (
    <span className="inline-flex items-center gap-1.5 whitespace-nowrap text-slate-600">
      <i className={`size-2 rounded-full ${cls}`} />
      {priority ?? "—"}
    </span>
  );
}

function isOverdue(issue: Issue) {
  return !!issue.dueDate && issue.dueDate < localIsoDate() && !isDone(issue);
}

function DueDate({ issue }: { issue: Issue }) {
  if (!issue.dueDate) return <span className="text-slate-400">—</span>;
  const overdue = isOverdue(issue);
  return (
    <span className={overdue ? "font-medium text-rose-700" : "text-slate-600"}>
      {date(issue.dueDate)}
      {overdue && <span className="ml-1 rounded bg-rose-100 px-1 text-[10px]">lejárt</span>}
    </span>
  );
}

function IssueLink({ issue, jiraBaseUrl }: { issue: Issue; jiraBaseUrl: string }) {
  return (
    <a
      href={`${jiraBaseUrl}/browse/${encodeURIComponent(issue.key)}`}
      target="_blank"
      rel="noreferrer"
      className="font-medium text-indigo-700 hover:underline"
      onClick={(event) => event.stopPropagation()}
    >
      {issue.key}
    </a>
  );
}

/* ------------------------------------------------------------------ */
/* Táblázat                                                            */
/* ------------------------------------------------------------------ */

function SortHeader({ label, sortKey, sort, setSort, className = "" }: {
  label: string;
  sortKey: SortKey;
  sort: SortState;
  setSort: (value: SortState) => void;
  className?: string;
}) {
  const active = sort.key === sortKey;
  return (
    <th className={`px-3 py-2 font-medium ${className}`}>
      <button
        className={`inline-flex items-center gap-1 hover:text-slate-900 ${active ? "text-slate-900" : ""}`}
        onClick={() => setSort({ key: sortKey, desc: active ? !sort.desc : sortKey === "updated" })}
      >
        {label}
        <span className="text-[10px]">{active ? (sort.desc ? "▼" : "▲") : ""}</span>
      </button>
    </th>
  );
}

export function IssueTable({ issues, jiraBaseUrl, sort, setSort, accent }: {
  issues: Issue[];
  jiraBaseUrl: string;
  sort: SortState;
  setSort: (value: SortState) => void;
  accent?: string;
}) {
  return (
    <div className="overflow-x-auto">
      <table className="w-full min-w-[920px] table-fixed text-sm">
        {/* Rögzített oszlopszélességek: a csoportok táblázatai így egymás alatt pontosan illeszkednek. */}
        <colgroup>
          <col className="w-36" />
          <col />
          <col className="w-40" />
          <col className="w-28" />
          <col className="w-44" />
          <col className="w-28" />
          <col className="w-40" />
        </colgroup>
        <thead>
          <tr className="border-b border-slate-200 bg-slate-50 text-left text-xs text-slate-500">
            <SortHeader label="Kulcs" sortKey="key" sort={sort} setSort={setSort} className="pl-5" />
            <th className="px-3 py-2 font-medium">Összefoglaló</th>
            <SortHeader label="Státusz" sortKey="status" sort={sort} setSort={setSort} />
            <SortHeader label="Prioritás" sortKey="priority" sort={sort} setSort={setSort} />
            <th className="px-3 py-2 font-medium">Szerep</th>
            <SortHeader label="Frissítve" sortKey="updated" sort={sort} setSort={setSort} />
            <SortHeader label="Határidő" sortKey="dueDate" sort={sort} setSort={setSort} className="pr-5" />
          </tr>
        </thead>
        <tbody>
          {sortIssues(issues, sort).map((issue) => (
            <tr key={issue.key} className={`border-b border-slate-100 align-top hover:bg-slate-50/70 ${accent ? `border-l-4 ${accent}` : ""}`}>
              <td className="py-2.5 pr-3 pl-5 whitespace-nowrap">
                <IssueLink issue={issue} jiraBaseUrl={jiraBaseUrl} />
                <div className="text-[11px] text-slate-500">{issue.issueType}</div>
              </td>
              <td className="px-3 py-2.5">
                <div className={isDone(issue) ? "text-slate-500 line-through decoration-slate-300" : ""}>{issue.summary}</div>
                {issue.parentKey && (
                  <div className="mt-0.5 text-[11px] text-slate-500">
                    ↳ {issue.parentKey} {issue.parentSummary}
                  </div>
                )}
              </td>
              <td className="px-3 py-2.5">
                <StatusBadge issue={issue} />
              </td>
              <td className="px-3 py-2.5">
                <PriorityDot priority={issue.priority} />
              </td>
              <td className="px-3 py-2.5">
                <ReasonBadges reasons={issue.reasons} />
              </td>
              <td className="px-3 py-2.5 whitespace-nowrap text-slate-600" title={date(issue.updated)}>
                {relative(issue.updated)}
              </td>
              <td className="py-2.5 pr-5 pl-3 whitespace-nowrap">
                <DueDate issue={issue} />
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}

/* ------------------------------------------------------------------ */
/* Színválasztó                                                        */
/* ------------------------------------------------------------------ */

function ColorPicker({ value, onChange }: { value: ColorName | null; onChange: (color: ColorName | null) => void }) {
  const [open, setOpen] = useState(false);
  const ref = useRef<HTMLDivElement>(null);
  useEffect(() => {
    if (!open) return;
    const close = (event: MouseEvent) => {
      if (ref.current && !ref.current.contains(event.target as Node)) setOpen(false);
    };
    document.addEventListener("mousedown", close);
    return () => document.removeEventListener("mousedown", close);
  }, [open]);
  return (
    <div className="relative" ref={ref}>
      <button
        type="button"
        title="Csoport színe"
        className="grid size-7 place-items-center rounded-md text-current opacity-60 hover:bg-white/70 hover:opacity-100"
        onClick={(event) => {
          event.stopPropagation();
          setOpen(!open);
        }}
      >
        <svg viewBox="0 0 24 24" className="size-4" fill="none" stroke="currentColor" strokeWidth="2">
          <path d="M12 3a9 9 0 100 18c1.1 0 1.5-.8 1.5-1.5 0-.9-.7-1.2-.7-2 0-.8.6-1.5 1.5-1.5H16a5 5 0 005-5c0-4.4-4-8-9-8z" />
          <circle cx="7.5" cy="11" r="1" fill="currentColor" />
          <circle cx="10" cy="7" r="1" fill="currentColor" />
          <circle cx="14.5" cy="7" r="1" fill="currentColor" />
        </svg>
      </button>
      {open && (
        <div className="absolute right-0 z-20 mt-1 w-48 rounded-xl border border-slate-200 bg-white p-2 shadow-lg" onClick={(e) => e.stopPropagation()}>
          <div className="grid grid-cols-5 gap-1.5">
            {COLOR_NAMES.map((name) => (
              <button
                key={name}
                title={PALETTE[name].label}
                className={`size-7 rounded-full ${PALETTE[name].dot} ${value === name ? "ring-2 ring-slate-900 ring-offset-2" : ""}`}
                onClick={() => {
                  onChange(name);
                  setOpen(false);
                }}
              />
            ))}
          </div>
          <button
            className="mt-2 w-full rounded-md py-1 text-xs text-slate-500 hover:bg-slate-50"
            onClick={() => {
              onChange(null);
              setOpen(false);
            }}
          >
            Alapértelmezett szín
          </button>
        </div>
      )}
    </div>
  );
}

/* ------------------------------------------------------------------ */
/* Csoportosított lista                                                */
/* ------------------------------------------------------------------ */

interface GroupViewProps {
  groups: IssueGroup[];
  prefs: DashboardPrefs;
  setColor: (groupKey: string, color: ColorName | null) => void;
  toggleCollapsed: (groupKey: string) => void;
  jiraBaseUrl: string;
  sort: SortState;
  setSort: (value: SortState) => void;
}

export function GroupedView({ groups, prefs, setColor, toggleCollapsed, jiraBaseUrl, sort, setSort }: GroupViewProps) {
  const collapsed = new Set(prefs.collapsed[prefs.groupBy] ?? []);
  return (
    <div className="space-y-4">
      {groups.map((group) => {
        const color = groupColor(prefs, group);
        const isCollapsed = collapsed.has(group.key);
        const overdue = group.issues.filter(isOverdue).length;
        return (
          <section key={group.key} className={`overflow-hidden rounded-xl border border-slate-200 border-l-4 ${color.border}`}>
            <header
              className={`flex cursor-pointer items-center gap-3 px-4 py-2.5 select-none ${color.header}`}
              onClick={() => toggleCollapsed(group.key)}
            >
              <svg viewBox="0 0 24 24" className={`size-4 transition-transform ${isCollapsed ? "-rotate-90" : ""}`} fill="none" stroke="currentColor" strokeWidth="2.5">
                <path d="M6 9l6 6 6-6" strokeLinecap="round" strokeLinejoin="round" />
              </svg>
              <span className={`size-2.5 rounded-full ${color.dot}`} />
              <h3 className="font-semibold">{group.label}</h3>
              <span className="rounded-full bg-white/70 px-2 py-0.5 text-xs font-semibold">{group.issues.length}</span>
              {overdue > 0 && <span className="rounded-full bg-rose-600 px-2 py-0.5 text-[11px] font-semibold text-white">{overdue} lejárt</span>}
              <div className="ml-auto">
                <ColorPicker value={prefs.colors[prefs.groupBy]?.[group.key] ?? null} onChange={(c) => setColor(group.key, c)} />
              </div>
            </header>
            {!isCollapsed && <IssueTable issues={group.issues} jiraBaseUrl={jiraBaseUrl} sort={sort} setSort={setSort} />}
          </section>
        );
      })}
    </div>
  );
}

/* ------------------------------------------------------------------ */
/* Tábla (kanban) nézet                                                */
/* ------------------------------------------------------------------ */

export function BoardView({ groups, prefs, setColor, jiraBaseUrl, sort }: Omit<GroupViewProps, "toggleCollapsed" | "setSort">) {
  return (
    <div className="-mx-5 overflow-x-auto px-5 pb-2">
      <div className="flex min-w-max items-start gap-4">
        {groups.map((group) => {
          const color = groupColor(prefs, group);
          return (
            <section key={group.key} className={`flex w-72 shrink-0 flex-col rounded-xl ${color.soft} ring-1 ring-slate-200`}>
              <header className={`flex items-center gap-2 rounded-t-xl px-3 py-2.5 ${color.header}`}>
                <span className={`size-2.5 rounded-full ${color.dot}`} />
                <h3 className="truncate text-sm font-semibold">{group.label}</h3>
                <span className="rounded-full bg-white/70 px-2 py-0.5 text-xs font-semibold">{group.issues.length}</span>
                <div className="ml-auto">
                  <ColorPicker value={prefs.colors[prefs.groupBy]?.[group.key] ?? null} onChange={(c) => setColor(group.key, c)} />
                </div>
              </header>
              <div className="flex max-h-[640px] flex-col gap-2 overflow-y-auto p-2">
                {sortIssues(group.issues, sort).map((issue) => (
                  <a
                    key={issue.key}
                    href={`${jiraBaseUrl}/browse/${encodeURIComponent(issue.key)}`}
                    target="_blank"
                    rel="noreferrer"
                    className={`block rounded-lg border border-slate-200 border-l-4 ${color.border} bg-white p-3 shadow-sm transition hover:shadow-md`}
                  >
                    <div className="flex items-center justify-between gap-2 text-xs">
                      <span className="font-semibold text-indigo-700">{issue.key}</span>
                      <PriorityDot priority={issue.priority} />
                    </div>
                    <p className={`mt-1.5 text-sm leading-snug ${isDone(issue) ? "text-slate-500 line-through decoration-slate-300" : "text-slate-900"}`}>
                      {issue.summary}
                    </p>
                    {issue.parentKey && <p className="mt-1 truncate text-[11px] text-slate-500">↳ {issue.parentKey} {issue.parentSummary}</p>}
                    <div className="mt-2 flex flex-wrap items-center gap-1.5">
                      {prefs.groupBy !== "status" && <StatusBadge issue={issue} />}
                      <ReasonBadges reasons={issue.reasons} />
                    </div>
                    <div className="mt-2 flex items-center justify-between text-[11px] text-slate-500">
                      <span>{issue.issueType}</span>
                      {issue.dueDate ? <DueDate issue={issue} /> : <span>{relative(issue.updated)}</span>}
                    </div>
                  </a>
                ))}
              </div>
            </section>
          );
        })}
      </div>
    </div>
  );
}
