export interface SelectedCycle {
  cycleId: number;
  period: "PAST" | "CURRENT" | "UPCOMING" | "UNAVAILABLE";
}

let selected: SelectedCycle | null = null;

export function setSelectedCycle(cycle: SelectedCycle | null) {
  selected = cycle;
}

export function selectedCyclePath(path: string, method: string): string {
  if (!selected) return path;
  if (
    !["GET", "HEAD", "OPTIONS"].includes(method) &&
    selected.period !== "CURRENT" &&
    !path.startsWith("/api/ac/cycles") &&
    !path.startsWith("/api/auth/")
  ) {
    throw new Error("The selected evaluation cycle is read-only.");
  }
  if (method !== "GET" || selected.period === "CURRENT") return path;
  const cycleId = selected.cycleId;
  const paths: Record<string, string> = {
    "/api/evaluation-eligibility": `/api/evaluation-eligibility/cycles/${cycleId}`,
    "/api/due-evaluations": `/api/evaluation-eligibility/cycles/${cycleId}/due`,
    "/api/overdue-evaluations": `/api/evaluation-eligibility/cycles/${cycleId}/overdue`,
    "/api/observer-eligibility/ac": `/api/observer-eligibility/ac/cycles/${cycleId}`,
    "/api/ac-dashboard/metrics": `/api/ac-dashboard/cycles/${cycleId}/metrics`,
    "/api/ac-dashboard/observations": `/api/ac-dashboard/cycles/${cycleId}/observations`,
    "/api/missing-observations": `/api/cycles/${cycleId}/missing-observations`,
  };
  return paths[path] ?? path;
}
