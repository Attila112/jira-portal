import { useQuery, useQueryClient } from "@tanstack/react-query";
import { useEffect, useState, type FormEvent } from "react";
import { Navigate, useLocation, useNavigate, useSearchParams } from "react-router";
import { api } from "../api";
import { SESSION_KEY, useSession } from "../auth";
import { Logo } from "../components/Layout";
import type { AuthOptions, SessionInfo } from "../types";

export default function LoginPage() {
  const { data: session } = useSession();
  const client = useQueryClient();
  const navigate = useNavigate();
  const location = useLocation();
  const [searchParams] = useSearchParams();
  const [username, setUsername] = useState("");
  const [password, setPassword] = useState("");
  const [error, setError] = useState<string | null>(searchParams.get("error"));
  const [busy, setBusy] = useState(false);
  const [showPassword, setShowPassword] = useState(false);

  const options = useQuery({
    queryKey: ["auth-options"],
    queryFn: () => api<AuthOptions>("/api/auth/options"),
    staleTime: Infinity,
  });
  const oauth = options.data?.jiraOAuth ?? false;
  const passwordLogin = options.data?.passwordLogin ?? true;
  const passwordVisible = passwordLogin && (!oauth || showPassword);

  const from = (location.state as { from?: string } | null)?.from ?? "/";

  // A CSRF-cookie beszerzése a belépés előtt.
  useEffect(() => {
    api("/api/auth/csrf").catch(() => undefined);
  }, []);

  if (session) return <Navigate to={from} replace />;

  async function submit(event: FormEvent) {
    event.preventDefault();
    if (busy) return;
    setBusy(true);
    setError(null);
    try {
      const info = await api<SessionInfo>("/api/auth/login", {
        method: "POST",
        body: JSON.stringify({ username: username.trim(), password }),
      });
      setPassword("");
      client.setQueryData(SESSION_KEY, info);
      navigate(from, { replace: true });
    } catch (failure) {
      setError(failure instanceof Error ? failure.message : "A belépés sikertelen.");
    } finally {
      setBusy(false);
    }
  }

  return (
    <main className="grid min-h-screen place-items-center bg-gradient-to-br from-indigo-50 via-slate-50 to-sky-50 p-4">
      <div className="card w-full max-w-md p-8">
        <Logo />
        <h1 className="mt-8 text-2xl font-semibold">Bejelentkezés</h1>
        <p className="mt-2 text-sm text-slate-600">
          {oauth ? "Lépj be a céges fiókoddal a Jirán keresztül." : "Használd a saját Jira-felhasználónevedet és jelszavadat."}
        </p>

        {error && (
          <p role="alert" className="mt-6 rounded-lg bg-rose-50 px-3 py-2 text-sm text-rose-700">
            {error}
          </p>
        )}

        {oauth && (
          <button
            type="button"
            className="btn-primary mt-6 w-full py-3"
            onClick={() => {
              setBusy(true);
              window.location.assign("/api/auth/oauth/start");
            }}
            disabled={busy}
          >
            <svg viewBox="0 0 24 24" className="size-5" fill="currentColor" aria-hidden="true">
              <path d="M11.5 2.5 3 11a1.5 1.5 0 0 0 0 2l8.5 8.5L14 19l-6-6 6-6zm1 0L10 5l6 6-6 6 2.5 2.5L21 13a1.5 1.5 0 0 0 0-2z" />
            </svg>
            {busy ? "Átirányítás a Jirára…" : "Belépés Jirával (céges SSO)"}
          </button>
        )}

        {oauth && passwordLogin && !showPassword && (
          <button type="button" className="mt-4 w-full text-center text-xs text-slate-500 hover:text-slate-800" onClick={() => setShowPassword(true)}>
            Belépés felhasználónévvel és jelszóval
          </button>
        )}

        {passwordVisible && (
          <form onSubmit={submit} className="mt-6 space-y-4">
            {oauth && <div className="border-t border-slate-200 pt-4 text-xs text-slate-500">vagy Jira-felhasználónévvel és jelszóval:</div>}
            <label className="block text-sm font-medium text-slate-700">
              Jira-felhasználónév
              <input
                className="input mt-1"
                autoComplete="username"
                autoFocus={!oauth}
                required
                value={username}
                onChange={(event) => setUsername(event.target.value)}
              />
            </label>
            <label className="block text-sm font-medium text-slate-700">
              Jelszó
              <input
                className="input mt-1"
                type="password"
                autoComplete="current-password"
                required
                value={password}
                onChange={(event) => setPassword(event.target.value)}
              />
            </label>
            <button className={`${oauth ? "btn-ghost" : "btn-primary"} w-full py-2.5`} type="submit" disabled={busy}>
              {busy ? "Belépés és jogosultság ellenőrzése…" : "Bejelentkezés"}
            </button>
          </form>
        )}

        <p className="mt-6 text-xs leading-relaxed text-slate-500">
          {oauth
            ? "A Belépés Jirával a céges Microsoft-fiókodat használja, a portál jelszót nem lát. "
            : "A jelszavadat a portál csak a Jira-belépéshez használja, nem menti el sehova. "}
          A munkamenet legfeljebb 8 óráig érvényes, kijelentkezéskor azonnal megszűnik.
        </p>
      </div>
    </main>
  );
}
