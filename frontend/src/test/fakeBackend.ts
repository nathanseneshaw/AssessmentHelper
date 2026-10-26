import { mock, spyOn } from "bun:test";
import type { Role } from "../navConfig";

export const ACCOUNTS = {
  ac: {
    password: "Committee123!",
    user: {
      userId: 1,
      professorId: 1,
      schoolId: 1,
      name: "Alice Alice",
      email: "alice@utdallas.edu",
      roles: ["AC", "PROFESSOR"],
    },
  },
  professor: {
    password: "Faculty123!",
    user: {
      userId: 2,
      professorId: 2,
      schoolId: 1,
      name: "Bob Bob",
      email: "bob@utdallas.edu",
      roles: ["PROFESSOR"],
    },
  },
} satisfies Record<Role, unknown>;

type SessionUser = Omit<(typeof ACCOUNTS)[Role]["user"], "professorId"> & {
  professorId: number | null;
};
type Route = (
  init: RequestInit | undefined,
  url: URL,
) => Response | Promise<Response>;

let signedInUser: SessionUser | null = null;

function problem(status: number, detail: string) {
  return Response.json(
    { status, detail },
    { status, headers: { "Content-Type": "application/problem+json" } },
  );
}

// Stands in for the backend's /api/auth endpoints, plus any other routes a test passes in.
export function fakeBackend(routes: Record<string, Route> = {}) {
  spyOn(globalThis, "fetch").mockImplementation((async (input, init) => {
    const url = new URL(String(input));
    const path = url.pathname;
    switch (path) {
      case "/api/auth/csrf":
        return new Response(null, { status: 204 });
      case "/api/auth/me":
        return signedInUser
          ? Response.json(signedInUser)
          : problem(401, "Sign in to continue.");
      case "/api/auth/login": {
        const form = new URLSearchParams(String(init?.body));
        const account = Object.values(ACCOUNTS).find(
          (candidate) => candidate.user.email === form.get("email"),
        );
        if (!account || account.password !== form.get("password")) {
          return problem(401, "Incorrect email or password.");
        }
        signedInUser = account.user;
        return Response.json(account.user);
      }
      case "/api/auth/logout":
        signedInUser = null;
        return new Response(null, { status: 204 });
      default:
        return routes[path]?.(init, url) ?? problem(404, "Not found");
    }
  }) as typeof fetch);
}

// Changes let a test sign in as a different professor, or as a user with no professor record.
export function signInAs(
  role: Role | null,
  changes: Partial<SessionUser> = {},
) {
  signedInUser = role ? { ...ACCOUNTS[role].user, ...changes } : null;
}

export function sessionUser() {
  return signedInUser;
}

export function resetBackend() {
  mock.restore();
  signedInUser = null;
}
