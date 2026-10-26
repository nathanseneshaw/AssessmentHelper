import { type ReactNode, useEffect, useId, useRef, useState } from "react";
import { getEvaluationQueue } from "../../api/evaluationEligibility";
import { ActionButton } from "../../components/ActionButton";
import { PageHeader } from "../../components/PageHeader";

type Rank = "Assistant" | "Associate" | "Full";
// Shown in capitals; stored in sentence case so screen readers say the word.
type Status = "Due" | "Upcoming" | "Overdue";

interface QueueRow {
  professorId: number;
  name: string;
  rank: Rank;
  lastEvaluation: string;
  nextDue: string;
  status: Status;
  action: string;
}

interface Filter {
  label: string;
  matches: (row: QueueRow) => boolean;
}

const ALL_FACULTY: Filter = { label: "All Faculty", matches: () => true };

const filters: Filter[] = [
  ALL_FACULTY,
  { label: "Due This Term", matches: (row) => row.status === "Due" },
  { label: "Overdue", matches: (row) => row.status === "Overdue" },
  { label: "Assistant", matches: (row) => row.rank === "Assistant" },
  { label: "Associate", matches: (row) => row.rank === "Associate" },
  { label: "Full", matches: (row) => row.rank === "Full" },
];

const STATUS_COLORS: Record<Status, string> = {
  Due: "text-brand-orange",
  Upcoming: "text-brand-green",
  Overdue: "text-brand-red",
};

// Each column's share of the table width, measured from the mockup.
// The Action column takes what is left.
const columns = [
  { label: "Faculty", className: "w-[24.25%] pr-6 pl-5.5" },
  { label: "Rank", className: "w-[14%] pr-6" },
  { label: "Last Evaluation", className: "w-[14.65%] pr-6" },
  { label: "Next Due", className: "w-[13.25%] pr-6" },
  { label: "Status", className: "w-[16.9%] pr-6" },
  { label: "Action", className: "pr-5.5" },
];

function QueueOverlay({
  children,
  headingId,
  filter,
  count,
  onClose,
}: {
  children: ReactNode;
  headingId: string;
  filter: string;
  count: number;
  onClose: () => void;
}) {
  const dialog = useRef<HTMLDialogElement>(null);
  useEffect(() => {
    const previous = document.activeElement as HTMLElement | null;
    const overflow = document.body.style.overflow;
    dialog.current?.showModal();
    document.body.style.overflow = "hidden";
    return () => {
      document.body.style.overflow = overflow;
      previous?.focus();
    };
  }, []);
  return (
    <dialog
      ref={dialog}
      aria-labelledby={headingId}
      onCancel={(event) => {
        event.preventDefault();
        onClose();
      }}
      className="fixed inset-0 m-auto max-h-[85vh] w-[calc(100%-2rem)] max-w-6xl rounded-xl border border-line bg-white p-6 text-ink shadow-xl backdrop:bg-ink/40"
    >
      <div className="mb-5 flex items-start justify-between gap-4">
        <div>
          <h2 id={headingId} className="font-bold text-xl">
            Evaluation queue - {filter}
          </h2>
          <p className="mt-1 text-sm text-muted">{count} faculty</p>
        </div>
        <button
          type="button"
          onClick={onClose}
          className="rounded-md border border-line px-5 py-2 text-sm hover:bg-mist"
        >
          Close
        </button>
      </div>
      <div className="max-h-[60vh] overflow-auto">{children}</div>
    </dialog>
  );
}

export function AcFacultyEvaluations() {
  const [active, setActive] = useState(ALL_FACULTY);
  const [queue, setQueue] = useState<QueueRow[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [reload, setReload] = useState(0);
  const [viewAll, setViewAll] = useState(false);
  const overlayHeadingId = useId();
  // biome-ignore lint/correctness/useExhaustiveDependencies: reload explicitly triggers retry.
  useEffect(() => {
    const controller = new AbortController();
    setLoading(true);
    setError("");
    setQueue([]);
    getEvaluationQueue(controller.signal)
      .then((result) => {
        if (controller.signal.aborted) return;
        setQueue(
          result.faculty.map((faculty) => ({
            professorId: faculty.professorId,
            name: faculty.name,
            rank: (
              {
                ASSISTANT: "Assistant",
                ASSOCIATE: "Associate",
                FULL: "Full",
              } as const
            )[faculty.rank as "ASSISTANT" | "ASSOCIATE" | "FULL"],
            lastEvaluation: faculty.lastEvaluationCycle,
            nextDue:
              faculty.nextDueSemester ??
              (faculty.status === "Due"
                ? result.cycleLabel
                : faculty.nextDueDate),
            status: faculty.status,
            action:
              faculty.status === "Upcoming"
                ? "View Past Evaluations"
                : "View Evaluation",
          })),
        );
      })
      .catch((cause) => {
        if (!controller.signal.aborted)
          setError(
            cause instanceof Error
              ? cause.message
              : "Unable to load faculty evaluations.",
          );
      })
      .finally(() => {
        if (!controller.signal.aborted) setLoading(false);
      });
    return () => controller.abort();
  }, [reload]);
  const headingId = useId();
  const rows = queue.filter(active.matches);

  const renderTable = (tableRows: QueueRow[], labelId: string) => (
    <table
      aria-labelledby={labelId}
      className="w-full whitespace-nowrap text-left"
    >
      <thead className="sticky top-0 bg-white">
        <tr className="text-muted text-sm leading-4">
          {columns.map((col) => (
            <th
              key={col.label}
              scope="col"
              className={`pt-7 font-medium ${col.className}`}
            >
              {col.label === "Next Due" && active.label === "Overdue"
                ? "Was Due"
                : col.label}
            </th>
          ))}
        </tr>
      </thead>
      <tbody>
        {tableRows.map((row) => (
          <tr key={row.professorId} className="h-20">
            <td className="pr-6 pl-5.5 font-semibold">{row.name}</td>
            <td className="pr-6">{row.rank}</td>
            <td className="pr-6">{row.lastEvaluation}</td>
            <td className="min-w-40 whitespace-normal pr-6">{row.nextDue}</td>
            <td
              className={`pr-6 font-bold text-[13px] uppercase ${STATUS_COLORS[row.status]}`}
            >
              {row.status}
            </td>
            <td className="pr-5.5">
              <ActionButton
                tone="green"
                ariaLabel={`${row.action} for ${row.name}`}
                className="min-w-56.25"
              >
                {row.action}
              </ActionButton>
            </td>
          </tr>
        ))}
        {tableRows.length === 0 && (
          <tr className="h-20">
            <td colSpan={columns.length} className="px-5.5 text-muted">
              {loading
                ? "Loading faculty evaluations…"
                : error
                  ? "Faculty evaluations could not be loaded."
                  : "No faculty match this filter."}
            </td>
          </tr>
        )}
      </tbody>
    </table>
  );

  return (
    <div>
      <PageHeader
        title="Faculty Evaluations"
        subtitle="Review who is due or overdue and initiate the evaluation cycle."
      />

      <section aria-labelledby={headingId} className="mt-8.75">
        {error && (
          <p role="alert" className="mb-4 text-brand-red">
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
        <h2 id={headingId} className="font-medium text-2xl tracking-tight">
          Evaluation queue
        </h2>

        {/* A floated legend sits in the flex row like any other item. */}
        <fieldset className="mt-3.75 flex min-w-0 flex-wrap gap-x-2 text-[15px] text-muted leading-5">
          <legend className="float-left">Filters:</legend>
          <div className="flex flex-wrap gap-x-5 gap-y-1">
            {filters.map((f) => (
              <button
                key={f.label}
                type="button"
                aria-pressed={f === active}
                onClick={() => setActive(f)}
                className={
                  f === active
                    ? "text-ink underline decoration-brand-green underline-offset-4"
                    : "hover:text-ink"
                }
              >
                {f.label}
              </button>
            ))}
          </div>
        </fieldset>
        <p aria-live="polite" className="sr-only">
          {rows.length} of {queue.length} faculty shown
        </p>

        <div className="mt-6 overflow-x-auto rounded-lg border border-line bg-white">
          {renderTable(rows.slice(0, 4), headingId)}
        </div>
        <div className="mt-4 flex items-center justify-between gap-4">
          <p className="text-sm text-muted">
            Showing {Math.min(4, rows.length)} of {rows.length} matching faculty
          </p>
          <button
            type="button"
            disabled={loading || Boolean(error) || rows.length === 0}
            onClick={() => setViewAll(true)}
            aria-label="View all matching faculty"
            className="rounded-lg bg-brand-green px-5 py-2.5 text-white hover:opacity-90 disabled:opacity-50"
          >
            View All
          </button>
        </div>
        {viewAll && (
          <QueueOverlay
            headingId={overlayHeadingId}
            filter={active.label}
            count={rows.length}
            onClose={() => setViewAll(false)}
          >
            {renderTable(rows, overlayHeadingId)}
          </QueueOverlay>
        )}
      </section>
    </div>
  );
}
