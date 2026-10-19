import { ActionButton } from "../../components/ActionButton";
import { type Kpi, KpiCard } from "../../components/KpiCard";
import { PageHeader } from "../../components/PageHeader";

type Status = "Scheduled" | "Completed" | "Missing";

const kpis: Kpi[] = [
  { label: "Scheduled", value: "12", note: "Upcoming observations" },
  { label: "Completed", value: "9", note: "Signed observations" },
  { label: "Missing", value: "4", note: "Require follow-up", alert: true },
  {
    label: "Observation Deadline",
    value: "Nov 13",
    note: "Fall 2026",
    alert: true,
  },
];

const observations: {
  observee: string;
  observer?: string;
  course: string;
  dateTime?: string;
  status: Status;
}[] = [
  {
    observee: "Professor 2",
    observer: "Professor 6",
    course: "CS 4348.501",
    dateTime: "Oct 3, 10:00 AM",
    status: "Scheduled",
  },
  {
    observee: "Professor 1",
    observer: "Professor 7",
    course: "CS 3345.002",
    dateTime: "Sep 28, 1:00 PM",
    status: "Completed",
  },
  { observee: "Professor 4", course: "CS 4337.001", status: "Missing" },
];

const STATUS_COLORS: Record<Status, string> = {
  Scheduled: "text-brand-orange",
  Completed: "text-brand-green",
  Missing: "text-brand-red",
};

// Column widths as a share of the table, measured from the Figma mockup
// (19%, 18.9%, 18.1%, 24.2% and 10.7%). The action column takes the rest.
const columns = [
  { label: "Observee", width: "w-19/100" },
  { label: "Observer", width: "w-189/1000" },
  { label: "Course", width: "w-181/1000" },
  { label: "Date / Time", width: "w-121/500" },
  { label: "Status", width: "w-107/1000" },
];

/** A dash for a missing value, with words for screen readers. */
function Blank({ label }: { label: string }) {
  return (
    <>
      <span aria-hidden="true">—</span>
      <span className="sr-only">{label}</span>
    </>
  );
}

export function AcObservations() {
  return (
    <div>
      <PageHeader
        title="Observation Tracking"
        subtitle="Monitor scheduled, completed, and missing observations during the cycle."
        action={
          <ActionButton tone="orange" className="min-w-52.5">
            Send Reminders
          </ActionButton>
        }
      />

      <div className="mt-10.25 grid max-w-324 gap-4 sm:grid-cols-2 lg:grid-cols-4">
        {kpis.map((kpi) => (
          <KpiCard key={kpi.label} kpi={kpi} />
        ))}
      </div>

      {/* The mockup leaves room below the rows, so the card keeps a minimum height.
          `relative` keeps the sr-only text inside the card when the table scrolls. */}
      <section className="relative mt-9.5 min-h-140 overflow-x-auto rounded-lg border border-line bg-white px-5.5">
        <table className="w-full min-w-4xl table-fixed text-left">
          <caption className="sr-only">Observations</caption>
          <thead className="text-muted text-sm">
            <tr>
              {columns.map((column) => (
                <th
                  key={column.label}
                  scope="col"
                  className={`pt-9 pr-4 font-medium ${column.width}`}
                >
                  {column.label}
                </th>
              ))}
              <th scope="col">
                <span className="sr-only">Action</span>
              </th>
            </tr>
          </thead>
          <tbody>
            {observations.map((row) => (
              <tr key={row.observee} className="h-32.75 *:pr-4">
                <th scope="row" className="font-semibold">
                  {row.observee}
                </th>
                <td>{row.observer ?? <Blank label="Not assigned" />}</td>
                <td>{row.course}</td>
                <td>{row.dateTime ?? <Blank label="Not scheduled" />}</td>
                <td className={STATUS_COLORS[row.status]}>{row.status}</td>
                <td>
                  {row.status === "Completed" && (
                    <button
                      type="button"
                      aria-label={`View ${row.observee}'s observation`}
                      className="h-9 w-17.5 rounded-lg bg-brand-green text-white hover:opacity-90"
                    >
                      View
                    </button>
                  )}
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </section>
    </div>
  );
}
