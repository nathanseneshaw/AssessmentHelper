import { type ReactNode, useEffect, useState } from "react";
import {
  type DashboardMetrics,
  getDashboardMetrics,
  type Ratio,
} from "../../api/acDashboard";
import {
  getDueQueue,
  getOverdueQueue,
  type OverdueQueue,
  rankLabel,
} from "../../api/evaluationEligibility";
import { type AcState, useObserverData } from "../../api/observerEligibility";
import { ActionButton } from "../../components/ActionButton";
import { CycleDeadlines } from "../../components/CycleDeadlines";
import { type Kpi, KpiCard } from "../../components/KpiCard";
import { PageHeader } from "../../components/PageHeader";
import { OverdueEvaluations } from "./OverdueEvaluations";

interface KpiGroup {
  title: string;
  grid: string;
  kpis: Kpi[];
}

type Reading = Pick<Kpi, "value" | "note">;

function rate(
  { count, total }: Ratio,
  counted: string,
  empty: string,
): Reading {
  return total === 0
    ? { value: "—", note: empty }
    : {
        value: `${Math.round((count / total) * 100)}%`,
        note: `${count} of ${total} ${counted}`,
      };
}

interface DashboardData {
  metrics: DashboardMetrics | null;
  metricsFailed: boolean;
  overdue: OverdueQueue | null;
  overdueError: string;
  onViewOverdue: () => void;
  /** Null until the observer matching data loads. */
  unmatched: number | null;
  unmatchedError: string;
}

function kpiGroups({
  metrics,
  metricsFailed,
  overdue,
  overdueError,
  onViewOverdue,
  unmatched,
  unmatchedError,
}: DashboardData): KpiGroup[] {
  const metric = (
    label: string,
    read: (loaded: DashboardMetrics) => Reading,
    alert = false,
  ): Kpi => ({
    label,
    alert,
    ...(metricsFailed
      ? { value: "Unavailable", note: "Metrics could not be loaded" }
      : metrics
        ? read(metrics)
        : { value: "—", note: "Loading…" }),
  });
  return [
    {
      title: "Faculty evaluation",
      grid: "sm:grid-cols-2",
      kpis: [
        metric("Faculty Due This Cycle", (loaded) => ({
          value: String(loaded.facultyDue),
          note: "Active faculty due for evaluation",
        })),
        {
          label: "Overdue Evaluations",
          value: overdue ? String(overdue.count) : "—",
          note: overdueError
            ? "Unable to load overdue evaluations"
            : overdue
              ? "Requires committee attention"
              : "Loading overdue evaluations…",
          alert: true,
          action: {
            label: "View All",
            ariaLabel: "View all overdue evaluations",
            disabled: !overdue || Boolean(overdueError),
            onClick: onViewOverdue,
          },
        },
      ],
    },
    {
      title: "Assessment participation",
      grid: "sm:grid-cols-2 xl:max-w-160",
      kpis: [
        metric("Participation Rate", (loaded) =>
          rate(
            loaded.participation,
            "due faculty signed up",
            "No faculty are due this cycle",
          ),
        ),
        metric("Observer Utilization", (loaded) =>
          rate(
            loaded.observerUtilization,
            "signups have an observer",
            "No signups this cycle",
          ),
        ),
      ],
    },
    {
      title: "Recommendation engine",
      grid: "sm:grid-cols-2",
      kpis: [
        metric("List Sufficiency Rate", (loaded) =>
          rate(
            loaded.listSufficiency,
            `signups have a full list of ${loaded.listSize}`,
            "No signups this cycle",
          ),
        ),
        {
          label: "Unmatched Faculty",
          value: unmatchedError
            ? "Unavailable"
            : unmatched === null
              ? "Loading"
              : String(unmatched),
          note: "Need manual attention",
          alert: true,
        },
      ],
    },
    {
      title: "Survey & observation completion",
      grid: "sm:grid-cols-[6fr_7fr]",
      kpis: [
        {
          label: "Survey Completion Rate",
          value: "78%",
          note: "Process feedback received",
        },
        metric(
          "Missing Observations",
          (loaded) => ({
            value: String(loaded.missingObservations),
            note: "Approved observations past their date and not completed",
          }),
          true,
        ),
      ],
    },
  ];
}

function ListPanel({
  title,
  subtitle,
  action,
  children,
}: {
  title: string;
  subtitle: string;
  action: ReactNode;
  children: ReactNode;
}) {
  return (
    <section className="flex flex-col rounded-lg border border-line bg-white px-5.5 pt-5 pb-6">
      <h2 className="font-medium text-xl">{title}</h2>
      <p className="mt-1.5 text-muted text-sm">{subtitle}</p>
      <ul className="mt-8 flex flex-col gap-8">{children}</ul>
      <div className="mt-auto flex justify-end pt-10">{action}</div>
    </section>
  );
}

function ListRow({ cells, status }: { cells: string[]; status: ReactNode }) {
  return (
    <li className="grid grid-cols-[minmax(0,1fr)_144px] items-center gap-4">
      <span className="flex flex-wrap gap-x-6">
        {cells.map((cell) => (
          <span key={cell}>{cell}</span>
        ))}
      </span>
      {status}
    </li>
  );
}

export function AcOverview() {
  const {
    data: matching,
    error: matchingError,
    reload: reloadMatching,
  } = useObserverData<AcState>("/ac");
  const unmatchedFaculty = (matching?.signups ?? []).filter(
    (s) => s.candidates.length === 0,
  );
  const [queue, setQueue] = useState<OverdueQueue | null>(null);
  const [error, setError] = useState("");
  const [open, setOpen] = useState(false);
  const [reload, setReload] = useState(0);
  const [dueQueue, setDueQueue] = useState<OverdueQueue | null>(null);
  const [dueError, setDueError] = useState("");
  const [dueOpen, setDueOpen] = useState(false);
  const [metrics, setMetrics] = useState<DashboardMetrics | null>(null);
  const [metricsFailed, setMetricsFailed] = useState(false);
  // Retry deliberately reruns the request even though the request URL is unchanged.
  // biome-ignore lint/correctness/useExhaustiveDependencies: reload is an explicit retry trigger.
  useEffect(() => {
    const controller = new AbortController();
    setError("");
    setDueError("");
    setMetricsFailed(false);
    getDueQueue(controller.signal)
      .then(setDueQueue)
      .catch((cause) => {
        if (!controller.signal.aborted)
          setDueError(
            cause instanceof Error
              ? cause.message
              : "Unable to load due evaluations.",
          );
      });
    getOverdueQueue(controller.signal)
      .then(setQueue)
      .catch((cause) => {
        if (!controller.signal.aborted)
          setError(
            cause instanceof Error
              ? cause.message
              : "Unable to load overdue evaluations.",
          );
      });
    getDashboardMetrics(controller.signal)
      .then(setMetrics)
      .catch(() => {
        if (!controller.signal.aborted) setMetricsFailed(true);
      });
    return () => controller.abort();
  }, [reload]);
  const groups = kpiGroups({
    metrics,
    metricsFailed,
    overdue: queue,
    overdueError: error,
    onViewOverdue: () => setOpen(true),
    unmatched: matching ? unmatchedFaculty.length : null,
    unmatchedError: matchingError,
  });
  return (
    <div>
      <PageHeader
        title="Assessment Committee Dashboard"
        subtitle={queue?.cycleLabel ?? "Assessment overview"}
      />
      {error && (
        <p role="alert" className="mt-4 text-sm text-brand-red">
          {error}{" "}
          <button
            type="button"
            className="underline"
            onClick={() => setReload((value) => value + 1)}
          >
            Retry
          </button>
        </p>
      )}
      {open && queue && (
        <OverdueEvaluations
          queue={queue}
          onClose={() => setOpen(false)}
          onNotified={(professorId, sentAt) =>
            setQueue(
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
      {dueOpen && dueQueue && (
        <OverdueEvaluations
          kind="due"
          queue={dueQueue}
          onClose={() => setDueOpen(false)}
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

      <div className="mt-7.5 grid gap-x-10 gap-y-8.5 xl:grid-cols-[minmax(0,40rem)_minmax(0,1fr)]">
        {groups.map((group) => (
          <section key={group.title}>
            <h2 className="mb-1.5 font-semibold text-[15px] text-brand-green">
              {group.title}
            </h2>
            <div className={`grid gap-4 ${group.grid}`}>
              {group.kpis.map((kpi) => (
                <KpiCard key={kpi.label} kpi={kpi} />
              ))}
            </div>
          </section>
        ))}
      </div>

      <div className="mt-10 grid gap-5 lg:grid-cols-2">
        <ListPanel
          title="Faculty Due for Evaluation"
          subtitle="Upcoming evaluation deadlines"
          action={
            <button
              type="button"
              disabled={!dueQueue || Boolean(dueError)}
              onClick={() => setDueOpen(true)}
              aria-label="View all faculty due for evaluation"
              className="min-w-39.5 rounded-lg bg-brand-green px-5 py-3.25 text-white hover:opacity-90 disabled:opacity-50"
            >
              View All
            </button>
          }
        >
          {dueError ? (
            <li role="alert" className="text-sm text-brand-red">
              {dueError}{" "}
              <button
                type="button"
                className="underline"
                onClick={() => setReload((value) => value + 1)}
              >
                Retry due evaluations
              </button>
            </li>
          ) : !dueQueue ? (
            <li className="text-sm text-muted">Loading due evaluations…</li>
          ) : dueQueue.count === 0 ? (
            <li className="text-sm text-muted">
              No faculty are due this cycle.
            </li>
          ) : null}
          {!dueError &&
            dueQueue?.faculty
              .slice(0, 3)
              .map((item) => (
                <ListRow
                  key={item.professorId}
                  cells={[item.name, rankLabel(item.rank)]}
                  status={
                    <span className="text-brand-orange">
                      {dueQueue.cycleLabel}
                    </span>
                  }
                />
              ))}
        </ListPanel>

        <ListPanel
          title="Unmatched Faculty"
          subtitle="Signups with no eligible observers"
          action={
            <ActionButton tone="orange" to="/ac/matching" className="min-w-47">
              Resolve Matches
            </ActionButton>
          }
        >
          {matchingError && (
            <li role="alert" className="text-brand-red">
              {matchingError}
            </li>
          )}
          {!matching && !matchingError && <li>Loading unmatched faculty...</li>}
          {matching && !matchingError && unmatchedFaculty.length === 0 && (
            <li>No unmatched faculty.</li>
          )}
          {unmatchedFaculty.length > 0 && (
            <li role="alert" className="font-semibold text-brand-red">
              {unmatchedFaculty.length} signup(s) have no eligible observers.
            </li>
          )}
          {unmatchedFaculty.map((item) => (
            <ListRow
              key={item.signupId}
              cells={[item.name, item.department]}
              status={
                <span className="font-semibold text-brand-red text-sm">
                  0 candidates
                </span>
              }
            />
          ))}
        </ListPanel>
      </div>

      <CycleDeadlines cycle={matching?.cycle} onSaved={reloadMatching} />
    </div>
  );
}
