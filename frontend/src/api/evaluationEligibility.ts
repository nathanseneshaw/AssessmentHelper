import { apiFetch } from "./client";

export interface OverdueFaculty {
  professorId: number;
  name: string;
  lastName: string;
  rank: string;
  email: string;
  lastEvaluationCycle: string;
  nextDueDate: string;
  nextDueSemester?: string;
  intervalYears: number;
  lastNotifiedAt: string | null;
}

export interface OverdueQueue {
  cycleId: number;
  cycleLabel: string;
  count: number;
  faculty: OverdueFaculty[];
}

export interface NotificationEmail {
  recipient: string;
  subject: string;
  body: string;
}

export type EvaluationKind = "due" | "overdue";

export type EligibilityStatus =
  | "Due"
  | "Overdue"
  | "Upcoming"
  | "Due next semester";
export interface EvaluationFaculty extends OverdueFaculty {
  status: EligibilityStatus;
}
export interface EvaluationQueue extends Omit<OverdueQueue, "faculty"> {
  faculty: EvaluationFaculty[];
}
export function getEvaluationQueue(
  signal?: AbortSignal,
): Promise<EvaluationQueue> {
  return apiFetch<EvaluationQueue>("/api/evaluation-eligibility", { signal });
}

export async function getDueQueue(signal?: AbortSignal): Promise<OverdueQueue> {
  return apiFetch<OverdueQueue>("/api/due-evaluations", { signal });
}

export async function getOverdueQueue(
  signal?: AbortSignal,
): Promise<OverdueQueue> {
  return apiFetch<OverdueQueue>("/api/overdue-evaluations", { signal });
}

export async function sendNotification(
  cycleId: number,
  professorId: number,
  email: NotificationEmail,
  kind: EvaluationKind = "overdue",
) {
  return apiFetch<{ sentAt: string }>(
    `/api/${kind}-evaluations/${cycleId}/professors/${professorId}/notifications`,
    {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(email),
    },
  );
}

export function getNotificationPreview(
  cycleId: number,
  professorId: number,
  kind: EvaluationKind,
  signal?: AbortSignal,
): Promise<NotificationEmail> {
  return apiFetch<NotificationEmail>(
    `/api/${kind}-evaluations/${cycleId}/professors/${professorId}/email-preview`,
    { signal },
  );
}

export function rankLabel(rank: string) {
  return `${rank.charAt(0)}${rank.slice(1).toLowerCase()} Professor`;
}
