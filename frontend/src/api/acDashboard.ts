import { useCallback, useEffect, useRef, useState } from "react";
import { apiFetch } from "./client";
import type { EvaluationDeferral } from "./evaluations";
import type { FeedbackSurvey } from "./feedback";
import { onObserverChange } from "./observerEligibility";

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
  /** Observees who submitted the cycle's feedback survey, out of the active and completed observees it goes to. */
  surveyCompletion: Ratio;
  surveyStatus: Exclude<FeedbackSurvey["status"], "SUBMITTED">;
  /** Observer assignments that break an eligibility rule, out of all assignments checked. */
  eligibilityAccuracy: EligibilityAccuracy;
}

export interface EligibilityAccuracy {
  incorrect: Ratio;
  /** (a) not the same professor. */
  samePerson: number;
  /** (b) not a same-level professor from another department. */
  otherDepartment: number;
  /** (c) not a different-level professor from the same department. */
  differentLevel: number;
  /** (d) not a same-level professor who works during the requested days and times. */
  scheduleConflict: number;
}

export function getDashboardMetrics(
  signal?: AbortSignal,
): Promise<DashboardMetrics> {
  return apiFetch<DashboardMetrics>("/api/ac-dashboard/metrics", { signal });
}

/** A signup past the observation deadline with no completed observation. */
export interface MissingSignup {
  signupId: number;
  observeeId: number;
  observee: string;
  course: string;
  /** Null when no observer ever accepted. */
  observer: string | null;
  /** Null when no observer ever accepted. */
  observationStatus: string | null;
}

export interface MissingSignups {
  cycleId: number;
  cycleLabel: string;
  observationDeadline: string | null;
  signups: MissingSignup[];
}

export function getMissingSignups(
  signal?: AbortSignal,
): Promise<MissingSignups> {
  return apiFetch<MissingSignups>("/api/missing-observations", { signal });
}

export function rate({ count, total }: Ratio, counted: string, empty: string) {
  return total === 0
    ? { value: "—", note: empty }
    : {
        value: `${Math.round((count / total) * 100)}%`,
        note: `${count} of ${total} ${counted}`,
      };
}

/** Participation Rate: professors who signed up / professors who were due. */
export function participationRate({ participation }: DashboardMetrics) {
  return participation.total === 0
    ? { value: "—", note: "No faculty are due this cycle" }
    : {
        value: `${Math.round((participation.count / participation.total) * 100)}%`,
        note: `${participation.count} signed up / ${participation.total} due`,
      };
}

/** List Sufficiency: average observer matches per sign-up. */
export function listSufficiency({
  listSufficiency: { count, total },
}: DashboardMetrics) {
  return total === 0
    ? { value: "—", note: "No signups this cycle" }
    : {
        value: (count / total).toFixed(1),
        note: `${count} matches across ${total} signups`,
      };
}

/** Evaluation Eligibility Accuracy: observer assignments that follow every rule. */
export function eligibilityAccuracy({
  eligibilityAccuracy: a,
}: DashboardMetrics) {
  const { count, total } = a.incorrect;
  return total === 0
    ? { value: "—", note: "No observer assignments to check" }
    : {
        value: `${Math.round(((total - count) / total) * 100)}%`,
        note: `${count} incorrect of ${total} (same prof ${a.samePerson}, other dept ${a.otherDepartment}, different level ${a.differentLevel}, schedule ${a.scheduleConflict})`,
      };
}

/** Until the survey opens nobody can answer it, so the card says why it is empty instead of showing 0%. */
export function surveyCompletionRate({
  surveyCompletion,
  surveyStatus,
}: DashboardMetrics) {
  if (surveyCompletion.count === 0 && surveyCompletion.total > 0) {
    if (surveyStatus === "NOT_OPEN")
      return {
        value: "—",
        note: "Survey opens after the observation deadline",
      };
    if (surveyStatus === "UNCONFIGURED")
      return { value: "—", note: "Survey deadlines are not set" };
  }
  return rate(
    surveyCompletion,
    "observees submitted feedback",
    "No observees to survey this cycle",
  );
}

/**
 * A number for the committee pages' loading effects to depend on, so their figures reload whenever they may have
 * changed. `matching` is the observer matching data, which polls on its own; the figures reload when it changes so
 * counts taken from the same signups stay in step with it.
 */
export function useDashboardRefresh(matching: unknown) {
  const [version, setVersion] = useState(0);
  const refresh = useCallback(() => setVersion((value) => value + 1), []);
  useEffect(() => {
    const stop = onObserverChange(refresh);
    window.addEventListener("focus", refresh);
    const timer = window.setInterval(refresh, 60000);
    return () => {
      stop();
      window.removeEventListener("focus", refresh);
      window.clearInterval(timer);
    };
  }, [refresh]);
  // The first answer, and the empty state while the matching data reloads, need no refresh of their own.
  const fingerprint = matching ? JSON.stringify(matching) : null;
  const seen = useRef<string | null>(null);
  useEffect(() => {
    if (fingerprint === null) return;
    if (seen.current !== null && seen.current !== fingerprint) refresh();
    seen.current = fingerprint;
  }, [fingerprint, refresh]);
  return { version, refresh };
}

/**
 * MISSING means the cycle observation deadline has passed without final completion.
 * SCHEDULED is any other observation that is not completed, even one with no date yet.
 */
export type ObservationStatus =
  | "DEFERRED"
  | "AWAITING_OBSERVER"
  | "SCHEDULED"
  | "PENDING_SIGN_OFF"
  | "COMPLETED"
  | "MISSING";

/** A generated signup in the cycle and its observation, once an observer accepts. */
export interface TrackedObservation {
  signupId: number;
  observationId?: number | null;
  observeeId: number;
  observee: string;
  observerId: number | null;
  observer: string | null;
  course: string;
  listReleased: boolean;
  date: string | null;
  startTime: string | null;
  status: ObservationStatus;
  missingReason?: string | null;
  observeeSigned: boolean;
  observerSigned: boolean;
  completedAt: string | null;
  deferral?: EvaluationDeferral | null;
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
