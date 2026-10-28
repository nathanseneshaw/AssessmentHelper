import { useCallback, useEffect, useState } from "react";
import { ApiError, apiFetch } from "./client";

export type ObservationStatus =
  | "ACCEPTED"
  | "SCHEDULED"
  | "IN_PROGRESS"
  | "SUBMITTED"
  | "COMPLETED";
export type ViewerRole = "OBSERVER" | "OBSERVEE";
export type QuestionType = "RATING" | "TEXT";

export interface Criterion {
  criterionId: number;
  criterionText: string;
  questionType: QuestionType;
  required: boolean;
  displayOrder: number;
  ratingMin: number | null;
  ratingMax: number | null;
}

export interface ObservationTemplate {
  templateId: number;
  templateName: string;
  version: number;
  criteria: Criterion[];
}

export interface ObservationAnswer {
  criterionId: number;
  rating: number | null;
  comments: string | null;
}

export interface ObservationPerson {
  professorId: number;
  name: string;
  department: string;
}

export interface ObservationCourse {
  sectionId: number;
  label: string;
  courseName: string;
  meetingDays: string | null;
  startTime: string | null;
  endTime: string | null;
  location: string | null;
}

export interface ObservationSummary {
  observationId: number;
  role: ViewerRole;
  status: ObservationStatus;
  observeeName: string;
  observerName: string;
  courseLabel: string;
  semesterLabel: string;
  cycleId: number;
  observationDate: string | null;
  startTime: string | null;
  submittedAt: string | null;
}

export interface ObservationDetail {
  observationId: number;
  status: ObservationStatus;
  viewerRole: ViewerRole;
  observee: ObservationPerson;
  observer: ObservationPerson;
  course: ObservationCourse;
  semesterLabel: string;
  cycleId: number;
  observationDate: string | null;
  startTime: string | null;
  template: ObservationTemplate;
  responses: ObservationAnswer[];
  submittedAt: string | null;
  canStart: boolean;
  canEdit: boolean;
}

export const STATUS_LABELS: Record<ObservationStatus, string> = {
  ACCEPTED: "Not started",
  SCHEDULED: "Not started",
  IN_PROGRESS: "In progress",
  SUBMITTED: "Submitted",
  COMPLETED: "Completed",
};

const MONTHS = [
  "Jan",
  "Feb",
  "Mar",
  "Apr",
  "May",
  "Jun",
  "Jul",
  "Aug",
  "Sep",
  "Oct",
  "Nov",
  "Dec",
];

/**
 * "2026-11-04" → "Nov 4, 2026". The parts are read as written, so no time zone can move the day.
 * Anything unexpected is returned unchanged.
 */
export function formatDate(date: string) {
  const [year, month, day] = date.split("-").map(Number);
  const name = MONTHS[(month ?? 0) - 1];
  if (!name || !year || !day) return date;
  return `${name} ${day}, ${year}`;
}

/** "08:30:00" or "08:30" → "8:30 AM". Anything unexpected is returned unchanged. */
export function formatTime(time: string) {
  const [hours, minutes] = time.split(":").map(Number);
  if (
    hours === undefined ||
    minutes === undefined ||
    Number.isNaN(hours) ||
    Number.isNaN(minutes)
  )
    return time;
  return `${hours % 12 || 12}:${String(minutes).padStart(2, "0")} ${hours < 12 ? "AM" : "PM"}`;
}

/**
 * When an observation happens: "2026-11-04" and "08:30:00" → "Nov 4, 2026, 8:30 AM", or just the date without a
 * time. Null without a date, so each caller can say "Not scheduled" its own way.
 */
export function formatWhen(date: string | null, time: string | null) {
  if (!date) return null;
  return time ? `${formatDate(date)}, ${formatTime(time)}` : formatDate(date);
}

/**
 * "2026-11-04T09:15:00" → "Nov 4, 2026, 9:15 AM CT". Server timestamps are Central time without an offset,
 * so they are shown as written rather than converted.
 */
export function formatTimestamp(timestamp: string) {
  const [date = "", time = ""] = timestamp.split("T");
  return time
    ? `${formatDate(date)}, ${formatTime(time)} CT`
    : formatDate(date);
}

const send = <T>(path: string, method: string, body?: unknown) =>
  apiFetch<T>(path, {
    method,
    headers: { "Content-Type": "application/json" },
    body: body === undefined ? undefined : JSON.stringify(body),
  });

export const observationPath = (observationId: number) =>
  `/api/observations/${observationId}`;
export const professorObservationsPath = (professorId: number) =>
  `/api/professors/${professorId}/observations`;

export const startObservation = (observationId: number) =>
  send<ObservationDetail>(`${observationPath(observationId)}/start`, "POST");

export const saveObservationResponses = (
  observationId: number,
  responses: ObservationAnswer[],
) =>
  send<ObservationDetail>(
    `${observationPath(observationId)}/responses`,
    "PUT",
    { responses },
  );

export const submitObservation = (
  observationId: number,
  responses: ObservationAnswer[],
) =>
  send<ObservationDetail>(`${observationPath(observationId)}/submit`, "POST", {
    responses,
  });

/** True when a write was refused because the observation changed elsewhere, for example in another tab. */
export const isConflict = (cause: unknown) =>
  cause instanceof ApiError && cause.status === 409;

/**
 * Loads one GET path. A null path loads nothing, for sessions without a faculty profile. `setData` lets a caller
 * replace the result with the detail a write returned, without another request. While `reload` fetches again, the
 * current data and any error stay on screen until the answer arrives; a different path starts empty.
 */
export function useApiResource<T>(path: string | null) {
  const [data, setData] = useState<T | null>(null);
  const [error, setError] = useState("");
  const [version, setVersion] = useState(0);
  const [shownPath, setShownPath] = useState(path);
  const reload = useCallback(() => setVersion((v) => v + 1), []);

  // Cleared while rendering, so another path's data is never shown for this one.
  if (shownPath !== path) {
    setShownPath(path);
    setData(null);
    setError("");
  }

  // biome-ignore lint/correctness/useExhaustiveDependencies: version triggers a manual retry.
  useEffect(() => {
    if (path === null) return;
    const controller = new AbortController();
    apiFetch<T>(path, { signal: controller.signal })
      .then((result) => {
        if (controller.signal.aborted) return;
        setData(result);
        setError("");
      })
      .catch((cause) => {
        if (!controller.signal.aborted)
          setError(
            cause instanceof Error ? cause.message : "Unable to load data.",
          );
      });
    return () => controller.abort();
  }, [path, version]);

  return { data, error, reload, setData };
}
