import { apiFetch } from "./client";

/** A part of a whole, such as signups with an observer out of all signups. */
export interface Ratio {
  count: number;
  total: number;
}

export interface DashboardMetrics {
  cycleId: number;
  cycleLabel: string;
  facultyDue: number;
  /** How many observer candidates make a full list. */
  listSize: number;
  participation: Ratio;
  observerUtilization: Ratio;
  listSufficiency: Ratio;
  missingObservations: number;
}

export function getDashboardMetrics(
  signal?: AbortSignal,
): Promise<DashboardMetrics> {
  return apiFetch<DashboardMetrics>("/api/ac-dashboard/metrics", { signal });
}
