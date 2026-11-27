import { useQuery } from "@tanstack/react-query";
import { useEffect, useMemo, useState } from "react";
import { Link } from "react-router";
import { api } from "../api";
import { useSessionInfo } from "../auth";
import { BoardView, GroupedView, IssueTable, type SortState } from "../components/IssueViews";
import {
  GROUP_BY_LABEL,
  groupIssues,
  isDone,
  loadPrefs,
  savePrefs,
  type ColorName,
  type DashboardPrefs,
  type GroupBy,
  type ViewMode,
} from "../components/issueGroups";
import { ErrorBox, Empty, PageHeader, Panel, RefreshButton, Spinner, StatCard } from "../components/ui";
import { h, pct, todayLong } from "../format";
import type { IssuesResponse, WorklogReport } from "../types";

type Filter = "all" | "responsible" | "tester" | "subtask" | "resolvedRecently";

const VIEWS: [ViewMode, string, string][] = [
  ["grouped", "Csoportosítva", "M4 6h16M4 12h16M4 18h10"],
  ["board", "Tábla", "M4 5h4v14H4zM10 5h4v9h-4zM16 5h4v11h-4z"],
  ["list", "Lista", "M8 6h12M8 12h12M8 18h12M4 6h.01M4 12h.01M4 18h.01"],
];

export default function DashboardPage() {
  const { user, jiraBaseUrl } = useSessionInfo();
  const [filter, setFilter] = useState<Filter>("all");
  const [search, setSearch] = useState("");
  const [sort, setSort] = useState<SortState>({ key: "updated", desc: true });
  const [prefs, setPrefs] = useState<DashboardPrefs>(() => loadPrefs(user.key));

  useEffect(() => savePrefs(user.key, prefs), [user.key, prefs]);
  const update = (patch: Partial<DashboardPrefs>) => setPrefs((current) => ({ ...current, ...patch }));

  const issues = useQuery({
    queryKey: ["dashboard-issues"],
    queryFn: () => api<IssuesResponse>("/api/dashboard/issues"),
    refetchInterval: 5 * 60_000,
  });
  const month = useQuery({
    queryKey: ["worklog-report", { view: "month" }],
    queryFn: () => api<WorklogReport>("/api/worklog/report?view=month"),
    staleTime: 2 * 60_000,
  });

  const all = useMemo(() => issues.data?.issues ?? [], [issues.data]);
  const counts = useMemo(
    () => ({
      responsible: all.filter((i) => i.reasons.includes("responsible") && !isDone(i)).length,
      tester: all.filter((i) => i.reasons.includes("tester") && !isDone(i)).length,
      subtask: all.filter((i) => i.reasons.includes("subtask")).length,
      resolvedRecently: all.filter((i) => i.reasons.includes("resolvedRecently")).length,
    }),
    [all],
  );

  const visible = useMemo(() => {
    const needle = search.trim().toLowerCase();
    return all.filter((issue) => {
      if (prefs.hideDone && isDone(issue) && filter !== "resolvedRecently") return false;
      if (filter === "responsible" && !(issue.reasons.includes("responsible") && !isDone(issue))) return false;
      if (filter === "tester" && !(issue.reasons.includes("tester") && !isDone(issue))) return false;
      if (filter === "subtask" && !issue.reasons.includes("subtask")) return false;
      if (filter === "resolvedRecently" && !issue.reasons.includes("resolvedRecently")) return false;
      if (!needle) return true;
      return `${issue.key} ${issue.summary} ${issue.status} ${issue.parentKey ?? ""} ${issue.projectName}`
        .toLowerCase()
        .includes(needle);
    });
  }, [all, filter, search, prefs.hideDone]);

  const groups = useMemo(() => groupIssues(visible, prefs.groupBy), [visible, prefs.groupBy]);

  function setColor(groupKey: string, color: ColorName | null) {
    setPrefs((current) => {
      const byGroup = { ...(current.colors[current.groupBy] ?? {}) };
      if (color) byGroup[groupKey] = color;
      else delete byGroup[groupKey];
      return { ...current, colors: { ...current.colors, [current.groupBy]: byGroup } };
    });
  }

  function toggleCollapsed(groupKey: string) {
    setPrefs((current) => {
      const list = new Set(current.collapsed[current.groupBy] ?? []);
      if (list.has(groupKey)) list.delete(groupKey);
      else list.add(groupKey);
      return { ...current, collapsed: { ...current.collapsed, [current.groupBy]: [...list] } };
    });
  }

  const firstName = user.displayName.trim().split(/\s+/).at(-1) ?? user.displayName;
  const fieldNames = issues.data?.fieldNames;

  return (
    <>
      <PageHeader
        eyebrow={todayLong()}
        title={`Szia, ${firstName}!`}
        subtitle="A neked szóló Jira-jegyek és a havi munkaidőd egy helyen."
        actions={<RefreshButton busy={issues.isFetching} onClick={() => { issues.refetch(); month.refetch(); }} />}
      />

      <div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-4">
        <StatCard
          label={`${fieldNames?.responsible ?? "Munka felelőse"}: nyitott`}
          value={issues.isPending ? "…" : counts.responsible}
          hint="nyitott jegy, ahol te vagy a felelős"
          tone="indigo"
          active={filter === "responsible"}
          onClick={() => setFilter(filter === "responsible" ? "all" : "responsible")}
        />
        <StatCard
          label={`${fieldNames?.tester ?? "Tesztelő"}: teendő`}
          value={issues.isPending ? "…" : counts.tester}
          hint="tesztelésre váró jegy"
          tone="amber"
          active={filter === "tester"}
          onClick={() => setFilter(filter === "tester" ? "all" : "tester")}
        />
        <StatCard
          label="Lezárva az elmúlt 7 napban"
          value={issues.isPending ? "…" : counts.resolvedRecently}
          hint="frissen megoldott jegy"
          tone="emerald"
          active={filter === "resolvedRecently"}
          onClick={() => setFilter(filter === "resolvedRecently" ? "all" : "resolvedRecently")}
        />
        <MonthCard report={month.data} loading={month.isPending} error={month.error} />
      </div>

      <Panel
        className="mt-6"
        eyebrow="Saját jegyek"
        title={issues.data ? `${visible.length} / ${all.length} jegy` : "Jegyek"}
        right={
          <div className="flex flex-wrap items-center gap-2">
            <div className="inline-flex rounded-lg bg-slate-100 p-1">
              {VIEWS.map(([value, label, icon]) => (
                <button
                  key={value}
                  className={`flex items-center gap-1.5 rounded-md px-3 py-1.5 text-sm font-medium ${prefs.view === value ? "bg-white shadow-sm" : "text-slate-500 hover:text-slate-800"}`}
                  onClick={() => update({ view: value })}
                >
                  <svg viewBox="0 0 24 24" className="size-4" fill="none" stroke="currentColor" strokeWidth="2">
                    <path d={icon} strokeLinecap="round" strokeLinejoin="round" />
                  </svg>
                  {label}
                </button>
              ))}
            </div>
            {prefs.view !== "list" && (
              <label className="flex items-center gap-2 text-sm text-slate-600">
                Csoportosítás:
                <select className="input w-auto py-1.5" value={prefs.groupBy} onChange={(event) => update({ groupBy: event.target.value as GroupBy })}>
                  {(Object.keys(GROUP_BY_LABEL) as GroupBy[]).map((key) => (
                    <option key={key} value={key}>
                      {GROUP_BY_LABEL[key]}
                    </option>
                  ))}
                </select>
              </label>
            )}
          </div>
        }
      >
        <div className="mb-4 flex flex-wrap items-center gap-2">
          {(
            [
              ["all", `Összes (${all.length})`],
              ["responsible", `Felelős vagyok (${counts.responsible})`],
              ["tester", `Tesztelő vagyok (${counts.tester})`],
              ["subtask", `Részfeladatok (${counts.subtask})`],
              ["resolvedRecently", `Lezárt, 7 nap (${counts.resolvedRecently})`],
            ] as [Filter, string][]
          ).map(([value, label]) => (
            <button key={value} className={filter === value ? "chip-on" : "chip-off"} onClick={() => setFilter(value)}>
              {label}
            </button>
          ))}
          <label className="ml-1 flex cursor-pointer items-center gap-1.5 text-xs text-slate-600">
            <input type="checkbox" className="accent-indigo-600" checked={prefs.hideDone} onChange={(event) => update({ hideDone: event.target.checked })} />
            Lezártak elrejtése
          </label>
          <input
            className="input ml-auto w-full sm:w-72"
            placeholder="Keresés kulcs, cím vagy státusz alapján"
            value={search}
            onChange={(event) => setSearch(event.target.value)}
          />
        </div>

        {issues.isPending && <Spinner label="Jegyek betöltése a Jirából…" />}
        {issues.error && <ErrorBox error={issues.error} onRetry={() => issues.refetch()} />}
        {issues.data && visible.length === 0 && <Empty>Nincs a szűrésnek megfelelő jegy.</Empty>}
        {issues.data && visible.length > 0 && prefs.view === "grouped" && (
          <GroupedView
            groups={groups}
            prefs={prefs}
            setColor={setColor}
            toggleCollapsed={toggleCollapsed}
            jiraBaseUrl={jiraBaseUrl}
            sort={sort}
            setSort={setSort}
          />
        )}
        {issues.data && visible.length > 0 && prefs.view === "board" && (
          <BoardView groups={groups} prefs={prefs} setColor={setColor} jiraBaseUrl={jiraBaseUrl} sort={sort} />
        )}
        {issues.data && visible.length > 0 && prefs.view === "list" && (
          <div className="-mx-5 border-t border-slate-200">
            <IssueTable issues={visible} jiraBaseUrl={jiraBaseUrl} sort={sort} setSort={setSort} />
          </div>
        )}
        {issues.data && issues.data.total !== null && issues.data.limit !== null && issues.data.total > issues.data.limit && (
          <p className="mt-3 text-xs text-amber-700">
            A Jira {issues.data.total} találatot adott, ebből az első {issues.data.limit} látszik.
          </p>
        )}
        {prefs.view !== "list" && issues.data && visible.length > 0 && (
          <p className="mt-3 text-xs text-slate-500">
            Tipp: a csoportok színe a paletta ikonnal módosítható, a csoportfejlécre kattintva összecsukható. A beállításokat a böngésző megjegyzi.
          </p>
        )}
      </Panel>

      {issues.data?.jql && (
        <details className="mt-4 text-xs text-slate-500">
          <summary className="cursor-pointer">Használt JQL ({issues.data.executionMs ?? "?"} ms)</summary>
          <pre className="mt-2 overflow-x-auto rounded-lg bg-slate-900 p-3 whitespace-pre-wrap text-slate-100">
            {issues.data.jql}
          </pre>
        </details>
      )}
    </>
  );
}

function MonthCard({ report, loading, error }: { report?: WorklogReport; loading: boolean; error: Error | null }) {
  const m = report?.month;
  return (
    <Link to="/worklog" className="card block p-4 transition hover:border-indigo-300">
      <div className="flex items-center justify-between">
        <div className="flex items-center gap-2">
          <span className="size-2 rounded-full bg-sky-500" />
          <span className="text-sm text-slate-500">{m ? `${m.label} – logolás` : "E havi logolás"}</span>
        </div>
        <span className="text-xs font-medium text-indigo-600">Worklog →</span>
      </div>
      {loading && <div className="mt-2 text-2xl font-semibold">…</div>}
      {error && <div className="mt-2 text-sm text-rose-700">{error.message}</div>}
      {m && (
        <>
          <div className="mt-2 text-2xl font-semibold tracking-tight">
            {h(m.loggedHours)} <span className="text-sm font-normal text-slate-500">/ {h(m.expectedHours)}</span>
          </div>
          <div className="mt-2 h-1.5 overflow-hidden rounded-full bg-slate-100">
            <div
              className={`h-full rounded-full ${m.toDateCompletionPercent >= 100 ? "bg-emerald-500" : m.toDateCompletionPercent >= 90 ? "bg-amber-500" : "bg-rose-500"}`}
              style={{ width: `${Math.min(100, m.completionPercent)}%` }}
            />
          </div>
          <div className="mt-1 text-xs text-slate-500">
            {pct(m.completionPercent)} · mai napig {m.differenceToDateHours >= 0 ? "+" : ""}
            {h(m.differenceToDateHours)} · ma {h(report.todayHours)}
          </div>
        </>
      )}
    </Link>
  );
}
