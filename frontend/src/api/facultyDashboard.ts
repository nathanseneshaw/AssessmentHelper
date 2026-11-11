import { apiFetch } from "./client";
import type { EvaluationRecord } from "./evaluations";

export interface Profile {
  name: string;
  rank: string | null;
  department: string;
  school: string;
}

/**
 * NOT_DUE: not due or overdue this cycle. NOT_SIGNED_UP: due or overdue with no signup.
 * AWAITING_OBSERVER: signed up and no observer has accepted yet.
 * SCHEDULED: an observer accepted and has not submitted the form yet.
 * SUBMITTED: the observer submitted the form. COMPLETED: the observation record is complete.
 */
export type EvaluationStatus =
  | "DEFERRED"
  | "NOT_DUE"
  | "NOT_SIGNED_UP"
  | "AWAITING_OBSERVER"
  | "SCHEDULED"
  | "SUBMITTED"
  | "COMPLETED";

/** The professor's own evaluation this cycle. */
export interface Evaluation {
  status: EvaluationStatus;
  signupDeadline: string | null;
  course: string | null;
  observer: string | null;
  observationDate: string | null;
  completedAt: string | null;
}

/** Every field is null for a professor the due list leaves out, such as one whose rank's policy is inactive. */
export interface Schedule {
  rank: string | null;
  intervalYears: number | null;
  nextDueDate: string | null;
  nextDueSemester: string | null;
  dueStatus: "Due" | "Overdue" | "Upcoming" | "Due next semester" | null;
}

/** Completed requires both signatures. Submitted forms are pending sign-off. */
export interface TermObservations {
  total: number;
  completed: number;
  scheduled: number;
  pendingSignOff: number;
}

export type ObservationRole = "OBSERVEE" | "OBSERVER";

/** Where the observation form is. NOT_STARTED covers an accepted or scheduled observation not begun yet. */
export type ObservationStatus =
  | "NOT_STARTED"
  | "IN_PROGRESS"
  | "SUBMITTED"
  | "COMPLETED";

/**
 * `with` is the other professor: the observer, or the observee.
 * `overdue` is true after the cycle observation deadline until final completion.
 */
export interface ObservationItem {
  observationId: number;
  cycleId: number;
  semester: string;
  role: ObservationRole;
  course: string;
  with: string;
  status: ObservationStatus;
  overdue: boolean;
  date: string | null;
  startTime: string | null;
  completedAt: string | null;
}

/**
 * SIGNUP: sign up by the cycle's deadline. OBSERVATION: an observation this cycle whose form is not submitted yet.
 * FEEDBACK: process feedback on a submitted or completed observation this cycle. SIGN_OFF: a submitted observation
 * the observee has not signed off yet.
 */
export interface Upcoming {
  type: "SIGNUP" | "OBSERVATION" | "FEEDBACK" | "SIGN_OFF";
  date: string | null;
  startTime: string | null;
  overdue: boolean;
  observationId: number | null;
  role: ObservationRole | null;
  status: ObservationStatus | null;
  course: string | null;
  with: string | null;
}

export interface FacultyDashboard {
  cycleId: number;
  cycleLabel: string;
  profile: Profile;
  evaluation: Evaluation;
  schedule: Schedule;
  termObservations: TermObservations;
  upcoming: Upcoming[];
  /** Earlier cycles, newest first. */
  history: EvaluationRecord[];
  /** Newest first. */
  observationsForMe: ObservationItem[];
  observationsForOthers: ObservationItem[];
}

export function getFacultyDashboard(
  professorId: number,
  signal?: AbortSignal,
): Promise<FacultyDashboard> {
  return apiFetch<FacultyDashboard>(
    `/api/professors/${professorId}/dashboard`,
    { signal },
  );
}
