import { type ReactNode, useCallback, useEffect, useRef } from "react";
import { Link, Outlet } from "react-router-dom";
import {
  type EvaluationDeadline,
  type EvaluationRecord,
  evaluationDeadlinePath,
  evaluationRecordsPath,
  evaluationStatusLabel,
  initials,
  observationCountLabel,
  rankLabel,
} from "../../api/evaluations";
import { type FeedbackSurvey, feedbackPath } from "../../api/feedback";
import {
  formatDate,
  formatWhen,
  type ObservationSummary,
  professorObservationsPath,
  STATUS_LABELS,
  useApiResource,
} from "../../api/observations";
import {
  type FacultyState,
  observerButton,
  onObserverChange,
  useObserverData,
} from "../../api/observerEligibility";
import { useAuth } from "../../auth/AuthContext";
import { evaluationSummary, type SummaryTone } from "./dashboardSummary";

const TONE_CLASSES: Record<SummaryTone, string> = {
  highlight: "text-brand-orange",
  alert: "text-brand-red",
};

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

/** "2026-10-03" → OCT and 03. The parts are read as written, so no time zone can move the day. */
function badge(date: string | null) {
  const [year, month, day] = (date ?? "").split("-").map(Number);
  if (!year || !month || !day) return { month: "", day: "" };
  return {
    month: new Date(year, month - 1, day)
      .toLocaleString("en-US", { month: "short" })
      .toUpperCase(),
    day: String(day).padStart(2, "0"),
  };
}

/**
 * What the professor has to do next: start or continue the observations they make. Soonest first, then the ones
 * without a date.
 */
function observationTasks(observations: ObservationSummary[]): UpcomingItem[] {
  // ISO dates and times sort as plain strings. An empty one stands for no date, and sorts last.
  const start = (o: ObservationSummary) =>
    o.observationDate ? `${o.observationDate}T${o.startTime ?? ""}` : "";
  const soonest = (a: ObservationSummary, b: ObservationSummary) => {
    const [x, y] = [start(a), start(b)];
    if (x === y) return a.observationId - b.observationId;
    if (!x || !y) return x ? -1 : 1;
    return x < y ? -1 : 1;
  };
  return observations
    .filter(
      (o) =>
        o.role === "OBSERVER" &&
        (o.status === "ACCEPTED" ||
          o.status === "SCHEDULED" ||
          o.status === "IN_PROGRESS"),
    )
    .sort(soonest)
    .map((o) => ({
      key: `observation-${o.observationId}`,
      ...badge(o.observationDate),
      title: `Observation — ${o.courseLabel}`,
      detail: `Observee: ${o.observeeName}`,
      action: o.status === "IN_PROGRESS" ? "Continue" : "Start Observation",
      actionContext: ` (${o.courseLabel})`,
      to: `/faculty/observations/${o.observationId}`,
      status: STATUS_LABELS[o.status],
      when: formatWhen(o.observationDate, o.startTime) ?? "Not scheduled",
      urgent: false,
    }));
}

/** The newest two observations for a dashboard card. The API lists the newest first. */
function ObservationLines({
  observations,
  error,
  person,
}: {
  observations: ObservationSummary[] | null;
  error: string;
  person: (observation: ObservationSummary) => string;
}) {
  if (error)
    return <p className="text-ink/60 text-sm">Couldn't load observations.</p>;
  if (!observations)
    return <p className="text-ink/60 text-sm">Loading observations...</p>;
  if (!observations.length)
    return <p className="text-ink/60 text-sm">No observations yet.</p>;
  return observations.slice(0, 2).map((o) => (
    <div key={o.observationId}>
      <p className="font-semibold text-sm">{o.courseLabel}</p>
      <p className="text-ink/60 text-xs">{person(o)}</p>
      <p className="text-ink/60 text-xs">
        {STATUS_LABELS[o.status]} •{" "}
        {o.observationDate ? formatDate(o.observationDate) : "Not scheduled"}
      </p>
    </div>
  ));
}

/** The newest two finished or earlier cycles for a dashboard card. The API lists the newest first. */
function HistoryLines({
  records,
  error,
}: {
  records: EvaluationRecord[] | null;
  error: string;
}) {
  if (error)
    return (
      <p className="text-ink/60 text-sm">Couldn't load evaluation history.</p>
    );
  if (!records)
    return <p className="text-ink/60 text-sm">Loading evaluation history...</p>;
  if (!records.length)
    return <p className="text-ink/60 text-sm">No previous evaluations yet.</p>;
  return records.slice(0, 2).map((record) => {
    const term = `${record.term} ${record.academicYear}`;
    return (
      <div
        key={record.cycleId}
        className="flex items-center justify-between gap-3"
      >
        <div>
          <p className="font-semibold text-sm">{term}</p>
          <p className="text-ink/60 text-xs">
            {evaluationStatusLabel(record.status)} •{" "}
            {observationCountLabel(record.observationCount)}
          </p>
        </div>
        <Link
          to={`/faculty/history?cycle=${record.cycleId}`}
          className="rounded-md bg-brand-green px-4 py-2 font-medium text-white text-xs"
        >
          View Evaluation
          <span className="sr-only"> ({term})</span>
        </Link>
      </div>
    );
  });
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

export function FacultyDashboard() {
  const { user } = useAuth();
  const id = user?.professorId;
  const { data, error } = useObserverData<FacultyState>(
    id ? `/faculty/${id}` : null,
  );
  const observations = useApiResource<ObservationSummary[]>(
    id ? professorObservationsPath(id) : null,
  );
  const reloadObservations = observations.reload;
  const feedback = useApiResource<FeedbackSurvey[]>(id ? feedbackPath : null);
  const reloadFeedback = feedback.reload;
  const deadline = useApiResource<EvaluationDeadline>(
    id ? evaluationDeadlinePath(id) : null,
  );
  const reloadDeadline = deadline.reload;
  const records = useApiResource<EvaluationRecord[]>(
    id ? evaluationRecordsPath(id) : null,
  );
  const reloadRecords = records.reload;
  // Finishing an observation can complete the evaluation, which moves the due date.
  const reloadEvaluation = useCallback(() => {
    reloadDeadline();
    reloadRecords();
  }, [reloadDeadline, reloadRecords]);
  useEffect(() => onObserverChange(reloadFeedback), [reloadFeedback]);
  useEffect(() => {
    const refresh = () => reloadFeedback();
    window.addEventListener("focus", refresh);
    const timer = window.setInterval(refresh, 60000);
    return () => {
      window.removeEventListener("focus", refresh);
      window.clearInterval(timer);
    };
  }, [reloadFeedback]);
  // The dialogs above announce their writes, such as an accepted request or a submitted observation.
  useEffect(() => onObserverChange(reloadObservations), [reloadObservations]);
  useEffect(() => onObserverChange(reloadEvaluation), [reloadEvaluation]);
  // Submitting an observation notifies the observee, and the status above checks for new notifications, so a
  // new one means the observations changed too. The first load needs no reload.
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
    ) {
      reloadObservations();
      reloadEvaluation();
    }
    seenNotification.current = newestNotification;
  }, [newestNotification, reloadObservations, reloadEvaluation]);
  const loaded = id ? observations.data : [];
  const forMe = loaded?.filter((o) => o.role === "OBSERVEE") ?? null;
  const forOthers = loaded?.filter((o) => o.role === "OBSERVER") ?? null;
  const feedbackTasks: UpcomingItem[] = (feedback.data ?? [])
    .filter(
      (s) =>
        s.status === "AVAILABLE" ||
        s.status === "NOT_OPEN" ||
        s.status === "SUBMITTED",
    )
    .map((s) => ({
      key: `feedback-${s.cycleId}`,
      ...badge(s.feedbackDeadline),
      title:
        s.status === "SUBMITTED"
          ? "Process Feedback Submitted"
          : "Process Feedback Due",
      detail: s.label,
      action: s.status === "SUBMITTED" ? "View Survey" : "Start Survey",
      disabled: s.status === "NOT_OPEN",
      to: `/faculty/feedback?cycle=${s.cycleId}`,
      status:
        s.status === "SUBMITTED"
          ? "Submitted"
          : s.status === "NOT_OPEN"
            ? "Not yet available"
            : "Action needed",
      when:
        s.status === "SUBMITTED"
          ? s.submittedDate
            ? `Submitted ${formatDate(s.submittedDate)}`
            : "Submitted"
          : `Due ${formatDate(s.feedbackDeadline ?? "")}`,
      urgent: s.status !== "SUBMITTED",
    }));
  const upcoming = [
    ...(loaded ? observationTasks(loaded) : []),
    ...feedbackTasks,
  ];
  const dueCount = upcoming.filter(
    (item) => item.status !== "Submitted",
  ).length;
  const summaryCards = evaluationSummary({
    deadline: deadline.data,
    deadlineError: deadline.error,
    records: records.data,
    cycle: data?.cycle,
    observations: loaded,
  });
  // The cycle in progress is not history yet. Once it is completed, it is.
  const previousEvaluations =
    records.data?.filter(
      (r) =>
        r.cycleId !== data?.cycle?.cycleId ||
        r.status?.toUpperCase() === "COMPLETED",
    ) ?? null;
  const professorName = data?.professor?.name;
  const rank = rankLabel(deadline.data?.professorRank);
  const subtitle = [rank, data?.professor?.department]
    .filter(Boolean)
    .join(" • ");
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

      <div className="mt-6 flex flex-wrap items-center justify-between gap-3 border-line border-b pb-5">
        <div className="flex items-center gap-4">
          <div className="flex h-11 w-11 items-center justify-center rounded-full bg-mist font-medium text-brand-green text-sm">
            {professorName ? initials(professorName) : "…"}
          </div>
          <div>
            <p className="font-semibold">
              {professorName ?? "Loading professor..."}
            </p>
            <p className="text-ink/60 text-xs">{subtitle || "Faculty"}</p>
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
      <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
        {summaryCards.map((item) => (
          <div
            key={item.label}
            className="rounded-xl border border-line bg-white p-5"
          >
            <p className="text-ink/60 text-xs">{item.label}</p>
            <p
              className={`mt-3 font-bold text-2xl ${
                item.tone ? TONE_CLASSES[item.tone] : ""
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
        {observations.error && (
          <p role="alert" className="mb-4 text-brand-red text-sm">
            {observations.error}{" "}
            <button
              type="button"
              className="underline"
              onClick={reloadObservations}
            >
              Retry
            </button>
          </p>
        )}
        <div className="flex flex-col">
          {!loaded && !observations.error && (
            <p className="border-line border-t py-5 text-ink/60 text-sm">
              Loading observations...
            </p>
          )}
          {!upcoming.length && (
            <p className="border-line border-t py-5 text-ink/60 text-sm">
              Nothing upcoming.
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
                        <span className="sr-only">{item.actionContext}</span>
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
          <HistoryLines records={previousEvaluations} error={records.error} />
        </SectionCard>

        <SectionCard
          title="Observations for Me"
          subtitle="Current and previous course observations done for my evaluations"
          viewAllLabel="View All Records"
          viewAllTo="/faculty/observations/me"
        >
          <ObservationLines
            observations={forMe}
            error={observations.error}
            person={(o) => `Observer: ${o.observerName}`}
          />
        </SectionCard>

        <SectionCard
          title="Observations for Others"
          subtitle="Course observations I completed for other professor evaluations"
          viewAllLabel="View All Records"
          viewAllTo="/faculty/observations/others"
        >
          <ObservationLines
            observations={forOthers}
            error={observations.error}
            person={(o) => `Observee: ${o.observeeName}`}
          />
        </SectionCard>
      </div>

      <Outlet />
    </div>
  );
}
