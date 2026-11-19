const STORAGE_KEY = "demoTabSession";

export const DEMO_SESSION_HEADER = "X-Demo-Session";

export function getDemoSession(): string | null {
  try {
    return sessionStorage.getItem(STORAGE_KEY);
  } catch {
    return null;
  }
}

export function setDemoSession(token: string) {
  sessionStorage.setItem(STORAGE_KEY, token);
}

export function clearDemoSession() {
  if (getDemoSession() !== null) sessionStorage.removeItem(STORAGE_KEY);
}

export function signOutDemoSession() {
  // Keep this tab signed out even if the browser still has a regular session.
  setDemoSession("signed-out");
}
