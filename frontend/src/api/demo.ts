import { type CurrentUserResponse, toUser, type User } from "./auth";
import { apiFetch } from "./client";

export interface DemoAccount {
  userId: number;
  name: string;
  email: string;
  department: string;
  committee: boolean;
}

export function getDemoAccounts(signal?: AbortSignal): Promise<DemoAccount[]> {
  return apiFetch<DemoAccount[]>("/api/demo/accounts", { signal });
}

export async function switchAccount(userId: number): Promise<User> {
  return toUser(
    await apiFetch<CurrentUserResponse>("/api/demo/switch", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ userId }),
    }),
  );
}
