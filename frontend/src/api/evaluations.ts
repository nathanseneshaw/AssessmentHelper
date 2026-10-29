import { apiFetch } from "./client";

export interface EvaluationPolicy {
  professorRank: string;
  evaluationIntervalYears: number;
  activeStatus: boolean;
  madeDate: string | null;
  effectiveDate: string | null;
  version: number;
}

export interface EvaluationRecord {
  cycleId: number;
  term: string;
  academicYear: number;
  status: string | null;
  observationCount: number;
  lastObservationCompletedAt: string | null;
}

export function getEvaluationPolicies(): Promise<EvaluationPolicy[]> {
  return apiFetch<EvaluationPolicy[]>("/api/evaluation-policies");
}

export function getEvaluationPolicy(
  professorRank: string,
): Promise<EvaluationPolicy> {
  return apiFetch<EvaluationPolicy>(
    `/api/evaluation-policies/${encodeURIComponent(professorRank)}`,
  );
}

/** When a professor is next due, from GET /api/professors/{id}/evaluation-deadline. */
export interface EvaluationDeadline {
  professorId: number;
  professorRank: string;
  evaluationIntervalYears: number;
  lastEvaluationDate: string | null;
  nextDueDate: string | null;
  overdue: boolean;
}

export const evaluationRecordsPath = (professorId: number) =>
  `/api/professors/${professorId}/evaluations`;
export const evaluationDeadlinePath = (professorId: number) =>
  `/api/professors/${professorId}/evaluation-deadline`;

export function getEvaluationRecords(
  professorId: number,
): Promise<EvaluationRecord[]> {
  return apiFetch<EvaluationRecord[]>(evaluationRecordsPath(professorId));
}

export function getEvaluationDeadline(
  professorId: number,
): Promise<EvaluationDeadline> {
  return apiFetch<EvaluationDeadline>(evaluationDeadlinePath(professorId));
}

const RANK_LABELS: Record<string, string> = {
  ASSISTANT: "Assistant Professor",
  ASSOCIATE: "Associate Professor",
  FULL: "Full Professor",
};

/** "ASSOCIATE" → "Associate Professor". Null without a rank. An unknown rank is shown as it is stored. */
export function rankLabel(rank: string | null | undefined) {
  if (!rank) return null;
  return RANK_LABELS[rank.toUpperCase()] ?? rank;
}

/** "Olivia Observee" → "OO". At most two letters, and "?" for an empty name. */
export function initials(name: string | null | undefined) {
  const letters = (name ?? "")
    .split(/\s+/)
    .filter(Boolean)
    .map((word) => word[0]?.toUpperCase() ?? "");
  if (!letters.length) return "?";
  return letters.length === 1
    ? (letters[0] ?? "?")
    : `${letters[0]}${letters[letters.length - 1]}`;
}

/** How a record's observee status reads on screen. A cycle with no status has not started. */
export function evaluationStatusLabel(status: string | null) {
  if (!status) return "Not started";
  if (status.toUpperCase() === "COMPLETED") return "Completed";
  if (status.toUpperCase() === "ACTIVE") return "In progress";
  return status.charAt(0).toUpperCase() + status.slice(1).toLowerCase();
}

export const observationCountLabel = (count: number) =>
  `${count} ${count === 1 ? "observation" : "observations"}`;
