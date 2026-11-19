import { DEMO_MODE } from "../demo/demoMode";
import {
  clearDemoSession,
  getDemoSession,
  signOutDemoSession,
} from "../demo/tabSession";
import type { Role } from "../navConfig";
import { ApiError, apiFetch } from "./client";

export interface CurrentUserResponse {
  userId: number;
  professorId: number | null;
  schoolId: number | null;
  name: string;
  email: string;
  roles: ("AC" | "PROFESSOR")[];
}

export interface User {
  userId: number;
  professorId: number | null;
  schoolId: number | null;
  name: string;
  email: string;
  role: Role;
}

// AC members also have the PROFESSOR role, but the app shows one view per user, so the AC view wins.
export function toUser({ roles, ...user }: CurrentUserResponse): User {
  return { ...user, role: roles.includes("AC") ? "ac" : "professor" };
}

export async function getCurrentUser(): Promise<User | null> {
  try {
    return toUser(await apiFetch<CurrentUserResponse>("/api/auth/me"));
  } catch (error) {
    if (error instanceof ApiError && error.status === 401) {
      return null;
    }
    throw error;
  }
}

export async function login(email: string, password: string): Promise<User> {
  const user = await apiFetch<CurrentUserResponse>("/api/auth/login", {
    method: "POST",
    body: new URLSearchParams({ email, password }),
  });
  clearDemoSession();
  return toUser(user);
}

export async function logout(): Promise<void> {
  if (DEMO_MODE && getDemoSession() !== null) {
    signOutDemoSession();
    return;
  }
  return apiFetch<void>("/api/auth/logout", { method: "POST" });
}
