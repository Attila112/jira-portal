import { useQuery, useQueryClient } from "@tanstack/react-query";
import type { ReactNode } from "react";
import { Navigate, useLocation } from "react-router";
import { ApiError, api } from "./api";
import type { SessionInfo } from "./types";

export const SESSION_KEY = ["session"] as const;

/** A bejelentkezett felhasználó; null, ha nincs munkamenet. */
export function useSession() {
  return useQuery({
    queryKey: SESSION_KEY,
    queryFn: async (): Promise<SessionInfo | null> => {
      try {
        return await api<SessionInfo>("/api/auth/me");
      } catch (error) {
        if (error instanceof ApiError && error.status === 401) return null;
        throw error;
      }
    },
    staleTime: 5 * 60_000,
    retry: false,
  });
}

/** Biztosan betöltött munkamenet a védett oldalakon belül. */
export function useSessionInfo(): SessionInfo {
  const { data } = useSession();
  if (!data) throw new Error("useSessionInfo csak RequireAuth alatt használható.");
  return data;
}

export function useLogout() {
  const client = useQueryClient();
  return async () => {
    try {
      await api("/api/auth/logout", { method: "POST" });
    } finally {
      client.clear();
      client.setQueryData(SESSION_KEY, null);
    }
  };
}

export function RequireAuth({ children }: { children: ReactNode }) {
  const { data, isPending, error } = useSession();
  const location = useLocation();
  if (isPending) {
    return <div className="grid min-h-screen place-items-center text-sm text-slate-500">Betöltés…</div>;
  }
  if (error) {
    return (
      <div className="grid min-h-screen place-items-center p-6 text-center text-sm text-rose-700">
        {error.message}
      </div>
    );
  }
  if (!data) {
    return <Navigate to="/login" replace state={{ from: location.pathname + location.search }} />;
  }
  return <>{children}</>;
}
