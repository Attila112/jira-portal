import { NavLink, Outlet, useNavigate } from "react-router";
import { useLogout, useSessionInfo } from "../auth";
import { initials } from "../format";

export function Logo() {
  return (
    <div className="flex items-center gap-3">
      <div className="grid size-9 place-items-center rounded-xl bg-indigo-600 text-white shadow-sm">
        <svg viewBox="0 0 24 24" className="size-5" fill="none" stroke="currentColor" strokeWidth="2.5">
          <path d="M5 12.5l4.5 4.5L19 7" strokeLinecap="round" strokeLinejoin="round" />
        </svg>
      </div>
      <div className="leading-tight">
        <div className="font-semibold">Jira Portal</div>
        <div className="text-xs text-slate-500">B+N Referencia</div>
      </div>
    </div>
  );
}

const navItems = [
  {
    to: "/",
    label: "Dashboard",
    icon: "M3 12l9-8 9 8M5 10v10h5v-6h4v6h5V10",
  },
  {
    to: "/worklog",
    label: "Worklog monitor",
    icon: "M12 7v5l3 2M21 12a9 9 0 11-18 0 9 9 0 0118 0z",
  },
];

export default function Layout() {
  const { user } = useSessionInfo();
  const logout = useLogout();
  const navigate = useNavigate();

  return (
    <div className="min-h-screen lg:flex">
      <aside className="flex flex-col gap-6 border-b border-slate-200 bg-white p-4 lg:sticky lg:top-0 lg:h-screen lg:w-64 lg:shrink-0 lg:border-r lg:border-b-0 lg:p-5">
        <Logo />
        <nav className="flex gap-1 lg:flex-col">
          {navItems.map((item) => (
            <NavLink
              key={item.to}
              to={item.to}
              end={item.to === "/"}
              className={({ isActive }) =>
                `flex items-center gap-3 rounded-lg px-3 py-2 text-sm font-medium transition ${
                  isActive ? "bg-indigo-50 text-indigo-700" : "text-slate-600 hover:bg-slate-50 hover:text-slate-900"
                }`
              }
            >
              <svg viewBox="0 0 24 24" className="size-5" fill="none" stroke="currentColor" strokeWidth="1.8">
                <path d={item.icon} strokeLinecap="round" strokeLinejoin="round" />
              </svg>
              {item.label}
            </NavLink>
          ))}
        </nav>

        <div className="rounded-xl border border-slate-200 p-3 lg:mt-auto">
          <div className="flex items-center gap-3">
            <div className="grid size-9 shrink-0 place-items-center rounded-full bg-slate-900 text-xs font-semibold text-white">
              {initials(user.displayName)}
            </div>
            <div className="min-w-0">
              <div className="truncate text-sm font-semibold">{user.displayName}</div>
              <div className="truncate text-xs text-slate-500">{user.username}</div>
            </div>
          </div>
          {user.admin && (
            <span className="mt-2 inline-block rounded-full bg-amber-100 px-2 py-0.5 text-[11px] font-medium text-amber-800">
              Adminisztrátor
            </span>
          )}
          <button
            className="btn-ghost mt-3 w-full"
            onClick={async () => {
              await logout();
              navigate("/login", { replace: true });
            }}
          >
            Kijelentkezés
          </button>
        </div>
      </aside>

      <main className="min-w-0 flex-1 p-4 lg:p-8">
        <Outlet />
      </main>
    </div>
  );
}
