import { Fragment, useEffect, useId, useRef, useState } from "react";
import {
  getObservations,
  type ObservationStatus,
  type ObservationTracking,
  sendObservationReminders,
  type TrackedObservation,
} from "../../api/acDashboard";
import { ActionButton } from "../../components/ActionButton";
import { type Kpi, KpiCard } from "../../components/KpiCard";
import { PageHeader } from "../../components/PageHeader";
import { clockTime, shortDate } from "../../format";

type Reading = Pick<Kpi, "value" | "note">;

const STATUS: Record<ObservationStatus, { label: string; color: string }> = {
  AWAITING_OBSERVER: { label: "Awaiting Observer", color: "text-muted" },
  SCHEDULED: { label: "Scheduled", color: "text-brand-orange" },
  COMPLETED: { label: "Completed", color: "text-brand-green" },
  MISSING: { label: "Missing", color: "text-brand-red" },
};

function observationKpis(
  data: ObservationTracking | null,
  failed: boolean,
): Kpi[] {
  const reading = (read: (loaded: ObservationTracking) => Reading): Reading =>
    failed
      ? { value: "Unavailable", note: "Observations could not be loaded" }
      : data
        ? read(data)
        : { value: "—", note: "Loading…" };
  const count = (status: ObservationStatus, note: string): Reading =>
    reading((loaded) => ({
      value: String(
        loaded.observations.filter((o) => o.status === status).length,
      ),
      note,
    }));
  return [
    { label: "Scheduled", ...count("SCHEDULED", "Upcoming observations") },
    { label: "Completed", ...count("COMPLETED", "Completed this cycle") },
    {
      label: "Missing",
      alert: true,
      ...count("MISSING", "Past their date and not completed"),
    },
    {
      label: "Observation Deadline",
      alert: true,
      ...reading((loaded) => ({
        value: loaded.observationDeadline
          ? shortDate(loaded.observationDeadline)
          : "Not set",
        note: `${loaded.cycleLabel} cycle`,
      })),
    },
  ];
}

function dateTime({ date, startTime }: TrackedObservation) {
  if (!date) return null;
  return startTime
    ? `${shortDate(date)}, ${clockTime(startTime)}`
    : shortDate(date);
}

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

function ObservationDetails({
  observation,
  onClose,
}: {
  observation: TrackedObservation;
  onClose: () => void;
}) {
  const dialog = useRef<HTMLDialogElement>(null);
  const titleId = useId();
  useEffect(() => {
    const previous = document.activeElement as HTMLElement | null;
    dialog.current?.showModal();
    return () => previous?.focus();
  }, []);
  const details = [
    ["Observer", observation.observer ?? "Not assigned"],
    ["Date / Time", dateTime(observation) ?? "Not scheduled"],
    ["Signed by observee", observation.observeeSigned ? "Yes" : "No"],
    ["Signed by observer", observation.observerSigned ? "Yes" : "No"],
    [
      "Completed",
      // A completion time such as "2026-11-04T10:00:00". The date part is the day it was completed.
      observation.completedAt
        ? shortDate(observation.completedAt.slice(0, 10))
        : "Not recorded",
    ],
  ];
  return (
    <dialog
      ref={dialog}
      aria-labelledby={titleId}
      onCancel={(event) => {
        event.preventDefault();
        onClose();
      }}
      className="fixed inset-0 m-auto w-[calc(100%-2rem)] max-w-md rounded-xl border border-line bg-white p-6 text-ink shadow-xl backdrop:bg-ink/40"
    >
      <div className="flex items-start justify-between gap-4">
        <div>
          <h2 id={titleId} className="font-bold text-xl">
            Observation Details
          </h2>
          <p className="mt-1 text-sm text-muted">
            {observation.observee} • {observation.course}
          </p>
        </div>
        <button
          type="button"
          onClick={onClose}
          className="rounded-md border border-line bg-[#f7f6f2] px-6 py-2 text-sm hover:bg-mist"
        >
          Close
        </button>
      </div>
      <dl className="mt-6 grid grid-cols-[auto_1fr] gap-x-6 gap-y-3 text-sm">
        {details.map(([term, detail]) => (
          <Fragment key={term}>
            <dt className="text-muted">{term}</dt>
            <dd>{detail}</dd>
          </Fragment>
        ))}
      </dl>
    </dialog>
  );
}

export function AcObservations() {
  const [data, setData] = useState<ObservationTracking | null>(null);
  const [error, setError] = useState("");
  const [reload, setReload] = useState(0);
  const [viewing, setViewing] = useState<TrackedObservation | null>(null);
  const [reminding, setReminding] = useState(false);
  const [reminderResult, setReminderResult] = useState("");
  const [reminderError, setReminderError] = useState("");
  // Retry deliberately reruns the request even though the request URL is unchanged.
  // biome-ignore lint/correctness/useExhaustiveDependencies: reload is an explicit retry trigger.
  useEffect(() => {
    const controller = new AbortController();
    setError("");
    getObservations(controller.signal)
      .then(setData)
      .catch((cause) => {
        if (!controller.signal.aborted)
          setError(
            cause instanceof Error
              ? cause.message
              : "Unable to load observations.",
          );
      });
    return () => controller.abort();
  }, [reload]);

  async function remind() {
    setReminding(true);
    setReminderResult("");
    setReminderError("");
    try {
      const { sent } = await sendObservationReminders();
      setReminderResult(
        sent === 0
          ? "No one needs a reminder right now."
          : `Sent ${sent} reminder${sent === 1 ? "" : "s"}.`,
      );
    } catch (cause) {
      setReminderError(
        cause instanceof Error
          ? cause.message
          : "Could not send reminders. Please try again.",
      );
    } finally {
      setReminding(false);
    }
  }

  const observations = data?.observations ?? [];
  const kpis = observationKpis(data, Boolean(error) && !data);
  return (
    <div>
      <PageHeader
        title="Observation Tracking"
        subtitle="Monitor scheduled, completed, and missing observations during the cycle."
        action={
          <ActionButton
            tone="orange"
            className="min-w-52.5"
            disabled={
              reminding || !observations.some((o) => o.status !== "COMPLETED")
            }
            onClick={() => void remind()}
          >
            {reminding ? "Sending…" : "Send Reminders"}
          </ActionButton>
        }
      />
      {viewing && (
        <ObservationDetails
          observation={viewing}
          onClose={() => setViewing(null)}
        />
      )}
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
      {reminderError && (
        <p role="alert" className="mt-4 text-sm text-brand-red">
          {reminderError}
        </p>
      )}
      <p role="status" className="mt-4 text-sm text-brand-green empty:hidden">
        {reminderResult}
      </p>

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
            {!data && !error && (
              <tr>
                <td colSpan={6} className="pt-8 text-muted">
                  Loading observations…
                </td>
              </tr>
            )}
            {data && observations.length === 0 && (
              <tr>
                <td colSpan={6} className="pt-8 text-muted">
                  No observations to track yet. Signups appear here once their
                  observer list is generated.
                </td>
              </tr>
            )}
            {observations.map((row) => (
              <tr key={row.signupId} className="h-32.75 *:pr-4">
                <th scope="row" className="font-semibold">
                  {row.observee}
                </th>
                <td>{row.observer ?? <Blank label="Not assigned" />}</td>
                <td>{row.course}</td>
                <td>{dateTime(row) ?? <Blank label="Not scheduled" />}</td>
                <td className={STATUS[row.status].color}>
                  {STATUS[row.status].label}
                </td>
                <td>
                  {row.status === "COMPLETED" && (
                    <button
                      type="button"
                      aria-label={`View ${row.observee}'s observation`}
                      onClick={() => setViewing(row)}
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
