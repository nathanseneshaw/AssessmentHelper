import { setDemoSession } from "../demo/tabSession";
import { type CurrentUserResponse, toUser, type User } from "./auth";
import { apiFetch } from "./client";

export interface DemoAccount {
  userId: number;
  professorId: number;
  name: string;
  email: string;
  department: string;
  committee: boolean;
}

export interface Department {
  departmentId: number;
  schoolId: number;
  departmentName: string;
  departmentCode: string;
}

export interface NewProfessor {
  firstName: string;
  lastName: string;
  departmentId: number | null;
  rank: string;
  hireDate: string | null;
}

export interface NewSection {
  professorId: number | null;
  departmentId: number | null;
  courseNumber: string;
  courseName: string;
  sectionNumber: string;
  meetingDays: string;
  startTime: string;
  endTime: string;
  location: string;
}

export interface AddedSection {
  sectionId: number;
  label: string;
  semester: string;
}

export interface OpenEvaluation {
  signupId: number;
  observeeId: number;
  observee: string;
  course: string;
  observer: string | null;
  observationStatus: string | null;
}

export interface CycleSemester {
  name: string;
  startDate: string;
  endDate: string;
}

export interface DemoDate {
  today: string;
  daysAhead: number;
  semesters: CycleSemester[];
  expiredRequests: number;
}

function postJson<T>(path: string, body: unknown): Promise<T> {
  return apiFetch<T>(path, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(body),
  });
}

export function getDemoAccounts(signal?: AbortSignal): Promise<DemoAccount[]> {
  return apiFetch<DemoAccount[]>("/api/demo/accounts", { signal });
}

export async function switchAccount(userId: number): Promise<User> {
  const { user, token } = await postJson<{
    user: CurrentUserResponse;
    token: string;
  }>("/api/demo/switch", { userId });
  setDemoSession(token);
  return toUser(user);
}

export function getDepartments(signal?: AbortSignal): Promise<Department[]> {
  return apiFetch<Department[]>("/api/demo/departments", { signal });
}

export function addProfessor(input: NewProfessor): Promise<DemoAccount> {
  return postJson<DemoAccount>("/api/demo/professors", input);
}

export function addSection(input: NewSection): Promise<AddedSection> {
  return postJson<AddedSection>("/api/demo/sections", input);
}

export function getOpenEvaluations(
  signal?: AbortSignal,
): Promise<OpenEvaluation[]> {
  return apiFetch<OpenEvaluation[]>("/api/demo/evaluations", { signal });
}

export function stepEvaluation(
  signupId: number,
): Promise<{ observationId: number; status: string }> {
  return postJson(`/api/demo/evaluations/${signupId}/step`, {});
}

export function completeEvaluation(
  signupId: number,
): Promise<{ observationId: number }> {
  return postJson(`/api/demo/evaluations/${signupId}/complete`, {});
}

export function getDemoDate(signal?: AbortSignal): Promise<DemoDate> {
  return apiFetch<DemoDate>("/api/demo/date", { signal });
}

export function moveDemoDate(date: string): Promise<DemoDate> {
  return postJson<DemoDate>("/api/demo/date", { date });
}

export function resetDemo(): Promise<{ signedOut: boolean }> {
  return postJson("/api/demo/reset", {});
}
