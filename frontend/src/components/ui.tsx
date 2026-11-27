import type { ReactNode } from "react";

export function PageHeader({ eyebrow, title, subtitle, actions }: {
  eyebrow?: string;
  title: ReactNode;
  subtitle?: ReactNode;
  actions?: ReactNode;
}) {
  return (
    <header className="mb-6 flex flex-wrap items-end justify-between gap-4">
      <div className="min-w-0">
        {eyebrow && <div className="eyebrow">{eyebrow}</div>}
        <h1 className="mt-1 text-2xl font-semibold tracking-tight">{title}</h1>
        {subtitle && <p className="mt-1 text-sm text-slate-500">{subtitle}</p>}
      </div>
      {actions && <div className="flex flex-wrap items-center gap-2">{actions}</div>}
    </header>
  );
}

export function StatCard({ label, value, hint, tone = "default", onClick, active }: {
  label: string;
  value: ReactNode;
  hint?: ReactNode;
  tone?: "default" | "indigo" | "amber" | "emerald" | "rose";
  onClick?: () => void;
  active?: boolean;
}) {
  const accent = {
    default: "bg-slate-400",
    indigo: "bg-indigo-500",
    amber: "bg-amber-500",
    emerald: "bg-emerald-500",
    rose: "bg-rose-500",
  }[tone];
  const content = (
    <>
      <div className="flex items-center gap-2">
        <span className={`size-2 rounded-full ${accent}`} />
        <span className="text-sm text-slate-500">{label}</span>
      </div>
      <div className="mt-2 text-2xl font-semibold tracking-tight">{value}</div>
      {hint && <div className="mt-1 text-xs text-slate-500">{hint}</div>}
    </>
  );
  if (onClick) {
    return (
      <button
        type="button"
        onClick={onClick}
        className={`card p-4 text-left transition hover:border-indigo-300 ${active ? "border-indigo-500 ring-2 ring-indigo-100" : ""}`}
      >
        {content}
      </button>
    );
  }
  return <div className="card p-4">{content}</div>;
}

export function Panel({ title, eyebrow, right, children, className = "" }: {
  title?: ReactNode;
  eyebrow?: string;
  right?: ReactNode;
  children: ReactNode;
  className?: string;
}) {
  return (
    <section className={`card p-5 ${className}`}>
      {(title || right) && (
        <div className="mb-4 flex flex-wrap items-start justify-between gap-3">
          <div>
            {eyebrow && <div className="eyebrow">{eyebrow}</div>}
            {title && <h2 className="text-base font-semibold">{title}</h2>}
          </div>
          {right}
        </div>
      )}
      {children}
    </section>
  );
}

export function ErrorBox({ error, onRetry }: { error: unknown; onRetry?: () => void }) {
  const message = error instanceof Error ? error.message : String(error);
  return (
    <div role="alert" className="flex flex-wrap items-center justify-between gap-3 rounded-xl border border-rose-200 bg-rose-50 px-4 py-3 text-sm text-rose-800">
      <span>{message}</span>
      {onRetry && (
        <button className="btn-ghost" onClick={onRetry}>
          Újra
        </button>
      )}
    </div>
  );
}

export function Spinner({ label = "Betöltés…" }: { label?: string }) {
  return (
    <div className="flex items-center gap-2 text-sm text-slate-500">
      <span className="size-4 animate-spin rounded-full border-2 border-slate-300 border-t-indigo-600" />
      {label}
    </div>
  );
}

export function Empty({ children }: { children: ReactNode }) {
  return <p className="py-6 text-center text-sm text-slate-500">{children}</p>;
}

export function RefreshButton({ onClick, busy }: { onClick: () => void; busy: boolean }) {
  return (
    <button className="btn-ghost" onClick={onClick} disabled={busy}>
      <svg viewBox="0 0 24 24" className={`size-4 ${busy ? "animate-spin" : ""}`} fill="none" stroke="currentColor" strokeWidth="2">
        <path d="M4 4v6h6M20 20v-6h-6M5.5 15a7 7 0 0012.3 2M18.5 9A7 7 0 006.2 7" strokeLinecap="round" strokeLinejoin="round" />
      </svg>
      Frissítés
    </button>
  );
}
