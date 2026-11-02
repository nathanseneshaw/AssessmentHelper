import { useEffect, useRef, useState } from "react";
import { Link, useNavigate } from "react-router-dom";
import {
  formatWhen,
  type ObservationSummary,
  professorObservationsPath,
  STATUS_LABELS,
  startObservation,
  useApiResource,
} from "../../api/observations";
import { observerChanged } from "../../api/observerEligibility";
import { useAuth } from "../../auth/AuthContext";
import { MissingObservations } from "../../components/MissingObservations";
import { Modal } from "../../components/Modal";
import { STATUS_COLORS } from "../../components/ObservationRecord";

const actionClass =
  "inline-flex shrink-0 items-center rounded-lg bg-brand-green px-4 py-2 font-semibold text-sm text-white hover:opacity-90 disabled:cursor-not-allowed disabled:opacity-50";

function ObservationItem({ row }: { row: ObservationSummary }) {
  const navigate = useNavigate();
  const [starting, setStarting] = useState(false);
  const [error, setError] = useState("");
  const path = `/faculty/observations/${row.observationId}`;
  const context = `${row.observeeName}, ${row.courseLabel}`;
  // Closing the list while a start is pending must not open the form afterwards.
  const mounted = useRef(true);
  useEffect(() => {
    mounted.current = true;
    return () => {
      mounted.current = false;
    };
  }, []);

  async function start() {
    setStarting(true);
    setError("");
    try {
      await startObservation(row.observationId);
      // The dashboard behind the dialog refreshes its observations.
      observerChanged();
      if (mounted.current) navigate(path);
    } catch (cause) {
      setError(
        cause instanceof Error
          ? cause.message
          : "Could not start the observation. Please try again.",
      );
      setStarting(false);
    }
  }

  return (
    <li className="rounded-lg border border-line p-4">
      <div className="flex flex-wrap items-start justify-between gap-3">
        <div>
          <p className="font-semibold">
            {row.courseLabel} · {row.observeeName}
          </p>
          <p className="mt-1 text-sm">
            {row.semesterLabel} ·{" "}
            {formatWhen(row.observationDate, row.startTime) ?? "Not scheduled"}
          </p>
          <p className={`text-sm ${STATUS_COLORS[row.status]}`}>
            {STATUS_LABELS[row.status]}
          </p>
        </div>
        {row.status === "ACCEPTED" || row.status === "SCHEDULED" ? (
          <button
            type="button"
            className={actionClass}
            disabled={starting}
            aria-label={`${starting ? "Starting" : "Start"} observation of ${context}`}
            onClick={start}
          >
            {starting ? "Starting…" : "Start observation"}
          </button>
        ) : (
          <Link
            to={path}
            className={actionClass}
            aria-label={`${row.status === "IN_PROGRESS" ? "Continue" : "View"} observation of ${context}`}
          >
            {row.status === "IN_PROGRESS" ? "Continue" : "View"}
          </Link>
        )}
      </div>
      {error && (
        <p role="alert" className="mt-3 text-brand-red text-sm">
          {error}
        </p>
      )}
    </li>
  );
}

export function FacultyObservationsForOthers() {
  const { user } = useAuth();
  const id = user?.professorId;
  const { data, error, reload } = useApiResource<ObservationSummary[]>(
    id ? professorObservationsPath(id) : null,
  );
  const rows = data?.filter((row) => row.role === "OBSERVER") ?? [];

  return (
    <Modal
      title="Observation Records for Others"
      subtitle="Classroom observations you do for other professors."
    >
      <MissingObservations professorId={id} observerOnly />
      {!id ? (
        <p role="alert" className="text-brand-red">
          Your session has no faculty profile. Sign out and sign in again.
        </p>
      ) : error ? (
        <p role="alert" className="text-brand-red">
          {error}{" "}
          <button type="button" className="underline" onClick={reload}>
            Retry
          </button>
        </p>
      ) : !data ? (
        <p role="status">Loading observations...</p>
      ) : !rows.length ? (
        <p>
          You have no observations to do yet. Observations appear here when you
          accept an observer request.
        </p>
      ) : (
        <ul className="space-y-4">
          {rows.map((row) => (
            <ObservationItem key={row.observationId} row={row} />
          ))}
        </ul>
      )}
    </Modal>
  );
}
