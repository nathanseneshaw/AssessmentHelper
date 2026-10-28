import {
  type ReactNode,
  useCallback,
  useEffect,
  useRef,
  useState,
} from "react";
import { Link, Outlet } from "react-router-dom";
import {
  type FacultyDashboard as Dashboard,
  getFacultyDashboard,
  type ObservationItem,
  type ObservationStatus,
  type Upcoming,
} from "../../api/facultyDashboard";
import { type FeedbackSurvey, feedbackPath } from "../../api/feedback";
import { formatWhen, useApiResource } from "../../api/observations";
import {
  type FacultyState,
  observerButton,
  onObserverChange,
  useObserverData,
} from "../../api/observerEligibility";
import { useAuth } from "../../auth/AuthContext";
import { longDate, monthYear, shortDate } from "../../format";

const RANKS: Record<string, string> = {
  ASSISTANT: "Assistant Professor",
  ASSOCIATE: "Associate Professor",
  FULL: "Professor",
};

const OBSERVATION_STATUS: Record<ObservationStatus, string> = {
  NOT_STARTED: "Not started",
  IN_PROGRESS: "In progress",
  SUBMITTED: "Submitted",
  COMPLETED: "Completed",
};

// The dashboard shows the newest few; the View All pages have the rest.
const PREVIEW = 2;

function titleCase(text: string) {
  return text.charAt(0).toUpperCase() + text.slice(1).toLowerCase();
}

function rankTitle(rank: string | null) {
  return rank ? (RANKS[rank] ?? titleCase(rank)) : null;
}

function plural(count: number, noun: string) {
  return `${count} ${noun}${count === 1 ? "" : "s"}`;
}

function initials(name: string) {
  return name
    .split(" ")
    .filter(Boolean)
    .map((word) => word[0])
    .slice(0, 2)
    .join("")
    .toUpperCase();
}

/** The day it happened, or the day it is planned for. completedAt has a time of day, so only its date is used. */
function observedOn(observation: ObservationItem) {
  const day = observation.completedAt?.slice(0, 10) ?? observation.date;
  return day ? longDate(day) : "Not scheduled";
}

/** An observation's status, or Overdue once its date has passed before the form was submitted. */
function statusLabel(status: ObservationStatus | null, overdue: boolean) {
  if (overdue) return "Overdue";
  return status ? OBSERVATION_STATUS[status] : "";
}

interface SummaryCard {
  label: string;
  value: string;
  note: string;
  highlight?: boolean;
}

function summary({
  evaluation,
  schedule,
  termObservations,
}: Dashboard): SummaryCard[] {
  const rank = rankTitle(schedule.rank);
  const status = {
    NOT_DUE: {
      value: "Not Due",
      note: schedule.nextDueSemester
        ? `Next evaluation in ${schedule.nextDueSemester}`
        : "Nothing to do this cycle",
    },
    NOT_SIGNED_UP: {
      value: "Sign-up Needed",
      note: evaluation.signupDeadline
        ? `Sign up by ${longDate(evaluation.signupDeadline)}`
        : "Sign up for this cycle",
    },
    AWAITING_OBSERVER: {
      value: "Awaiting Observer",
      note: `Signed up for ${evaluation.course}`,
    },
    SCHEDULED: {
      value: "Scheduled",
      note: `${evaluation.observer} • ${
        evaluation.observationDate
          ? longDate(evaluation.observationDate)
          : "Not scheduled"
      }`,
    },
    SUBMITTED: {
      value: "Submitted",
      note: `${evaluation.observer} • ${
        evaluation.observationDate
          ? longDate(evaluation.observationDate)
          : "Not scheduled"
      }`,
    },
    COMPLETED: {
      value: "Completed",
      note: evaluation.completedAt
        ? `Observed ${longDate(evaluation.completedAt.slice(0, 10))}`
        : "Observation completed",
    },
  }[evaluation.status];
  return [
    { label: "Evaluation Status", ...status, highlight: true },
    {
      label: "Cycle",
      value: schedule.intervalYears
        ? `Every ${plural(schedule.intervalYears, "Year")}`
        : "Not set",
      note: rank ? `${rank} schedule` : "No evaluation policy applies",
    },
    {
      label: "Next Due",
      value: schedule.nextDueDate ? monthYear(schedule.nextDueDate) : "Not set",
      note: schedule.nextDueSemester
        ? `${schedule.dueStatus === "Overdue" ? "Overdue • " : ""}${schedule.nextDueSemester} semester`
        : "No due date on file",
    },
    {
      label: "Observations This Term",
      value: String(termObservations.total),
      note:
        termObservations.total > 0
          ? `${termObservations.completed} completed • ${termObservations.scheduled} scheduled`
          : "None yet",
    },
  ];
}

function due(date: string | null) {
  return date ? `Due ${shortDate(date)}` : "No deadline set";
}

interface UpcomingItem {
  key: string;
  /** Empty for an item with no date yet. */
  month: string;
  day: string;
  title: string;
  detail: string;
  action: string;
  disabled?: boolean;
  /** Read after the action by screen readers, to tell apart the links of several rows. */
  actionContext?: string;
  to: string;
  status: string;
  when: string;
  urgent: boolean;
}

/** "2026-10-03" → OCT and 03, or both empty for no date. */
function badge(date: string | null) {
  return date
    ? {
        month: new Date(date)
          .toLocaleDateString("en-US", { month: "short", timeZone: "UTC" })
          .toUpperCase(),
        day: new Date(date).toLocaleDateString("en-US", {
          day: "2-digit",
          timeZone: "UTC",
        }),
      }
    : { month: "", day: "" };
}

/** The cycle's process feedback survey. It shows, disabled, before it opens, and goes away once it has closed. */
function feedbackItem(survey: FeedbackSurvey): UpcomingItem {
  const submitted = survey.status === "SUBMITTED";
  return {
    ...badge(survey.feedbackDeadline),
    key: `feedback-${survey.cycleId}`,
    title: submitted ? "Process Feedback Submitted" : "Process Feedback Due",
    detail: survey.label,
    action: submitted ? "View Survey" : "Start Survey",
    disabled: survey.status === "NOT_OPEN",
    to: `/faculty/feedback?cycle=${survey.cycleId}`,
    status: submitted
      ? "Submitted"
      : survey.status === "NOT_OPEN"
        ? "Not yet available"
        : "Action needed",
    when: submitted
      ? survey.submittedDate
        ? `Submitted ${longDate(survey.submittedDate)}`
        : "Submitted"
      : due(survey.feedbackDeadline),
    urgent: !submitted,
  };
}

function upcomingItem(item: Upcoming, cycleLabel: string): UpcomingItem {
  switch (item.type) {
    case "SIGNUP":
      return {
        ...badge(item.date),
        key: "signup",
        title: "Sign Up for Evaluation",
        detail: `${cycleLabel} cycle`,
        action: "Sign Up",
        to: "/faculty/signup",
        status: "Action needed",
        when: due(item.date),
        urgent: true,
      };
    case "FEEDBACK":
      return {
        ...badge(item.date),
        key: `feedback-${item.observationId}`,
        title: "Process Feedback Due",
        detail: `${item.course} observation`,
        action: "Start Survey",
        actionContext: ` (${item.course})`,
        to: "/faculty/feedback",
        status: item.overdue ? "Overdue" : "Action needed",
        when: due(item.date),
        urgent: true,
      };
    case "OBSERVATION": {
      const observing = item.role === "OBSERVER";
      return {
        ...badge(item.date),
        key: `observation-${item.observationId}`,
        title: `Observation — ${item.course}`,
        detail: observing ? `Observee: ${item.with}` : `Observer: ${item.with}`,
        // The observer fills in the form. The observee can open it only once it is submitted, so they get their list.
        action: !observing
          ? "View Details"
          : item.status === "IN_PROGRESS"
            ? "Continue"
            : "Start Observation",
        actionContext: ` (${item.course})`,
        to: observing
          ? `/faculty/observations/${item.observationId}`
          : "/faculty/observations/me",
        status: statusLabel(item.status, item.overdue),
        when: formatWhen(item.date, item.startTime) ?? "Not scheduled",
        urgent: item.overdue,
      };
    }
  }
}

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

/** The newest observations for a dashboard card. The API lists the newest first. */
function ObservationList({
  observations,
}: {
  observations: ObservationItem[];
}) {
  if (observations.length === 0)
    return <p className="text-ink/60 text-sm">No observations yet.</p>;
  return observations.slice(0, PREVIEW).map((observation) => (
    <div key={observation.observationId}>
      <p className="font-semibold text-sm">{observation.course}</p>
      <p className="text-ink/60 text-xs">
        {observation.role === "OBSERVEE" ? "Observer" : "Observee"}:{" "}
        {observation.with}
      </p>
      <p className="text-ink/60 text-xs">
        {statusLabel(observation.status, observation.overdue)} •{" "}
        {observedOn(observation)}
      </p>
    </div>
  ));
}

export function FacultyDashboard() {
  const { user } = useAuth();
  const id = user?.professorId;
  const { data, error } = useObserverData<FacultyState>(
    id ? `/faculty/${id}` : null,
  );
  const [dashboard, setDashboard] = useState<Dashboard | null>(null);
  const [dashboardError, setDashboardError] = useState("");
  const [reload, setReload] = useState(0);

  // Retry deliberately reruns the request even though the professor is unchanged.
  // biome-ignore lint/correctness/useExhaustiveDependencies: reload is an explicit retry trigger.
  useEffect(() => {
    if (!id) return;
    const controller = new AbortController();
    setDashboardError("");
    getFacultyDashboard(id, controller.signal)
      .then(setDashboard)
      .catch((cause) => {
        if (!controller.signal.aborted)
          setDashboardError(
            cause instanceof Error
              ? cause.message
              : "Unable to load your evaluation summary.",
          );
      });
    return () => controller.abort();
  }, [id, reload]);

  const refresh = useCallback(() => setReload((value) => value + 1), []);
  const feedback = useApiResource<FeedbackSurvey[]>(id ? feedbackPath : null);
  const reloadFeedback = feedback.reload;
  useEffect(() => onObserverChange(reloadFeedback), [reloadFeedback]);
  // A survey opens once the observation deadline passes, so it is checked again on focus and every minute.
  useEffect(() => {
    const check = () => reloadFeedback();
    window.addEventListener("focus", check);
    const timer = window.setInterval(check, 60000);
    return () => {
      window.removeEventListener("focus", check);
      window.clearInterval(timer);
    };
  }, [reloadFeedback]);
  // The dialogs above announce their writes, such as an accepted request or a submitted observation.
  useEffect(() => onObserverChange(refresh), [refresh]);
  // Submitting an observation notifies the observee, and the status above checks for new notifications, so a
  // new one means the dashboard changed too. The first load needs no reload.
  const newestNotification = data?.notifications?.reduce(
    (newest, n) => Math.max(newest, n.notificationId),
    0,
  );
  const seenNotification = useRef<number | undefined>(undefined);
  useEffect(() => {
    if (newestNotification === undefined) return;
    if (
      seenNotification.current !== undefined &&
      seenNotification.current !== newestNotification
    )
      refresh();
    seenNotification.current = newestNotification;
  }, [newestNotification, refresh]);

  const profile = dashboard?.profile;
  const role = [rankTitle(profile?.rank ?? null), profile?.school]
    .filter(Boolean)
    .join(" • ");
  // Process feedback comes from the feedback survey, so the summary's own feedback items are left out.
  const upcoming = [
    ...(dashboard?.upcoming ?? [])
      .filter((item) => item.type !== "FEEDBACK")
      .map((item) => upcomingItem(item, dashboard?.cycleLabel ?? "")),
    ...(feedback.data ?? [])
      .filter(
        (s) =>
          s.status === "AVAILABLE" ||
          s.status === "NOT_OPEN" ||
          s.status === "SUBMITTED",
      )
      .map(feedbackItem),
  ];
  const dueCount = upcoming.filter(
    (item) => item.status !== "Submitted",
  ).length;
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
          {data?.signup?.released && !error ? (
            <Link to="/faculty/observers" className={observerButton}>
              View Generated Observers
            </Link>
          ) : (
            <button type="button" disabled className={observerButton}>
              View Generated Observers
            </button>
          )}
          {data?.cycle?.signupOpen && !error ? (
            <Link to="/faculty/signup" className={observerButton}>
              Sign Up for Evaluation
            </Link>
          ) : (
            <button type="button" disabled className={observerButton}>
              Sign Up for Evaluation
            </button>
          )}
        </div>
      </div>

      <div className="mt-6 flex items-center justify-between border-line border-b pb-5">
        <div className="flex items-center gap-4">
          <div
            aria-hidden="true"
            className="flex h-11 w-11 items-center justify-center rounded-full bg-mist font-medium text-brand-green text-sm"
          >
            {profile && initials(profile.name)}
          </div>
          <div>
            <p className="font-semibold">
              {data?.professor?.name ?? profile?.name ?? "Loading professor..."}
            </p>
            {role && <p className="text-ink/60 text-xs">{role}</p>}
          </div>
        </div>
        <p className="text-ink/60 text-sm">
          {data?.cycle?.label ?? "Current"} Cycle
        </p>
      </div>

      {(error || !id) && (
        <p role="alert" className="mt-4 text-brand-red">
          {error ||
            "This account has no faculty profile, so there is no evaluation status to show."}
        </p>
      )}
      <div className="mt-4 flex flex-wrap items-center gap-4 rounded-lg border border-line bg-white p-4">
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
      {dashboardError ? (
        <p role="alert" className="text-brand-red text-sm">
          {dashboardError}{" "}
          <button
            type="button"
            className="underline"
            onClick={() => setReload((value) => value + 1)}
          >
            Retry evaluation summary
          </button>
        </p>
      ) : !dashboard ? (
        id && <p className="text-muted text-sm">Loading evaluation summary…</p>
      ) : (
        <>
          <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
            {summary(dashboard).map((item) => (
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
              {dueCount} {dueCount === 1 ? "item" : "items"} due
            </p>
            <div className="flex flex-col">
              {upcoming.length === 0 && (
                <p className="border-line border-t py-5 text-muted text-sm">
                  Nothing coming up this cycle.
                </p>
              )}
              {upcoming.map((item) => (
                <div
                  key={item.key}
                  className="flex flex-wrap items-center justify-between gap-4 border-line border-t py-5"
                >
                  <div className="flex items-center gap-4">
                    {/* The date is repeated in words below the action. */}
                    <div
                      aria-hidden="true"
                      className={`flex h-12 w-12 shrink-0 flex-col items-center justify-center rounded-md ${
                        item.urgent
                          ? "bg-peach text-brand-orange"
                          : item.month
                            ? "bg-mist text-brand-green"
                            : "border border-line bg-white text-ink/60"
                      }`}
                    >
                      {item.month ? (
                        <>
                          <span className="font-semibold text-[10px]">
                            {item.month}
                          </span>
                          <span className="font-bold text-lg leading-none">
                            {item.day}
                          </span>
                        </>
                      ) : (
                        <span className="font-semibold text-xs">TBD</span>
                      )}
                    </div>
                    <div>
                      <p className="font-semibold">{item.title}</p>
                      <p className="text-ink/60 text-xs">{item.detail}</p>
                    </div>
                  </div>
                  <div className="flex flex-col items-center gap-1">
                    <div className="flex gap-3">
                      {item.disabled ? (
                        <button
                          type="button"
                          disabled
                          className="cursor-not-allowed rounded-md bg-btn-gray px-5 py-2 font-medium text-ink/60 text-xs"
                        >
                          {item.action}
                        </button>
                      ) : (
                        <Link
                          to={item.to}
                          className="rounded-md bg-brand-green px-5 py-2 font-medium text-white text-xs"
                        >
                          {item.action}
                          {item.actionContext && (
                            <span className="sr-only">
                              {item.actionContext}
                            </span>
                          )}
                        </Link>
                      )}
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
              {dashboard.history.length === 0 && (
                <p className="text-muted text-sm">No previous evaluations.</p>
              )}
              {dashboard.history.slice(0, PREVIEW).map((item) => (
                <div
                  key={item.cycleId}
                  className="flex items-center justify-between"
                >
                  <div>
                    <p className="font-semibold text-sm">
                      {item.term} {item.academicYear}
                    </p>
                    <p className="text-ink/60 text-xs">
                      {[
                        item.status && titleCase(item.status),
                        plural(item.observationCount, "observation"),
                      ]
                        .filter(Boolean)
                        .join(" • ")}
                    </p>
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
              <ObservationList observations={dashboard.observationsForMe} />
            </SectionCard>

            <SectionCard
              title="Observations for Others"
              subtitle="Course observations I completed for other professor evaluations"
              viewAllLabel="View All Records"
              viewAllTo="/faculty/observations/others"
            >
              <ObservationList observations={dashboard.observationsForOthers} />
            </SectionCard>
          </div>
        </>
      )}

      <Outlet />
    </div>
  );
}
