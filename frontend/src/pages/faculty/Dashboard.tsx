import type { ReactNode } from "react";
import { Link, Outlet } from "react-router-dom";
import {
  type FacultyState,
  observerButton,
  type Person,
  useObserverData,
} from "../../api/observerEligibility";
import { useAuth } from "../../auth/AuthContext";

const summary = [
  {
    label: "Evaluation Status",
    value: "In Progress",
    note: "Current evaluation cycle",
    highlight: true,
  },
  {
    label: "Cycle",
    value: "Every 2 Years",
    note: "Associate professor schedule",
  },
  { label: "Next Due", value: "Mar 2027", note: "Spring 2027 semester" },
  {
    label: "Observations This Term",
    value: "2",
    note: "One submitted • one scheduled",
  },
];

const upcoming = [
  {
    month: "OCT",
    day: "03",
    title: "Observation — CS 4348.501",
    detail: "Observer: Professor 2",
    action: "View Details",
    to: "/faculty/observations/cs4348-501",
    status: "Scheduled",
    when: "Oct 3, 10:00 AM",
    urgent: false,
  },
  {
    month: "SEP",
    day: "28",
    title: "Process Feedback Due",
    detail: "CS 3345.002 observation",
    action: "Start Survey",
    to: "/faculty/feedback",
    status: "Action needed",
    when: "Due Sep 28",
    urgent: true,
  },
];

const history = [
  { term: "Spring 2025", detail: "Completed • 2 observations" },
  { term: "Fall 2023", detail: "Completed • 2 observations" },
];

const observationsForMe = [
  { course: "CS 4348.501", detail: "Submitted • Mar 20, 2025" },
  { course: "CS 4348.501", detail: "Scheduled • Apr 14, 2025" },
];

const observationsForOthers = [
  { course: "CS 3345.002", detail: "Submitted • Mar 28, 2025" },
  { course: "CS 3345.002", detail: "Scheduled • Apr 20, 2025" },
];

function SectionCard({
  title,
  subtitle,
  viewAllLabel,
  viewAllTo,
  children,
}: {
  title: string;
  subtitle: string;
  viewAllLabel: string;
  viewAllTo: string;
  children: ReactNode;
}) {
  return (
    <div className="rounded-xl border border-line bg-white p-5">
      <div className="flex items-start justify-between gap-3">
        <h3 className="font-semibold text-lg">{title}</h3>
        <Link
          to={viewAllTo}
          className="whitespace-nowrap font-semibold text-brand-green text-xs hover:underline"
        >
          {viewAllLabel} →
        </Link>
      </div>
      <p className="mt-1 mb-5 text-ink/60 text-xs">{subtitle}</p>
      <div className="flex flex-col gap-4">{children}</div>
    </div>
  );
}

export function FacultyDashboard() {
  const { user, login } = useAuth();
  const id = user?.professorId ?? 1;
  const { data, error } = useObserverData<FacultyState>(`/faculty/${id}`);
  const { data: people } = useObserverData<Person[]>("/faculty");
  const released = !!data?.signup?.released;
  const canSignup = !!data?.cycle?.signupOpen && !data?.signup && !error;
  const facultyLink = (to: string, label: string, enabled: boolean) =>
    enabled ? (
      <Link to={to} className={observerButton}>
        {label}
      </Link>
    ) : (
      <button type="button" disabled className={observerButton}>
        {label}
      </button>
    );
  const liveSummary = summary.map((item) =>
    item.label === "Observations This Term"
      ? {
          ...item,
          value: String(
            data?.outgoing?.filter((r) => r.status === "CONFIRMED").length ?? 0,
          ),
          note: "Accepted observer requests",
        }
      : item,
  );
  return (
    <div className="mx-auto max-w-6xl">
      <p className="font-semibold text-brand-green text-xs tracking-wide">
        FACULTY OVERVIEW
      </p>

      <div className="mt-2 flex flex-wrap items-start justify-between gap-4">
        <div>
          <h1 className="font-bold text-4xl">My Evaluation Dashboard</h1>
          <p className="mt-1 text-ink/60 text-sm">
            Your evaluation progress and upcoming observations, in one place.
          </p>
        </div>
        <div className="flex gap-3">
          {facultyLink(
            "/faculty/observers",
            "View Generated Observers",
            released && !error,
          )}
          {facultyLink("/faculty/signup", "Sign Up for Evaluation", canSignup)}
        </div>
      </div>

      <div className="mt-6 flex items-center justify-between border-line border-b pb-5">
        <div className="flex items-center gap-4">
          <div className="flex h-11 w-11 items-center justify-center rounded-full bg-mist font-medium text-brand-green text-sm">
            P1
          </div>
          <div>
            <p className="font-semibold">
              {data?.professor?.name ?? "Loading professor..."}
            </p>
            <p className="text-ink/60 text-xs">
              Associate Professor • School of ECS
            </p>
          </div>
        </div>
        <p className="text-ink/60 text-sm">
          {data?.cycle?.label ?? "Current"} Cycle
        </p>
      </div>

      {error && (
        <p role="alert" className="mt-4 text-brand-red">
          {error}
        </p>
      )}
      <div className="mt-4 flex flex-wrap items-center gap-4 rounded-lg border border-line bg-white p-4">
        <label className="text-sm">
          Demo faculty identity{" "}
          <select
            value={id}
            onChange={(e) => {
              const p = people?.find(
                (p) => p.professorId === Number(e.target.value),
              );
              if (p && user)
                login({ ...user, professorId: p.professorId, name: p.name });
            }}
            className="ml-3 rounded border border-line p-2"
          >
            {Array.isArray(people) &&
              people.map((p) => (
                <option key={p.professorId} value={p.professorId}>
                  {p.name}
                </option>
              ))}
          </select>
        </label>
        <Link
          className="font-semibold text-brand-green underline"
          to="/faculty/requests"
        >
          Observer Requests (
          {data?.incoming?.filter((r) => r.status === "PENDING").length ?? 0}{" "}
          pending)
        </Link>
      </div>
      {data?.notifications && data.notifications.length > 0 && (
        <section
          aria-labelledby="notifications-title"
          className="mt-4 rounded-lg border border-line bg-mist p-4"
        >
          <h2
            id="notifications-title"
            className="font-semibold text-brand-green"
          >
            Notifications
          </h2>
          <ul
            aria-label="Notification history"
            // biome-ignore lint/a11y/noNoninteractiveTabindex: Keyboard users need to focus this scrolling history.
            tabIndex={0}
            className="mt-3 max-h-40 space-y-3 overflow-y-auto pr-2 text-sm"
          >
            {data.notifications.map((n) => (
              <li
                key={n.notificationId}
                className="border-line border-b pb-3 last:border-0"
              >
                <p>{n.message}</p>
                <time
                  dateTime={n.createdAt}
                  className="mt-1 block text-xs text-muted"
                >
                  {new Date(n.createdAt).toLocaleString("en-US", {
                    timeZone: "America/Chicago",
                    dateStyle: "medium",
                    timeStyle: "short",
                  })}{" "}
                  CT
                </time>
              </li>
            ))}
          </ul>
        </section>
      )}
      <h2 className="mt-6 mb-3 text-ink/70 text-sm">Evaluation summary</h2>
      <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
        {liveSummary.map((item) => (
          <div
            key={item.label}
            className="rounded-xl border border-line bg-white p-5"
          >
            <p className="text-ink/60 text-xs">{item.label}</p>
            <p
              className={`mt-3 font-bold text-2xl ${
                item.highlight ? "text-brand-orange" : ""
              }`}
            >
              {item.value}
            </p>
            <p className="mt-3 text-ink/60 text-xs">{item.note}</p>
          </div>
        ))}
      </div>

      <div className="mt-6 rounded-xl border border-line bg-white p-5">
        <h3 className="font-semibold text-lg">Upcoming</h3>
        <p className="mt-1 mb-4 text-ink/60 text-xs">
          Next observations and action items
        </p>
        <div className="flex flex-col">
          {upcoming.map((item) => (
            <div
              key={item.title}
              className="flex flex-wrap items-center justify-between gap-4 border-line border-t py-5"
            >
              <div className="flex items-center gap-4">
                <div
                  className={`flex h-12 w-12 flex-col items-center justify-center rounded-md ${
                    item.urgent
                      ? "bg-peach text-brand-orange"
                      : "bg-mist text-brand-green"
                  }`}
                >
                  <span className="font-semibold text-[10px]">
                    {item.month}
                  </span>
                  <span className="font-bold text-lg leading-none">
                    {item.day}
                  </span>
                </div>
                <div>
                  <p className="font-semibold">{item.title}</p>
                  <p className="text-ink/60 text-xs">{item.detail}</p>
                </div>
              </div>
              <div className="flex flex-col items-center gap-1">
                <div className="flex gap-3">
                  <Link
                    to={item.to}
                    className={`rounded-md px-5 py-2 font-medium text-xs ${
                      item.urgent
                        ? "bg-btn-light text-ink"
                        : "bg-brand-green text-white"
                    }`}
                  >
                    {item.action}
                  </Link>
                  <span
                    className={`rounded-md px-5 py-2 font-medium text-xs ${
                      item.urgent
                        ? "bg-peach text-brand-orange"
                        : "bg-mist text-brand-green"
                    }`}
                  >
                    {item.status}
                  </span>
                </div>
                <p className="self-end text-ink/60 text-xs">{item.when}</p>
              </div>
            </div>
          ))}
        </div>
      </div>

      <div className="mt-6 grid gap-4 lg:grid-cols-3">
        <SectionCard
          title="Evaluation History"
          subtitle="Your previous evaluation cycles"
          viewAllLabel="View All"
          viewAllTo="/faculty/history"
        >
          {history.map((item) => (
            <div key={item.term} className="flex items-center justify-between">
              <div>
                <p className="font-semibold text-sm">{item.term}</p>
                <p className="text-ink/60 text-xs">{item.detail}</p>
              </div>
              <Link
                to="/faculty/history"
                className="rounded-md bg-brand-green px-4 py-2 font-medium text-white text-xs"
              >
                View Evaluation
              </Link>
            </div>
          ))}
        </SectionCard>

        <SectionCard
          title="Observations for Me"
          subtitle="Current and previous course observations done for my evaluations"
          viewAllLabel="View All Records"
          viewAllTo="/faculty/observations/me"
        >
          {observationsForMe.map((item) => (
            <div key={`${item.course}-${item.detail}`}>
              <p className="font-semibold text-sm">{item.course}</p>
              <p className="text-ink/60 text-xs">{item.detail}</p>
            </div>
          ))}
        </SectionCard>

        <SectionCard
          title="Observations for Others"
          subtitle="Course observations I completed for other professor evaluations"
          viewAllLabel="View All Records"
          viewAllTo="/faculty/observations/others"
        >
          {observationsForOthers.map((item) => (
            <div key={`${item.course}-${item.detail}`}>
              <p className="font-semibold text-sm">{item.course}</p>
              <p className="text-ink/60 text-xs">{item.detail}</p>
            </div>
          ))}
        </SectionCard>
      </div>

      <Outlet />
    </div>
  );
}
