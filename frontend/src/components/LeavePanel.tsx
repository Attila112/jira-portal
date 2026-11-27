import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useMemo, useState, type FormEvent } from "react";
import { api, query } from "../api";
import { date, localIsoDate } from "../format";
import type { Leave, LeaveType } from "../types";
import { ErrorBox, Panel } from "./ui";

export const LEAVE_LABEL: Record<LeaveType, string> = {
  VACATION: "Szabadság",
  SICK: "Betegszabadság",
  OTHER: "Egyéb távollét",
};

const LEAVE_COLOR: Record<LeaveType, string> = {
  VACATION: "bg-violet-100 text-violet-800",
  SICK: "bg-rose-100 text-rose-800",
  OTHER: "bg-slate-200 text-slate-700",
};

interface LeaveRange {
  from: string;
  to: string;
  type: LeaveType;
  note: string;
  days: string[];
}

function parse(value: string): Date {
  return new Date(`${value}T12:00:00`);
}

function isWeekend(d: Date) {
  return d.getDay() === 0 || d.getDay() === 6;
}

/** Hétfő–péntek napok a [from, to] intervallumban. */
function weekdaysBetween(from: string, to: string): string[] {
  const result: string[] = [];
  const end = parse(to);
  for (let d = parse(from); d <= end; d.setDate(d.getDate() + 1)) {
    if (!isWeekend(d)) result.push(localIsoDate(d));
    if (result.length > 400) break;
  }
  return result;
}

/** Egymást követő (csak hétvégével megszakított), azonos típusú napok összevonása. */
function toRanges(leaves: Leave[]): LeaveRange[] {
  const ranges: LeaveRange[] = [];
  for (const leave of [...leaves].sort((a, b) => a.date.localeCompare(b.date))) {
    const last = ranges.at(-1);
    if (last && last.type === leave.type && last.note === leave.note) {
      const gap = new Date(parse(last.to));
      let adjacent = false;
      for (let i = 0; i < 4; i++) {
        gap.setDate(gap.getDate() + 1);
        if (localIsoDate(gap) === leave.date) {
          adjacent = true;
          break;
        }
        if (!isWeekend(gap)) break;
      }
      if (adjacent) {
        last.to = leave.date;
        last.days.push(leave.date);
        continue;
      }
    }
    ranges.push({ from: leave.date, to: leave.date, type: leave.type, note: leave.note, days: [leave.date] });
  }
  return ranges;
}

export default function LeavePanel({ user, editable }: { user: string; editable: boolean }) {
  const client = useQueryClient();
  const leaves = useQuery({
    queryKey: ["leaves", user],
    queryFn: () => api<Leave[]>(`/api/worklog/leaves${query({ user })}`),
  });
  const save = useMutation({
    mutationFn: (list: Leave[]) => api<Leave[]>("/api/worklog/leaves", { method: "PUT", body: JSON.stringify(list) }),
    onSuccess: (list) => {
      client.setQueryData(["leaves", user], list);
      client.invalidateQueries({ queryKey: ["worklog-report"] });
      client.invalidateQueries({ queryKey: ["worklog-overview"] });
    },
  });

  const today = localIsoDate();
  const [from, setFrom] = useState(today);
  const [to, setTo] = useState(today);
  const [type, setType] = useState<LeaveType>("VACATION");
  const [note, setNote] = useState("");
  const [showAll, setShowAll] = useState(false);
  const [formError, setFormError] = useState<string | null>(null);

  const list = useMemo(() => leaves.data ?? [], [leaves.data]);
  const ranges = useMemo(() => toRanges(list).reverse(), [list]);
  const year = today.slice(0, 4);
  const yearCounts = useMemo(() => {
    const counts: Record<LeaveType, number> = { VACATION: 0, SICK: 0, OTHER: 0 };
    for (const leave of list) if (leave.date.startsWith(year)) counts[leave.type] += 1;
    return counts;
  }, [list, year]);

  // Alapból a jövőbeli és az elmúlt ~2 hónap tételei látszanak.
  const cutoff = localIsoDate(new Date(Date.now() - 62 * 86_400_000));
  const visible = showAll ? ranges : ranges.filter((range) => range.to >= cutoff);

  function add(event: FormEvent) {
    event.preventDefault();
    setFormError(null);
    const end = to < from ? from : to;
    const days = weekdaysBetween(from, end);
    if (days.length === 0) {
      setFormError("A megadott időszakban nincs hétköznap.");
      return;
    }
    const existing = new Set(list.map((leave) => leave.date));
    const added = days.filter((day) => !existing.has(day)).map((day) => ({ date: day, type, note: note.trim() }));
    if (added.length === 0) {
      setFormError("Ezek a napok már szerepelnek.");
      return;
    }
    save.mutate([...list, ...added], { onSuccess: () => setNote("") });
  }

  function remove(range: LeaveRange) {
    const removed = new Set(range.days);
    save.mutate(list.filter((leave) => !removed.has(leave.date)));
  }

  return (
    <Panel
      eyebrow="Távollétek"
      title={editable ? "Szabadságaim" : "Szabadságok"}
      right={
        <div className="flex flex-wrap gap-2 text-xs">
          {(Object.keys(LEAVE_LABEL) as LeaveType[]).map((key) => (
            <span key={key} className={`rounded-full px-2.5 py-1 font-medium ${LEAVE_COLOR[key]}`}>
              {year}: {yearCounts[key]} nap {LEAVE_LABEL[key].toLowerCase()}
            </span>
          ))}
        </div>
      }
    >
      <p className="mb-4 text-xs text-slate-500">
        A szabadságnapok nem munkanapok: kiesnek a havi keretből és a munkanapi átlagból, a naptárban lilával
        látszanak. Hétvégét és ünnepnapot nem kell megadni.
      </p>

      {editable && (
        <form onSubmit={add} className="mb-5 flex flex-wrap items-end gap-3 rounded-xl bg-slate-50 p-3">
          <label className="flex flex-col text-xs font-medium text-slate-600">
            Kezdete
            <input
              type="date"
              className="input mt-1 w-40 py-1.5"
              value={from}
              required
              onChange={(event) => {
                setFrom(event.target.value);
                if (to < event.target.value) setTo(event.target.value);
              }}
            />
          </label>
          <label className="flex flex-col text-xs font-medium text-slate-600">
            Vége
            <input type="date" className="input mt-1 w-40 py-1.5" value={to} min={from} required onChange={(event) => setTo(event.target.value)} />
          </label>
          <label className="flex flex-col text-xs font-medium text-slate-600">
            Típus
            <select className="input mt-1 w-44 py-1.5" value={type} onChange={(event) => setType(event.target.value as LeaveType)}>
              {(Object.keys(LEAVE_LABEL) as LeaveType[]).map((key) => (
                <option key={key} value={key}>
                  {LEAVE_LABEL[key]}
                </option>
              ))}
            </select>
          </label>
          <label className="flex min-w-40 flex-1 flex-col text-xs font-medium text-slate-600">
            Megjegyzés (nem kötelező)
            <input className="input mt-1 py-1.5" maxLength={200} value={note} onChange={(event) => setNote(event.target.value)} />
          </label>
          <button className="btn-primary" disabled={save.isPending}>
            {save.isPending ? "Mentés…" : "Hozzáadás"}
          </button>
          {formError && <p className="w-full text-xs text-rose-700">{formError}</p>}
        </form>
      )}

      {leaves.error && <ErrorBox error={leaves.error} />}
      {save.error && <ErrorBox error={save.error} />}
      {leaves.data && ranges.length === 0 && <p className="text-sm text-slate-500">Még nincs megadott szabadság.</p>}
      {visible.length > 0 && (
        <ul className="divide-y divide-slate-100">
          {visible.map((range) => (
            <li key={range.from} className={`flex flex-wrap items-center gap-3 py-2 text-sm ${range.to < today ? "text-slate-500" : ""}`}>
              <span className={`rounded px-2 py-0.5 text-xs font-medium ${LEAVE_COLOR[range.type]}`}>{LEAVE_LABEL[range.type]}</span>
              <span className="font-medium">
                {date(range.from)}
                {range.to !== range.from && ` – ${date(range.to)}`}
              </span>
              <span className="text-xs text-slate-500">{range.days.length} munkanap</span>
              {range.note && <span className="text-xs text-slate-500">· {range.note}</span>}
              {editable && (
                <button
                  className="ml-auto text-xs font-medium text-rose-700 hover:underline disabled:opacity-50"
                  disabled={save.isPending}
                  onClick={() => remove(range)}
                >
                  Törlés
                </button>
              )}
            </li>
          ))}
        </ul>
      )}
      {ranges.length > visible.length && (
        <button className="mt-2 text-xs font-medium text-indigo-700 hover:underline" onClick={() => setShowAll(true)}>
          Korábbiak mutatása ({ranges.length - visible.length})
        </button>
      )}
    </Panel>
  );
}
