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

export interface EvaluationDeadline {
  professorId: number;
  professorRank: string | null;
  evaluationIntervalYears: number | null;
  lastEvaluationDate: string | null;
  nextDueDate: string | null;
  overdue: boolean;
}

export const evaluationDeadlinePath = (professorId: number) =>
  `/api/professors/${professorId}/evaluation-deadline`;

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

export function getEvaluationRecords(
  professorId: number,
): Promise<EvaluationRecord[]> {
  return apiFetch<EvaluationRecord[]>(
    `/api/professors/${professorId}/evaluations`,
  );
}
