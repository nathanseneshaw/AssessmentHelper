import { useEffect, useState } from "react";
import {
  type DashboardMetrics,
  getDashboardMetrics,
} from "../../api/acDashboard";
import {
  getDueQueue,
  type OverdueQueue,
} from "../../api/evaluationEligibility";
import {
  type AcState,
  schedule,
  useObserverData,
} from "../../api/observerEligibility";
import { ActionButton } from "../../components/ActionButton";
import { type Kpi, KpiCard } from "../../components/KpiCard";
import { PageHeader } from "../../components/PageHeader";
import { shortDate } from "../../format";
import { OverdueEvaluations } from "./OverdueEvaluations";

type Reading = Pick<Kpi, "value" | "note">;

const loading: Reading = { value: "—", note: "Loading…" };

interface SignupData {
  metrics: DashboardMetrics | null;
  metricsFailed: boolean;
  /** Null until the observer matching data loads. */
  observer: AcState | null;
  observerError: string;
  notSignedUpDisabled: boolean;
  onViewNotSignedUp: () => void;
}

function signupKpis({
  metrics,
  metricsFailed,
  observer,
  observerError,
  notSignedUpDisabled,
  onViewNotSignedUp,
}: SignupData): Kpi[] {
  const metric = (
    label: string,
    read: (loaded: DashboardMetrics) => Reading,
    card: Partial<Kpi> = {},
  ): Kpi => ({
    label,
    ...card,
    ...(metricsFailed
      ? { value: "Unavailable", note: "Metrics could not be loaded" }
      : metrics
        ? read(metrics)
        : loading),
  });
  const cycle = observer?.cycle;
  const deadline: Reading = observer
    ? {
        value: cycle?.signupDeadline
          ? shortDate(cycle.signupDeadline)
          : "Not set",
        note: cycle ? `${cycle.label} cycle` : "Current cycle",
      }
    : observerError
      ? { value: "Unavailable", note: "Cycle could not be loaded" }
      : loading;
  return [
    metric("Due Faculty", (loaded) => ({
      value: String(loaded.facultyDue),
      note: "Current cycle",
    })),
    metric("Signed Up", ({ participation: { count, total } }) => ({
      value: String(count),
      note:
        total === 0
          ? "No faculty are due this cycle"
          : `${Math.round((count / total) * 100)}% participation`,
    })),
    metric(
      "Not Yet Signed Up",
      ({ participation: { count, total } }) => ({
        value: String(total - count),
        note:
          total - count > 0
            ? "Reminder recommended"
            : "All due faculty have signed up",
      }),
      {
        alert: true,
        action: {
          label: "View All",
          ariaLabel: "View all faculty not yet signed up",
          disabled: notSignedUpDisabled,
          onClick: onViewNotSignedUp,
        },
      },
    ),
    { label: "Signup Deadline", alert: true, ...deadline },
  ];
}

const columns = ["Observee", "Course / Section", "Meeting Time", "Status"];

export function AcSignups() {
  const { data, error } = useObserverData<AcState>("/ac");
  const [metrics, setMetrics] = useState<DashboardMetrics | null>(null);
  const [metricsFailed, setMetricsFailed] = useState(false);
  const [dueQueue, setDueQueue] = useState<OverdueQueue | null>(null);
  const [dueFailed, setDueFailed] = useState(false);
  const [notSignedUpOpen, setNotSignedUpOpen] = useState(false);
  useEffect(() => {
    const controller = new AbortController();
    getDashboardMetrics(controller.signal)
      .then(setMetrics)
      .catch(() => {
        if (!controller.signal.aborted) setMetricsFailed(true);
      });
    getDueQueue(controller.signal)
      .then(setDueQueue)
      .catch(() => {
        if (!controller.signal.aborted) setDueFailed(true);
      });
    return () => controller.abort();
  }, []);
  const signedUp = new Set((data?.signups ?? []).map((s) => s.professorId));
  const notSignedUp = (dueQueue?.faculty ?? []).filter(
    (person) => !signedUp.has(person.professorId),
  );
  const kpis = signupKpis({
    metrics,
    metricsFailed,
    observer: data,
    observerError: error,
    notSignedUpDisabled: !dueQueue || dueFailed || !data || Boolean(error),
    onViewNotSignedUp: () => setNotSignedUpOpen(true),
  });
  const signupRequests = (data?.signups ?? []).map((s) => ({
    signupId: s.signupId,
    observee: s.name,
    section: s.section.label,
    meetingTime: schedule(s.section),
    status: s.released ? "List sent" : "Awaiting review",
  }));
  return (
    <div>
      <PageHeader
        title="Evaluation Signups"
        subtitle="Review course selections and preferred observation times submitted by faculty."
      />
      {notSignedUpOpen && dueQueue && data && (
        <OverdueEvaluations
          kind="due"
          title="Faculty Not Yet Signed Up"
          countLabel="not signed up"
          emptyMessage="All due faculty have signed up."
          queue={{
            ...dueQueue,
            count: notSignedUp.length,
            faculty: notSignedUp,
          }}
          onClose={() => setNotSignedUpOpen(false)}
          onNotified={(professorId, sentAt) =>
            setDueQueue(
              (previous) =>
                previous && {
                  ...previous,
                  faculty: previous.faculty.map((person) =>
                    person.professorId === professorId
                      ? { ...person, lastNotifiedAt: sentAt }
                      : person,
                  ),
                },
            )
          }
        />
      )}

      {/* Cards keep the Overview's KPI width rather than stretching across the page. */}
      {error && (
        <p role="alert" className="mt-4 text-brand-red">
          {error}
        </p>
      )}
      <div className="mt-10.25 grid gap-4 sm:grid-cols-2 xl:max-w-324 xl:grid-cols-4">
        {kpis.map((kpi) => (
          <KpiCard key={kpi.label} kpi={kpi} />
        ))}
      </div>

      <section className="mt-9.5 rounded-lg border border-line bg-white px-5.5 pt-5 pb-5">
        <h2 className="font-semibold text-[21px]/7">Signup Requests</h2>
        {!data && !error && <p>Loading signups...</p>}
        {data && signupRequests.length === 0 && <p>No signups yet.</p>}
        {/* On narrow screens the table scrolls inside the card. `relative` keeps
            the sr-only header inside the scroller so the page does not widen. */}
        <div className="relative mt-6.75 overflow-x-auto">
          <table className="w-full min-w-200">
            <thead>
              <tr className="text-muted text-sm">
                {columns.map((column) => (
                  <th
                    key={column}
                    scope="col"
                    className="w-21/100 pb-7.5 text-left font-medium"
                  >
                    {column}
                  </th>
                ))}
                <th scope="col">
                  <span className="sr-only">Action</span>
                </th>
              </tr>
            </thead>
            <tbody>
              {signupRequests.map((request) => (
                // From lg up the rows sit 131px apart, as in the mockup.
                <tr key={request.signupId} className="*:pb-8 lg:*:pb-20.75">
                  <th scope="row" className="text-left font-semibold">
                    {request.observee}
                  </th>
                  <td>{request.section}</td>
                  <td>{request.meetingTime}</td>
                  <td>{request.status}</td>
                  <td>
                    <ActionButton
                      tone="green"
                      ariaLabel={`Review signup for ${request.observee}`}
                      to={`/ac/matching?signup=${request.signupId}`}
                      className="min-w-40.5"
                    >
                      Review
                    </ActionButton>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </section>
    </div>
  );
}
