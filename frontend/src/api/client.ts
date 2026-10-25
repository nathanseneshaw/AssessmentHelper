const DEFAULT_API_URL = "http://localhost:8080";
const CSRF_COOKIE = "XSRF-TOKEN";
const CSRF_HEADER = "X-XSRF-TOKEN";
const SAFE_METHODS = new Set(["GET", "HEAD", "OPTIONS"]);

// Bun inlines BUN_PUBLIC_API_URL when it bundles the frontend. If the variable is unset,
// the reference is left as written, and `process` does not exist in the browser.
function configuredApiUrl(): string | undefined {
  try {
    return process.env.BUN_PUBLIC_API_URL;
  } catch {
    return undefined;
  }
}

export const API_URL = configuredApiUrl() ?? DEFAULT_API_URL;

export class ApiError extends Error {
  constructor(
    readonly status: number,
    message: string,
  ) {
    super(message);
    this.name = "ApiError";
  }
}

export async function apiFetch<T>(
  path: string,
  init: RequestInit = {},
): Promise<T> {
  const headers = new Headers(init.headers);
  if (!SAFE_METHODS.has((init.method ?? "GET").toUpperCase())) {
    const token = await csrfToken();
    if (token) {
      headers.set(CSRF_HEADER, token);
    }
  }
  const response = await fetch(`${API_URL}${path}`, {
    ...init,
    headers,
    credentials: "include",
  });
  if (!response.ok) {
    throw new ApiError(response.status, await errorMessage(response));
  }
  if (response.status === 204) {
    return undefined as T;
  }
  return (await response.json()) as T;
}

// The backend keeps its CSRF token in a cookie that this page can read, because browsers share cookies
// between ports on the same host. Spring only creates the token when something asks for it.
async function csrfToken(): Promise<string | undefined> {
  const existing = readCookie(CSRF_COOKIE);
  if (existing) {
    return existing;
  }
  await fetch(`${API_URL}/api/auth/csrf`, { credentials: "include" });
  return readCookie(CSRF_COOKIE);
}

function readCookie(name: string): string | undefined {
  const prefix = `${name}=`;
  const cookie = document.cookie
    .split("; ")
    .find((entry) => entry.startsWith(prefix));
  return cookie ? decodeURIComponent(cookie.slice(prefix.length)) : undefined;
}

async function errorMessage(response: Response): Promise<string> {
  const problem = (await response.json().catch(() => null)) as {
    title?: string;
    detail?: string;
  } | null;
  return (
    problem?.detail ??
    problem?.title ??
    `Request failed with status ${response.status}`
  );
}
