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
  return toUser(
    await postJson<CurrentUserResponse>("/api/demo/switch", { userId }),
  );
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
