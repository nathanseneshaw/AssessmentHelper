import { useCallback, useEffect, useState } from "react";
import { apiFetch } from "./client";

export interface Cycle {
  cycleId: number;
  semesterId: number;
  label: string;
  startDate: string;
  signupDeadline: string;
  observationDeadline: string;
  feedbackDeadline?: string;
  signupOpen: boolean;
  requestsOpen: boolean;
}
export interface Person {
  professorId: number;
  name: string;
  department: string;
}
export interface Section {
  sectionId: number;
  label: string;
  courseName: string;
  meetingDays: string;
  startTime: string;
  endTime: string;
}
export interface Candidate extends Person {
  observerId: number;
  level: string;
  qualifyingCourses: string[];
}
export interface Signup {
  signupId: number;
  professorId: number;
  name: string;
  department: string;
  section: Section;
  windowStart: string;
  windowEnd: string;
  released: boolean;
  candidates: Candidate[];
}
export interface ObserverRequest {
  requestId: number;
  signupId: number;
  professorId: number;
  observeeName: string;
  observerName: string;
  section: Section;
  windowStart: string;
  windowEnd: string;
  status: string;
}
export interface FacultyState {
  cycle: Cycle;
  professor: Person;
  signup: Signup | null;
  incoming: ObserverRequest[];
  outgoing: ObserverRequest[];
  notifications: {
    notificationId: number;
    message: string;
    createdAt: string;
  }[];
}
export interface AcState {
  cycle: Cycle;
  signups: Signup[];
}
export const observerBase = "/api/observer-eligibility";
export const schedule = (s: Section) =>
  `${s.meetingDays} ${s.startTime?.slice(0, 5) ?? "?"}–${s.endTime?.slice(0, 5) ?? "?"}`;
export const postObserver = <T = void>(path: string, body?: unknown) =>
  apiFetch<T>(`${observerBase}${path}`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: body === undefined ? undefined : JSON.stringify(body),
  });
// A null path loads nothing, for sessions without a faculty profile.
export function useObserverData<T>(path: string | null) {
  const [data, setData] = useState<T | null>(null);
  const [error, setError] = useState("");
  const [version, setVersion] = useState(0);
  const reload = useCallback(() => {
    setVersion((v) => v + 1);
  }, []);
  // Retry explicitly reloads the same URL.
  // biome-ignore lint/correctness/useExhaustiveDependencies: version triggers a manual retry.
  useEffect(() => {
    const controller = new AbortController();
    setData(null);
    if (path === null) return;
    let pending = false;
    const read = async () => {
      if (pending) return;
      pending = true;
      try {
        const result = await apiFetch<T>(`${observerBase}${path}`, {
          signal: controller.signal,
        });
        if (!controller.signal.aborted) {
          setData(result);
          setError("");
        }
      } catch (cause) {
        if (!controller.signal.aborted)
          setError(
            cause instanceof Error
              ? cause.message
              : "Unable to load observer data.",
          );
      } finally {
        pending = false;
      }
    };
    void read();
    const timer = setInterval(() => void read(), 5000);
    window.addEventListener("observer-data-changed", read);
    window.addEventListener("focus", read);
    return () => {
      controller.abort();
      clearInterval(timer);
      window.removeEventListener("observer-data-changed", read);
      window.removeEventListener("focus", read);
    };
  }, [path, version]);
  return { data, error, reload };
}
export function observerChanged() {
  window.dispatchEvent(new Event("observer-data-changed"));
}
export const observerButton =
  "rounded-lg bg-brand-orange px-5 py-3 text-sm font-medium text-white hover:opacity-90 disabled:bg-btn-gray disabled:cursor-not-allowed";
