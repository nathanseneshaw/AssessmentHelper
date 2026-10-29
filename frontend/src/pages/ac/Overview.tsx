import { type ReactNode, useEffect, useState } from "react";
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

const kpiGroups: { title: string; grid: string; kpis: Kpi[] }[] = [
  {
    title: "Faculty evaluation",
    grid: "sm:grid-cols-2",
    kpis: [
      {
        label: "Evaluation Eligibility Accuracy",
        value: "96%",
        note: "Faculty correctly identified",
      },
      {
        label: "Overdue Evaluations",
        value: "—",
        note: "Requires committee attention",
        alert: true,
        action: {
          label: "View All",
          ariaLabel: "View all overdue evaluations",
        },
      },
    ],
  },
  {
    title: "Assessment participation",
    grid: "sm:grid-cols-2 xl:max-w-160",
    kpis: [
      {
        label: "Participation Rate",
        value: "82%",
        note: "# prof signed up / # of prof due",
      },
      {
        label: "Observer Utilization",
        value: "74%",
        note: "# of observers / # of prof signed up",
      },
    ],
  },
  {
    title: "Recommendation engine",
    grid: "sm:grid-cols-2",
    kpis: [
      {
        label: "List Sufficiency Rate",
        value: "91%",
        note: "Avg # of observer matches / # sign-ups",
      },
      {
        label: "Unmatched Faculty",
        value: "2",
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
      {
        label: "Missing Observations",
        value: "4",
        note: "Approved observations that are not completed",
        alert: true,
      },
    ],
  },
];

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
  // Retry deliberately reruns the request even though the request URL is unchanged.
  // biome-ignore lint/correctness/useExhaustiveDependencies: reload is an explicit retry trigger.
  useEffect(() => {
    const controller = new AbortController();
    setError("");
    setDueError("");
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
    return () => controller.abort();
  }, [reload]);
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
        {kpiGroups.map((group) => (
          <section key={group.title}>
            <h2 className="mb-1.5 font-semibold text-[15px] text-brand-green">
              {group.title}
            </h2>
            <div className={`grid gap-4 ${group.grid}`}>
              {group.kpis.map((kpi) => (
                <KpiCard
                  key={kpi.label}
                  kpi={
                    kpi.label === "Overdue Evaluations"
                      ? {
                          ...kpi,
                          value: queue ? String(queue.count) : "—",
                          note: error
                            ? "Unable to load overdue evaluations"
                            : queue
                              ? kpi.note
                              : "Loading overdue evaluations…",
                          action: {
                            label: "View All",
                            ariaLabel: "View all overdue evaluations",
                            disabled: !queue || Boolean(error),
                            onClick: () => setOpen(true),
                          },
                        }
                      : kpi.label === "Unmatched Faculty"
                        ? {
                            ...kpi,
                            value: matchingError
                              ? "Unavailable"
                              : matching
                                ? String(unmatchedFaculty.length)
                                : "Loading",
                          }
                        : kpi
                  }
                />
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
