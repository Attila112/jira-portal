import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useMemo, useState, type FormEvent, type ReactNode } from "react";
import { useSearchParams } from "react-router";
import { api, query, request } from "../api";
import { useSessionInfo } from "../auth";
import LeavePanel from "../components/LeavePanel";
import { Empty, ErrorBox, PageHeader, Panel, RefreshButton, Spinner, StatCard } from "../components/ui";
import { date, h, initials, lastTwelveMonths, monthLabel, pct, shortMonth, signedH, yearMonthOf } from "../format";
import type { DayTotal, GroupTotal, MonthOverview, MonthStatus, OverviewReport, SavedPerson, WorklogReport } from "../types";

const DAY_PRESETS = [7, 14, 30, 60, 90];

/** A riport paraméterei az URL-ben élnek: újratöltés és link-megosztás után is megmaradnak. */
function useReportParams() {
  const [params, setParams] = useSearchParams();
  const view = params.get("view") === "month" ? "month" : "period";
  const days = Math.min(366, Math.max(1, Number(params.get("days")) || 14));
  const month = params.get("month") ?? yearMonthOf(new Date());
  const projects = params.get("projects") ?? "";
  const user = params.get("user") ?? "";
  const update = (patch: Record<string, string | number | null>) => {
    const next = new URLSearchParams(params);
    for (const [key, value] of Object.entries(patch)) {
      if (value === null || value === "") next.delete(key);
      else next.set(key, String(value));
    }
    setParams(next, { replace: true });
  };
  return { view, days, month, projects, user, update } as const;
}

export default function WorklogPage() {
  const session = useSessionInfo();
  const p = useReportParams();

  const reportParams = {
    user: p.user || null,
    view: p.view,
    days: p.view === "period" ? p.days : null,
    month: p.view === "month" ? p.month : null,
    projects: p.projects || null,
  };
  const report = useQuery({
    queryKey: ["worklog-report", reportParams],
    queryFn: () => api<WorklogReport>(`/api/worklog/report${query(reportParams)}`),
    placeholderData: (previous) => previous,
  });
  const overview = useQuery({
    queryKey: ["worklog-overview", { user: p.user, projects: p.projects }],
    queryFn: () => api<OverviewReport>(`/api/worklog/overview${query({ user: p.user, projects: p.projects })}`),
    staleTime: 5 * 60_000,
  });

  const data = report.data;
  const target = data?.target ?? overview.data?.target;
  const targetName = target?.displayName ?? (p.user || session.user.displayName);
  const [exporting, setExporting] = useState(false);
  const [exportError, setExportError] = useState<string | null>(null);

  async function exportCsv() {
    setExporting(true);
    setExportError(null);
    try {
      const response = await request(`/api/worklog/export${query(reportParams)}`);
      const blob = await response.blob();
      const disposition = response.headers.get("Content-Disposition") ?? "";
      const fileName = /filename="([^"]+)"/.exec(disposition)?.[1] ?? "worklog_export.csv";
      const url = URL.createObjectURL(blob);
      const link = document.createElement("a");
      link.href = url;
      link.download = fileName;
      link.click();
      URL.revokeObjectURL(url);
    } catch (error) {
      setExportError(error instanceof Error ? error.message : "Sikertelen export.");
    } finally {
      setExporting(false);
    }
  }

  return (
    <>
      <PageHeader
        eyebrow="Worklog monitor"
        title={
          <span className="flex items-center gap-3">
            <span className="grid size-10 place-items-center rounded-full bg-indigo-600 text-sm font-semibold text-white">
              {initials(targetName)}
            </span>
            {targetName}
          </span>
        }
        subtitle={
          data
            ? `${data.view === "month" ? "Havi nézet" : "Időszakos nézet"} · ${date(data.startDate)} – ${date(data.endDate)} · ${
                data.projects.length ? data.projects.join(" + ") : "minden projekt"
              }`
            : "Munkaidő-riport a Jira worklogokból"
        }
        actions={
          <>
            <RefreshButton busy={report.isFetching || overview.isFetching} onClick={() => { report.refetch(); overview.refetch(); }} />
            <button className="btn-ghost" onClick={exportCsv} disabled={!data || exporting}>
              {exporting ? "Export…" : "CSV export"}
            </button>
          </>
        }
      />

      {session.user.admin && <AdminTargetPicker current={p.user} self={session.user.username} onSelect={(user) => p.update({ user })} />}

      <Controls params={p} presets={session.projectPresets} />

      {exportError && <div className="mt-4"><ErrorBox error={new Error(exportError)} /></div>}
      {report.error && <div className="mt-4"><ErrorBox error={report.error} onRetry={() => report.refetch()} /></div>}
      {report.isPending && <div className="mt-6"><Spinner label="Worklogok lekérése a Jirából…" /></div>}

      {data && (
        <div className={`mt-6 space-y-6 transition-opacity ${report.isPlaceholderData ? "opacity-60" : ""}`}>
          <div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-4">
            <StatCard label="Összes logolás" value={h(data.summary.totalHours)} hint={`${data.summary.worklogCount} worklog`} tone="indigo" />
            <StatCard
              label="Munkanapi átlag"
              value={data.summary.dailyAverage === null ? "—" : h(data.summary.dailyAverage)}
              hint={
                (data.summary.averageWorkingDays ? `összes óra / ${data.summary.averageWorkingDays} munkanap` : "az időszakban nincs munkanap") +
                (data.summary.leaveDays ? ` · ${data.summary.leaveDays} nap szabadság nélkül` : "")
              }
              tone="emerald"
            />
            <StatCard label="Mai logolás" value={h(data.todayHours)} hint={date(data.today)} tone="amber" />
            <StatCard label="Érintett jegyek" value={data.summary.issueCount} hint="egyedi issue" />
          </div>

          <MonthPanel month={data.month} hoursPerDay={session.hoursPerDay} />

          <LeavePanel user={p.user} editable={data.target.self} />

          <OverviewPanel
            overview={overview.data}
            loading={overview.isPending}
            error={overview.error}
            selected={data.view === "month" ? data.month.yearMonth : null}
            onOpen={(month) => p.update({ view: "month", month })}
          />

          <div className="grid gap-6 xl:grid-cols-2">
            <Panel title="Napi logolás" right={<span className="text-xs text-slate-500">{date(data.startDate)} – {date(data.endDate)}</span>}>
              <DailyChart days={data.summary.daily} hoursPerDay={session.hoursPerDay} />
            </Panel>
            <Panel title={`Naptár – ${data.month.label}`} right={<Legend />}>
              <CalendarHeatmap month={data.month} hoursPerDay={session.hoursPerDay} />
            </Panel>
          </div>

          <div className="grid gap-6 xl:grid-cols-2">
            <Panel title="Projekt bontás">
              <Bars items={data.summary.projects} />
            </Panel>
            <Panel title="Top issue-k">
              <Bars items={data.summary.issues.slice(0, 10)} showKey jiraBaseUrl={session.jiraBaseUrl} />
            </Panel>
          </div>

          <WorklogTable report={data} jiraBaseUrl={session.jiraBaseUrl} />

          <details className="text-xs text-slate-500">
            <summary className="cursor-pointer">Technikai részletek és futásidők</summary>
            <pre className="mt-2 overflow-x-auto rounded-lg bg-slate-900 p-3 text-slate-100">
              {JSON.stringify(data.diagnostics, null, 2)}
            </pre>
          </details>
        </div>
      )}
    </>
  );
}

/* ------------------------------------------------------------------ */
/* Vezérlők                                                            */
/* ------------------------------------------------------------------ */

function Controls({ params: p, presets }: { params: ReturnType<typeof useReportParams>; presets: { label: string; projects: string }[] }) {
  const months = lastTwelveMonths();
  const monthIndex = months.indexOf(p.month);
  return (
    <div className="card flex flex-wrap items-end gap-x-8 gap-y-4 p-4">
      <div>
        <div className="eyebrow mb-1.5">Nézet</div>
        <div className="inline-flex rounded-lg bg-slate-100 p-1">
          {(["period", "month"] as const).map((view) => (
            <button
              key={view}
              className={`rounded-md px-3 py-1.5 text-sm font-medium ${p.view === view ? "bg-white shadow-sm" : "text-slate-500"}`}
              onClick={() => p.update({ view })}
            >
              {view === "period" ? "Időszakos" : "Havi"}
            </button>
          ))}
        </div>
      </div>

      {p.view === "period" ? (
        <div>
          <div className="eyebrow mb-1.5">Időszak (nap)</div>
          <div className="flex flex-wrap items-center gap-2">
            {DAY_PRESETS.map((days) => (
              <button key={days} className={p.days === days ? "chip-on" : "chip-off"} onClick={() => p.update({ days })}>
                {days}
              </button>
            ))}
            <input
              type="number"
              min={1}
              max={366}
              className="input w-20 py-1"
              value={p.days}
              onChange={(event) => p.update({ days: Math.min(366, Math.max(1, Number(event.target.value) || 1)) })}
            />
          </div>
        </div>
      ) : (
        <div>
          <div className="eyebrow mb-1.5">Hónap</div>
          <div className="flex items-center gap-2">
            <button className="btn-ghost px-2 py-1" disabled={monthIndex >= months.length - 1} onClick={() => p.update({ month: months[monthIndex + 1] })}>
              ‹
            </button>
            <select className="input w-44 py-1" value={p.month} onChange={(event) => p.update({ month: event.target.value })}>
              {months.map((month) => (
                <option key={month} value={month}>
                  {monthLabel(month)}
                </option>
              ))}
            </select>
            <button className="btn-ghost px-2 py-1" disabled={monthIndex <= 0} onClick={() => p.update({ month: months[monthIndex - 1] })}>
              ›
            </button>
          </div>
        </div>
      )}

      <div>
        <div className="eyebrow mb-1.5">Projektek</div>
        <div className="flex flex-wrap gap-2">
          {presets.map((preset) => (
            <button
              key={preset.label}
              className={p.projects === preset.projects ? "chip-on" : "chip-off"}
              onClick={() => p.update({ projects: preset.projects })}
            >
              {preset.label}
            </button>
          ))}
        </div>
      </div>
    </div>
  );
}

function AdminTargetPicker({ current, self, onSelect }: { current: string; self: string; onSelect: (user: string) => void }) {
  const client = useQueryClient();
  const people = useQuery({ queryKey: ["saved-people"], queryFn: () => api<SavedPerson[]>("/api/worklog/people") });
  const save = useMutation({
    mutationFn: (list: SavedPerson[]) => api<SavedPerson[]>("/api/worklog/people", { method: "PUT", body: JSON.stringify(list) }),
    onSuccess: (list) => client.setQueryData(["saved-people"], list),
  });
  const [name, setName] = useState("");
  const [username, setUsername] = useState("");
  const list = people.data ?? [];
  const active = current.trim().toLowerCase();

  function add(event: FormEvent) {
    event.preventDefault();
    if (!name.trim() || !username.trim()) return;
    const next = [...list.filter((person) => person.username.toLowerCase() !== username.trim().toLowerCase()),
      { username: username.trim(), displayName: name.trim() }];
    save.mutate(next, {
      onSuccess: () => {
        onSelect(username.trim());
        setName("");
        setUsername("");
      },
    });
  }

  return (
    <div className="card mb-4 p-4">
      <div className="flex flex-wrap items-center justify-between gap-2">
        <div className="eyebrow">Admin · kinek a worklogját nézed?</div>
        {save.error && <span className="text-xs text-rose-700">{save.error.message}</span>}
      </div>
      <div className="mt-3 flex flex-wrap items-center gap-2">
        <button className={!active || active === self.toLowerCase() ? "chip-on" : "chip-off"} onClick={() => onSelect("")}>
          Saját magam
        </button>
        {list.map((person) => (
          <span
            key={person.username}
            className={`${active === person.username.toLowerCase() ? "chip-on" : "chip-off"} inline-flex items-center gap-1 pr-1.5`}
          >
            <button onClick={() => onSelect(person.username)} title={person.username}>
              {person.displayName}
            </button>
            <button
              aria-label={`${person.displayName} törlése`}
              className="rounded-full px-1 opacity-60 hover:opacity-100"
              onClick={() => save.mutate(list.filter((item) => item.username !== person.username))}
            >
              ×
            </button>
          </span>
        ))}
        <form onSubmit={add} className="ml-auto flex flex-wrap gap-2">
          <input className="input w-40 py-1" placeholder="Név" value={name} onChange={(e) => setName(e.target.value)} />
          <input className="input w-56 py-1" placeholder="Jira username / email" value={username} onChange={(e) => setUsername(e.target.value)} />
          <button className="btn-primary py-1" disabled={save.isPending}>
            Mentés és megnyitás
          </button>
        </form>
      </div>
    </div>
  );
}

/* ------------------------------------------------------------------ */
/* Havi állapot és 12 havi áttekintés                                  */
/* ------------------------------------------------------------------ */

function MonthPanel({ month: m, hoursPerDay }: { month: MonthStatus; hoursPerDay: number }) {
  const percent = Math.max(0, m.completionPercent);
  const capped = Math.min(percent, 100);
  const ringColor = m.toDateCompletionPercent >= 100 ? "#10b981" : m.toDateCompletionPercent >= 90 ? "#f59e0b" : "#6366f1";
  return (
    <Panel
      eyebrow="Kiválasztott hónap állapota"
      title={`${m.label} – havi munkaidő`}
      right={<span className="rounded-full bg-slate-100 px-3 py-1 text-xs text-slate-600">{hoursPerDay} óra / munkanap</span>}
    >
      <div className="grid items-center gap-6 lg:grid-cols-[auto_1fr]">
        <div
          className="mx-auto grid size-36 place-items-center rounded-full"
          style={{ background: `conic-gradient(${ringColor} ${capped * 3.6}deg, #e2e8f0 0deg)` }}
        >
          <div className="grid size-28 place-items-center rounded-full bg-white text-center">
            <div>
              <div className="text-2xl font-semibold">{pct(percent)}</div>
              <div className="text-xs text-slate-500">havi keret</div>
            </div>
          </div>
        </div>
        <div>
          <div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-4">
            <MiniStat
              label="Teljes havi keret"
              value={h(m.expectedHours)}
              hint={`${m.workingDays} munkanap${m.leaveDays ? ` (${m.leaveDays} nap szabadság levonva)` : ""}`}
            />
            <MiniStat label="Hónapban logolva" value={h(m.loggedHours)} hint={m.currentMonth ? "hónap elejétől máig" : "a teljes hónapban"} />
            <MiniStat label={m.currentMonth ? "Mai napig elvárt" : "Elvárt"} value={h(m.expectedToDateHours)} hint={`${m.elapsedWorkingDays} eltelt munkanap`} />
            <MiniStat
              label={m.currentMonth ? "Eltérés máig" : "Eltérés"}
              value={<span className={m.differenceToDateHours >= 0 ? "text-emerald-600" : "text-rose-600"}>{signedH(m.differenceToDateHours)}</span>}
              hint={m.currentMonth ? "az időarányos kerethez képest" : "a lezárt hónap keretéhez képest"}
            />
          </div>
          <div className="mt-5">
            <div className="mb-1 flex justify-between text-xs text-slate-500">
              <span>Teljes havi kerethez viszonyítva</span>
              <span className="font-medium text-slate-700">{pct(percent)}</span>
            </div>
            <div className="h-2.5 overflow-hidden rounded-full bg-slate-100">
              <div className="h-full rounded-full bg-indigo-500" style={{ width: `${capped}%` }} />
            </div>
            <p className="mt-2 text-xs text-slate-500">Hátralévő havi keret: {h(m.remainingHours)}</p>
          </div>
        </div>
      </div>
    </Panel>
  );
}

function MiniStat({ label, value, hint }: { label: string; value: ReactNode; hint: string }) {
  return (
    <div className="rounded-xl bg-slate-50 p-3">
      <div className="text-xs text-slate-500">{label}</div>
      <div className="mt-1 text-lg font-semibold">{value}</div>
      <div className="text-[11px] text-slate-500">{hint}</div>
    </div>
  );
}

function OverviewPanel({ overview, loading, error, selected, onOpen }: {
  overview?: OverviewReport;
  loading: boolean;
  error: Error | null;
  selected: string | null;
  onOpen: (month: string) => void;
}) {
  const [showTable, setShowTable] = useState(false);
  const months = overview?.months ?? [];
  const max = Math.max(1, ...months.map((m) => Math.max(m.loggedHours, m.expectedHours)));
  return (
    <Panel
      eyebrow="Elmúlt 12 hónap"
      title="Havi munkaidő és logolás"
      right={
        <div className="flex items-center gap-3 text-xs text-slate-500">
          <span className="flex items-center gap-1"><i className="size-2.5 rounded-sm bg-slate-200" /> keret</span>
          <span className="flex items-center gap-1"><i className="size-2.5 rounded-sm bg-indigo-500" /> logolt</span>
          {months.length > 0 && (
            <button className="btn-ghost py-1" onClick={() => setShowTable(!showTable)}>
              {showTable ? "Grafikon" : "Táblázat"}
            </button>
          )}
        </div>
      }
    >
      {loading && <Spinner label="12 havi összesítő betöltése…" />}
      {error && <ErrorBox error={error} />}
      {months.length > 0 && !showTable && (
        <div className="grid grid-cols-12 gap-1.5 sm:gap-3">
          {months.map((m) => (
            <button
              key={m.yearMonth}
              onClick={() => onOpen(m.yearMonth)}
              title={`${m.label}: ${h(m.loggedHours)} / ${h(m.expectedHours)}`}
              className={`group flex flex-col items-center gap-1 rounded-lg p-1 transition hover:bg-slate-50 ${selected === m.yearMonth ? "bg-indigo-50" : ""}`}
            >
              <div className="flex h-32 items-end gap-0.5 sm:gap-1">
                <i className="w-2 rounded-t bg-slate-200 sm:w-3" style={{ height: `${(m.expectedHours / max) * 100}%` }} />
                <i
                  className={`w-2 rounded-t sm:w-3 ${m.completionPercent >= 100 ? "bg-emerald-500" : "bg-indigo-500"}`}
                  style={{ height: `${(m.loggedHours / max) * 100}%` }}
                />
              </div>
              <span className={`text-[11px] ${m.currentMonth ? "font-semibold text-indigo-700" : "text-slate-500"}`}>{shortMonth(m.yearMonth)}</span>
              <span className="hidden text-[10px] text-slate-400 sm:block">{pct(m.completionPercent)}</span>
            </button>
          ))}
        </div>
      )}
      {months.length > 0 && showTable && <OverviewTable months={months} onOpen={onOpen} />}
    </Panel>
  );
}

function OverviewTable({ months, onOpen }: { months: MonthOverview[]; onOpen: (month: string) => void }) {
  return (
    <div className="-mx-5 overflow-x-auto">
      <table className="w-full min-w-[640px] text-sm">
        <thead>
          <tr className="border-y border-slate-200 bg-slate-50 text-left text-xs text-slate-500">
            <th className="py-2 pr-3 pl-5 font-medium">Hónap</th>
            <th className="px-3 py-2 font-medium">Logolt</th>
            <th className="px-3 py-2 font-medium">Keret</th>
            <th className="px-3 py-2 font-medium">Teljesítés</th>
            <th className="px-3 py-2 font-medium">Szabadság</th>
            <th className="px-3 py-2 font-medium">Worklog</th>
            <th className="px-3 py-2 font-medium">Issue</th>
            <th className="py-2 pr-5 pl-3" />
          </tr>
        </thead>
        <tbody>
          {[...months].reverse().map((m) => (
            <tr key={m.yearMonth} className="border-b border-slate-100">
              <td className="py-2 pr-3 pl-5 font-medium">
                {m.label} {m.currentMonth && <span className="ml-1 rounded bg-indigo-50 px-1.5 text-[11px] text-indigo-700">aktuális</span>}
              </td>
              <td className="px-3 py-2">{h(m.loggedHours)}</td>
              <td className="px-3 py-2">{h(m.expectedHours)}</td>
              <td className="px-3 py-2">
                <span className={`rounded px-1.5 py-0.5 text-xs font-medium ${m.completionPercent >= 100 ? "bg-emerald-50 text-emerald-700" : m.completionPercent >= 80 ? "bg-amber-50 text-amber-800" : "bg-rose-50 text-rose-700"}`}>
                  {pct(m.completionPercent)}
                </span>
              </td>
              <td className="px-3 py-2">{m.leaveDays ? `${m.leaveDays} nap` : "—"}</td>
              <td className="px-3 py-2">{m.worklogCount}</td>
              <td className="px-3 py-2">{m.issueCount}</td>
              <td className="py-2 pr-5 pl-3 text-right">
                <button className="text-xs font-medium text-indigo-700 hover:underline" onClick={() => onOpen(m.yearMonth)}>
                  Megnyitás
                </button>
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}

/* ------------------------------------------------------------------ */
/* Grafikonok                                                          */
/* ------------------------------------------------------------------ */

function barColor(hours: number, hoursPerDay: number) {
  if (hours >= hoursPerDay) return "bg-emerald-500";
  if (hours >= hoursPerDay / 2) return "bg-amber-400";
  return "bg-sky-400";
}

function DailyChart({ days, hoursPerDay }: { days: DayTotal[]; hoursPerDay: number }) {
  if (!days.length) return <Empty>Nincs adat.</Empty>;
  const max = Math.max(hoursPerDay + 1, ...days.map((d) => d.hours));
  const labelEvery = Math.ceil(days.length / 15);
  return (
    <div>
      <div className="relative flex h-52 items-end gap-[2px]">
        <div
          className="pointer-events-none absolute inset-x-0 border-t border-dashed border-slate-300"
          style={{ bottom: `${(hoursPerDay / max) * 100}%` }}
        >
          <span className="absolute -top-4 right-0 text-[10px] text-slate-400">{hoursPerDay} h</span>
        </div>
        {days.map((d) => (
          <div key={d.date} className="flex h-full flex-1 flex-col justify-end" title={`${date(d.date)}: ${h(d.hours)}`}>
            <div
              className={`rounded-t-sm ${d.hours > 0 ? barColor(d.hours, hoursPerDay) : ""} ${d.workday ? "" : "opacity-50"}`}
              style={{ height: `${Math.max(d.hours > 0 ? 2 : 0, (d.hours / max) * 100)}%` }}
            />
            {d.leave ? (
              <div className="h-1.5 rounded-sm bg-violet-400" title="Szabadság" />
            ) : (
              !d.workday && d.hours === 0 && <div className="h-1 rounded-t-sm bg-slate-100" />
            )}
          </div>
        ))}
      </div>
      <div className="mt-1 flex gap-[2px]">
        {days.map((d, index) => (
          <div key={d.date} className="flex-1 text-center text-[10px] text-slate-400">
            {index % labelEvery === 0 ? d.date.slice(5).replace("-", ".") : ""}
          </div>
        ))}
      </div>
    </div>
  );
}

function Legend() {
  return (
    <div className="flex items-center gap-2 text-[11px] text-slate-500">
      <span className="flex items-center gap-1"><i className="size-2.5 rounded-sm bg-sky-100" /> &lt;4</span>
      <span className="flex items-center gap-1"><i className="size-2.5 rounded-sm bg-amber-200" /> 4–8</span>
      <span className="flex items-center gap-1"><i className="size-2.5 rounded-sm bg-emerald-300" /> 8+</span>
      <span className="flex items-center gap-1"><i className="size-2.5 rounded-sm bg-violet-200" /> szabadság</span>
    </div>
  );
}

function CalendarHeatmap({ month, hoursPerDay }: { month: MonthStatus; hoursPerDay: number }) {
  const [year, monthNumber] = month.yearMonth.split("-").map(Number);
  const first = new Date(year, monthNumber - 1, 1);
  const offset = (first.getDay() + 6) % 7;
  const lastDay = new Date(year, monthNumber, 0).getDate();
  const byDay = new Map(month.daily.map((d) => [Number(d.date.slice(8)), d]));
  const cells: (number | null)[] = [...Array(offset).fill(null), ...Array.from({ length: lastDay }, (_, i) => i + 1)];
  return (
    <div>
      <div className="mb-1 grid grid-cols-7 gap-1.5 text-center text-[11px] font-medium text-slate-400">
        {["H", "K", "Sze", "Cs", "P", "Szo", "V"].map((d) => <span key={d}>{d}</span>)}
      </div>
      <div className="grid grid-cols-7 gap-1.5">
        {cells.map((day, index) => {
          if (day === null) return <span key={`e${index}`} />;
          const d = byDay.get(day);
          let tone = "bg-slate-50 text-slate-300"; // jövőbeli nap
          if (d?.leave) {
            tone = "bg-violet-100 text-violet-900 ring-1 ring-violet-300";
          } else if (d) {
            if (d.hours >= hoursPerDay) tone = "bg-emerald-300 text-emerald-950";
            else if (d.hours >= hoursPerDay / 2) tone = "bg-amber-200 text-amber-950";
            else if (d.hours > 0) tone = "bg-sky-100 text-sky-900";
            else tone = d.workday ? "bg-rose-50 text-rose-800 ring-1 ring-rose-200" : "bg-slate-100 text-slate-400";
          }
          return (
            <div key={day} className={`flex h-12 flex-col justify-between rounded-lg p-1.5 ${tone}`} title={d ? `${date(d.date)}: ${h(d.hours)}${d.leave ? " · szabadság" : ""}` : undefined}>
              <span className="text-[11px] font-semibold">{day}</span>
              {d?.leave && <span className="text-right text-[10px] font-medium">Szab.</span>}
              {d && !d.leave && d.hours > 0 && <span className="text-right text-[10px]">{d.hours.toLocaleString("hu-HU", { maximumFractionDigits: 1 })}h</span>}
            </div>
          );
        })}
      </div>
    </div>
  );
}

function Bars({ items, showKey, jiraBaseUrl }: { items: GroupTotal[]; showKey?: boolean; jiraBaseUrl?: string }) {
  if (!items.length) return <Empty>Nincs adat.</Empty>;
  const max = Math.max(1, ...items.map((i) => i.hours));
  return (
    <div className="space-y-3">
      {items.map((item) => (
        <div key={item.key}>
          <div className="mb-1 flex items-baseline justify-between gap-3 text-sm">
            <span className="min-w-0 truncate">
              {showKey && jiraBaseUrl ? (
                <a className="font-medium text-indigo-700 hover:underline" href={`${jiraBaseUrl}/browse/${encodeURIComponent(item.key)}`} target="_blank" rel="noreferrer">
                  {item.key}
                </a>
              ) : (
                <span className="font-medium">{item.key || "—"}</span>
              )}
              {showKey && item.label !== item.key && <span className="ml-2 text-slate-500">{item.label}</span>}
            </span>
            <span className="shrink-0 text-slate-600">{h(item.hours)}</span>
          </div>
          <div className="h-2 overflow-hidden rounded-full bg-slate-100">
            <div className="h-full rounded-full bg-indigo-500" style={{ width: `${(item.hours / max) * 100}%` }} />
          </div>
        </div>
      ))}
    </div>
  );
}

/* ------------------------------------------------------------------ */
/* Részletes worklog táblázat                                          */
/* ------------------------------------------------------------------ */

function WorklogTable({ report, jiraBaseUrl }: { report: WorklogReport; jiraBaseUrl: string }) {
  const [search, setSearch] = useState("");
  const rows = useMemo(() => {
    const needle = search.trim().toLowerCase();
    if (!needle) return report.summary.rows;
    return report.summary.rows.filter((r) =>
      `${r.date} ${r.issue} ${r.issueSummary ?? ""} ${r.project} ${r.comment}`.toLowerCase().includes(needle),
    );
  }, [report, search]);
  return (
    <Panel
      title="Részletes worklogok"
      eyebrow={`${rows.length} worklog · ${report.diagnostics.searchedIssueCount ?? "?"} issue átvizsgálva`}
      right={<input className="input w-full sm:w-72" placeholder="Keresés issue, projekt vagy megjegyzés alapján" value={search} onChange={(e) => setSearch(e.target.value)} />}
    >
      {rows.length === 0 ? (
        <Empty>Nincs worklog.</Empty>
      ) : (
        <div className="-mx-5 max-h-[520px] overflow-auto">
          <table className="w-full min-w-[720px] text-sm">
            <thead className="sticky top-0">
              <tr className="border-y border-slate-200 bg-slate-50 text-left text-xs text-slate-500">
                <th className="py-2 pr-3 pl-5 font-medium">Dátum</th>
                <th className="px-3 py-2 font-medium">Issue</th>
                <th className="px-3 py-2 font-medium">Projekt</th>
                <th className="px-3 py-2 font-medium">Megjegyzés</th>
                <th className="py-2 pr-5 pl-3 text-right font-medium">Óra</th>
              </tr>
            </thead>
            <tbody>
              {rows.map((r) => (
                <tr key={r.worklogId ?? `${r.date}${r.issue}${r.seconds}`} className="border-b border-slate-100 align-top">
                  <td className="py-2 pr-3 pl-5 whitespace-nowrap text-slate-600">{date(r.date)}</td>
                  <td className="px-3 py-2">
                    <a className="font-medium text-indigo-700 hover:underline" href={`${jiraBaseUrl}/browse/${encodeURIComponent(r.issue)}`} target="_blank" rel="noreferrer">
                      {r.issue}
                    </a>
                    {r.issueSummary && <div className="text-[11px] text-slate-500">{r.issueSummary}</div>}
                  </td>
                  <td className="px-3 py-2"><span className="rounded bg-slate-100 px-1.5 py-0.5 text-xs">{r.project}</span></td>
                  <td className="px-3 py-2 text-slate-600">{r.comment || <span className="text-slate-300">—</span>}</td>
                  <td className="py-2 pr-5 pl-3 text-right whitespace-nowrap">{h(r.seconds / 3600)}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </Panel>
  );
}
