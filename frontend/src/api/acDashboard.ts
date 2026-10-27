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

/**
 * MISSING means the observation's date has passed and it is not completed.
 * SCHEDULED is any other observation that is not completed, even one with no date yet.
 */
export type ObservationStatus =
  | "AWAITING_OBSERVER"
  | "SCHEDULED"
  | "COMPLETED"
  | "MISSING";

/** A generated signup in the cycle and its observation, once an observer accepts. */
export interface TrackedObservation {
  signupId: number;
  observeeId: number;
  observee: string;
  observerId: number | null;
  observer: string | null;
  course: string;
  listReleased: boolean;
  date: string | null;
  startTime: string | null;
  status: ObservationStatus;
  observeeSigned: boolean;
  observerSigned: boolean;
  completedAt: string | null;
}

export interface ObservationTracking {
  cycleId: number;
  cycleLabel: string;
  observationDeadline: string | null;
  observations: TrackedObservation[];
}

export function getObservations(
  signal?: AbortSignal,
): Promise<ObservationTracking> {
  return apiFetch<ObservationTracking>("/api/ac-dashboard/observations", {
    signal,
  });
}

/** Sends in-app reminders for outstanding observations and says how many went out. */
export function sendObservationReminders(): Promise<{ sent: number }> {
  return apiFetch<{ sent: number }>(
    "/api/ac-dashboard/observations/reminders",
    { method: "POST" },
  );
}
