const hours2 = new Intl.NumberFormat("hu-HU", { minimumFractionDigits: 2, maximumFractionDigits: 2 });
const percent1 = new Intl.NumberFormat("hu-HU", { maximumFractionDigits: 1 });
const dateFmt = new Intl.DateTimeFormat("hu-HU", { year: "numeric", month: "2-digit", day: "2-digit" });
const longDate = new Intl.DateTimeFormat("hu-HU", { year: "numeric", month: "long", day: "numeric", weekday: "long" });

export const HU_MONTHS = [
  "január", "február", "március", "április", "május", "június",
  "július", "augusztus", "szeptember", "október", "november", "december",
];

export function h(value: number | null | undefined): string {
  return `${hours2.format(Number(value ?? 0))} h`;
}

export function signedH(value: number): string {
  return `${value >= 0 ? "+" : ""}${hours2.format(value)} h`;
}

export function pct(value: number): string {
  return `${percent1.format(value)}%`;
}

/** "2026-11-24" vagy ISO időpont -> "2026. 11. 24." */
export function date(value: string | null | undefined): string {
  if (!value) return "—";
  const parsed = value.length === 10 ? new Date(`${value}T12:00:00`) : new Date(value);
  return Number.isNaN(parsed.getTime()) ? value : dateFmt.format(parsed);
}

export function todayLong(): string {
  return longDate.format(new Date());
}

export function relative(value: string | null | undefined): string {
  if (!value) return "—";
  const then = new Date(value).getTime();
  if (Number.isNaN(then)) return value;
  const minutes = Math.round((Date.now() - then) / 60_000);
  if (minutes < 1) return "most";
  if (minutes < 60) return `${minutes} perce`;
  const hoursAgo = Math.round(minutes / 60);
  if (hoursAgo < 24) return `${hoursAgo} órája`;
  const days = Math.round(hoursAgo / 24);
  if (days < 30) return `${days} napja`;
  return date(value);
}

export function monthLabel(yearMonth: string): string {
  const [year, month] = yearMonth.split("-").map(Number);
  return `${year}. ${HU_MONTHS[month - 1]}`;
}

export function shortMonth(yearMonth: string): string {
  const [, month] = yearMonth.split("-").map(Number);
  return HU_MONTHS[month - 1].slice(0, 3);
}

export function yearMonthOf(d: Date): string {
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, "0")}`;
}

/** Az elmúlt 12 hónap (a mostanival kezdve). */
export function lastTwelveMonths(): string[] {
  const now = new Date();
  return Array.from({ length: 12 }, (_, offset) => yearMonthOf(new Date(now.getFullYear(), now.getMonth() - offset, 1)));
}

export function initials(name: string): string {
  return name
    .split(/\s+/)
    .filter(Boolean)
    .map((part) => part[0])
    .slice(0, 2)
    .join("")
    .toUpperCase();
}

/** A mai nap helyi idő szerint, YYYY-MM-DD formában. */
export function localIsoDate(d: Date = new Date()): string {
  return `${yearMonthOf(d)}-${String(d.getDate()).padStart(2, "0")}`;
}
