import { useId, useState } from "react";
import { ActionButton } from "../../components/ActionButton";
import { PageHeader } from "../../components/PageHeader";

type Rank = "Assistant" | "Associate" | "Full";
// Shown in capitals; stored in sentence case so screen readers say the word.
type Status = "Due" | "Upcoming" | "Overdue";

interface QueueRow {
  name: string;
  rank: Rank;
  lastEvaluation: string;
  nextDue: string;
  status: Status;
  action: string;
}

// Copied from the Figma mockup, including its order.
const queue: QueueRow[] = [
  {
    name: "Professor 1",
    rank: "Assistant",
    lastEvaluation: "Fall 2025",
    nextDue: "Fall 2026",
    status: "Due",
    action: "View Evaluation",
  },
  {
    name: "Professor 4",
    rank: "Associate",
    lastEvaluation: "Spring 2024",
    nextDue: "Spring 2026",
    status: "Upcoming",
    action: "View Past Evaluations",
  },
  {
    name: "Professor 2",
    rank: "Associate",
    lastEvaluation: "Spring 2025",
    nextDue: "Spring 2027",
    status: "Upcoming",
    action: "View Past Evaluations",
  },
  {
    name: "Professor 5",
    rank: "Full",
    lastEvaluation: "Fall 2024",
    nextDue: "Fall 2026",
    status: "Due",
    action: "View Evaluation",
  },
];

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

export function AcFacultyEvaluations() {
  const [active, setActive] = useState(ALL_FACULTY);
  const headingId = useId();
  const rows = queue.filter(active.matches);

  return (
    <div>
      <PageHeader
        title="Faculty Evaluations"
        subtitle="Review who is due or overdue and initiate the evaluation cycle."
      />

      <section aria-labelledby={headingId} className="mt-8.75">
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

        {/* The minimum height comes from the mockup, and keeps the card the
            same size while filtering. Narrow screens scroll the table sideways. */}
        <div className="mt-6 min-h-160 overflow-x-auto rounded-lg border border-line bg-white">
          <table
            aria-labelledby={headingId}
            className="w-full whitespace-nowrap text-left"
          >
            <thead>
              <tr className="text-muted text-sm leading-4">
                {columns.map((col) => (
                  <th
                    key={col.label}
                    scope="col"
                    className={`pt-7 font-medium ${col.className}`}
                  >
                    {col.label}
                  </th>
                ))}
              </tr>
            </thead>
            <tbody>
              {rows.map((row) => (
                <tr key={row.name} className="h-28.75">
                  <td className="pr-6 pl-5.5 font-semibold">{row.name}</td>
                  <td className="pr-6">{row.rank}</td>
                  <td className="pr-6">{row.lastEvaluation}</td>
                  <td className="pr-6">{row.nextDue}</td>
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
              {rows.length === 0 && (
                <tr className="h-28.75">
                  <td colSpan={columns.length} className="px-5.5 text-muted">
                    No faculty match this filter.
                  </td>
                </tr>
              )}
            </tbody>
          </table>
        </div>
      </section>
    </div>
  );
}
