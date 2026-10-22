import { apiFetch } from "./client";

export interface ObserverRequest {
  requestId: number;
  signupId: number;
  observerId: number;
  requestDate: string;
  expirationDate: string;
  responseDate: string | null;
  status: "PENDING" | "CONFIRMED" | "EXPIRED";
}

export interface RequestOptions {
  signupId: number;
  courseLabel: string;
  semesterLabel: string;
  signupStatus: string;
  unavailableReason: string | null;
  candidates: {
    matchId: number;
    observerId: number;
    observerName: string;
    department: string;
  }[];
  requests: { request: ObserverRequest; observerName: string }[];
}

export function getRequestOptions(professorId: number) {
  return apiFetch<RequestOptions[]>(
    `/api/professors/${professorId}/observer-requests`,
  );
}

export function sendObserverRequest(
  professorId: number,
  signupId: number,
  matchId: number,
) {
  return apiFetch<ObserverRequest>(
    `/api/professors/${professorId}/observer-requests`,
    {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ signupId, matchId }),
    },
  );
}
