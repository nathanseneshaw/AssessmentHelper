import { useEffect, useRef } from "react";
import type { AuthUser, UserRole } from "../auth/mockAuth";

const dashboards = {
  faculty: {
    title: "Faculty Dashboard",
    description: "Your workspace for course assessment activities.",
  },
  "assessment-committee": {
    title: "Assessment Committee Dashboard",
    description: "Your workspace for reviewing program assessment activities.",
  },
} satisfies Record<UserRole, { title: string; description: string }>;

interface DashboardPageProps {
  user: AuthUser;
  onSignOut: () => void;
}

export function DashboardPage({ user, onSignOut }: DashboardPageProps) {
  const dashboard = dashboards[user.role];
  const headingRef = useRef<HTMLHeadingElement>(null);

  useEffect(() => {
    headingRef.current?.focus();
  }, []);

  return (
    <main className="min-h-screen bg-slate-100 px-4 py-8 text-slate-900">
      <div className="mx-auto w-full max-w-4xl">
        <header className="flex flex-wrap items-center justify-between gap-4 rounded-xl bg-white p-6 shadow-sm">
          <div>
            <p className="font-bold">Assessment Helper</p>
            <p className="mt-1 break-all text-sm text-slate-600">
              {user.email}
            </p>
          </div>
          <button
            type="button"
            onClick={onSignOut}
            className="rounded-lg bg-blue-700 px-4 py-2 font-semibold text-white hover:bg-blue-800"
          >
            Sign Out
          </button>
        </header>

        <section className="mt-6 rounded-xl bg-white p-6 shadow-sm">
          <h1 ref={headingRef} tabIndex={-1} className="text-3xl font-bold">
            {dashboard.title}
          </h1>
          <p className="mt-3 text-lg">Welcome, {user.name}.</p>
          <p className="mt-2 text-slate-600">{dashboard.description}</p>
          <p className="mt-6 rounded-lg bg-slate-50 p-4 text-sm text-slate-600">
            You are signed in with a demo account. Dashboard features are coming
            soon.
          </p>
        </section>
      </div>
    </main>
  );
}
