// Vékony fetch-réteg: CSRF-fejléc, egységes hibakezelés.

export class ApiError extends Error {
  readonly status: number;

  constructor(status: number, message: string) {
    super(message);
    this.status = status;
  }
}

function csrfToken(): string | undefined {
  const cookie = document.cookie.split("; ").find((part) => part.startsWith("XSRF-TOKEN="));
  return cookie ? decodeURIComponent(cookie.substring("XSRF-TOKEN=".length)) : undefined;
}

export async function request(path: string, init: RequestInit = {}): Promise<Response> {
  const method = (init.method ?? "GET").toUpperCase();
  const headers = new Headers(init.headers);
  headers.set("Accept", "application/json");
  if (method !== "GET" && method !== "HEAD") {
    const token = csrfToken();
    if (token) headers.set("X-XSRF-TOKEN", token);
    if (init.body !== undefined && !headers.has("Content-Type")) headers.set("Content-Type", "application/json");
  }

  let response: Response;
  try {
    response = await fetch(path, { ...init, method, headers, credentials: "same-origin" });
  } catch {
    throw new ApiError(0, "A szerver nem érhető el. Fut a backend?");
  }

  if (!response.ok) {
    let message = defaultMessage(response.status);
    try {
      const body = (await response.json()) as { error?: string };
      if (body?.error) message = body.error;
    } catch {
      /* nem JSON */
    }
    throw new ApiError(response.status, message);
  }
  return response;
}

export async function api<T>(path: string, init: RequestInit = {}): Promise<T> {
  const response = await request(path, init);
  if (response.status === 204) return undefined as T;
  return (await response.json()) as T;
}

export function query(params: Record<string, string | number | null | undefined>): string {
  const search = new URLSearchParams();
  for (const [key, value] of Object.entries(params)) {
    if (value !== null && value !== undefined && value !== "") search.set(key, String(value));
  }
  const text = search.toString();
  return text ? `?${text}` : "";
}

function defaultMessage(status: number): string {
  switch (status) {
    case 401:
      return "A munkamenet lejárt. Jelentkezz be újra.";
    case 403:
      return "Ehhez nincs jogosultságod.";
    case 404:
      return "Nem található.";
    case 429:
      return "Túl sok kérés. Várj egy kicsit.";
    case 503:
      return "A Jira most nem érhető el. Próbáld újra később.";
    default:
      return `Váratlan hiba (HTTP ${status}).`;
  }
}
