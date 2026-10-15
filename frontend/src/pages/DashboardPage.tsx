import { useEffect, useRef } from "react";
import type { AuthUser, UserRole } from "../auth/mockAuth";

const dashboards = {
  faculty: {
    title: "Faculty Dashboard",
    description: "Placeholder dashboard for faculty members.",
  },
  "assessment-committee": {
    title: "Assessment Committee Dashboard",
    description: "Placeholder dashboard for assessment committee members.",
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
    <main className="min-h-dvh px-4 py-6 text-neutral-900 sm:px-6 sm:py-8">
      <div className="mx-auto w-full max-w-4xl">
        <header className="flex flex-wrap items-center justify-between gap-4 rounded-xl border border-neutral-200 border-t-4 border-t-utd-orange bg-white p-5 shadow-sm sm:p-6">
          <div className="min-w-0">
            <p className="text-xs font-semibold uppercase tracking-wider text-utd-green">
              The University of Texas at Dallas
            </p>
            <p className="mt-1 font-bold text-utd-green">Assessment Helper</p>
          </div>
          <button
            type="button"
            onClick={onSignOut}
            className="min-h-11 rounded-lg bg-utd-green px-4 py-2 font-semibold text-white hover:bg-utd-green-dark"
          >
            Sign Out
          </button>
        </header>

        <section className="mt-6 rounded-xl border border-neutral-200 bg-white p-5 shadow-sm sm:p-6">
          <h1
            ref={headingRef}
            tabIndex={-1}
            className="text-2xl font-bold tracking-tight text-utd-green sm:text-3xl"
          >
            {dashboard.title}
          </h1>
          <p className="mt-2 text-neutral-600">{dashboard.description}</p>
        </section>
      </div>
    </main>
  );
}
