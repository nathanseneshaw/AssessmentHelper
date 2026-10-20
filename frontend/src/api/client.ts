const DEFAULT_API_URL = "http://localhost:8080";

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
  init?: RequestInit,
): Promise<T> {
  const response = await fetch(`${API_URL}${path}`, init);
  if (!response.ok) {
    throw new ApiError(response.status, await errorMessage(response));
  }
  if (response.status === 204) {
    return undefined as T;
  }
  return (await response.json()) as T;
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
