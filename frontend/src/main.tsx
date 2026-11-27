import { MutationCache, QueryCache, QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { StrictMode } from "react";
import { createRoot } from "react-dom/client";
import { BrowserRouter, Navigate, Route, Routes } from "react-router";
import { ApiError } from "./api";
import { RequireAuth, SESSION_KEY } from "./auth";
import Layout from "./components/Layout";
import DashboardPage from "./pages/DashboardPage";
import LoginPage from "./pages/LoginPage";
import WorklogPage from "./pages/WorklogPage";
import "./index.css";

// Bármely kérés 401-et kap -> a munkamenet megszűnt, vissza a belépéshez.
function onApiError(error: unknown) {
  if (error instanceof ApiError && error.status === 401) {
    queryClient.setQueryData(SESSION_KEY, null);
  }
}

const queryClient = new QueryClient({
  queryCache: new QueryCache({ onError: onApiError }),
  mutationCache: new MutationCache({ onError: onApiError }),
  defaultOptions: {
    queries: {
      refetchOnWindowFocus: false,
      retry: (count, error) => !(error instanceof ApiError && error.status >= 400 && error.status < 500) && count < 1,
    },
  },
});

createRoot(document.getElementById("root")!).render(
  <StrictMode>
    <QueryClientProvider client={queryClient}>
      <BrowserRouter>
        <Routes>
          <Route path="/login" element={<LoginPage />} />
          <Route
            element={
              <RequireAuth>
                <Layout />
              </RequireAuth>
            }
          >
            <Route path="/" element={<DashboardPage />} />
            <Route path="/worklog" element={<WorklogPage />} />
          </Route>
          <Route path="*" element={<Navigate to="/" replace />} />
        </Routes>
      </BrowserRouter>
    </QueryClientProvider>
  </StrictMode>,
);
