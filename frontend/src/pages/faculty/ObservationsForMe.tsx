import { Link } from "react-router-dom";
import {
  formatWhen,
  type ObservationSummary,
  professorObservationsPath,
  STATUS_LABELS,
  useApiResource,
} from "../../api/observations";
import { useAuth } from "../../auth/AuthContext";
import { Modal } from "../../components/Modal";
import { STATUS_COLORS } from "../../components/ObservationRecord";

const linkClass =
  "inline-flex items-center rounded-lg px-4 py-2 font-semibold text-sm hover:opacity-90";

/** What the observee can do with this observation. They cannot open it until the observer submits it. */
function Action({ observation }: { observation: ObservationSummary }) {
  const to = `/faculty/observations/${observation.observationId}`;
  const which = (
    <span className="sr-only">
      {" "}
      the {observation.semesterLabel} {observation.courseLabel} observation
    </span>
  );
  if (
    observation.status === "SUBMITTED" ||
    observation.status === "COMPLETED"
  ) {
    return (
      <Link to={to} className={`${linkClass} bg-brand-green text-white`}>
        View{which}
      </Link>
    );
  }
  return (
    <p className="text-muted text-sm">
      The observer has not submitted this observation yet.
    </p>
  );
}

export function FacultyObservationsForMe() {
  const { user } = useAuth();
  const id = user?.professorId;
  const { data, error, reload } = useApiResource<ObservationSummary[]>(
    id ? professorObservationsPath(id) : null,
  );
  const mine = data?.filter((o) => o.role === "OBSERVEE") ?? [];

  return (
    <Modal
      title="Observation Records for Me"
      subtitle="Observations of your classes, newest first."
    >
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
        <p>Loading observations...</p>
      ) : !mine.length ? (
        <p>No observations of your classes yet.</p>
      ) : (
        <ul className="space-y-4">
          {mine.map((o) => (
            <li
              key={o.observationId}
              className="flex flex-wrap items-center justify-between gap-4 rounded-lg border border-line p-4"
            >
              <div>
                <p className="font-semibold">{o.courseLabel}</p>
                <p className="mt-1 text-sm">Observer: {o.observerName}</p>
                <p className="text-muted text-sm">
                  {o.semesterLabel} ·{" "}
                  {formatWhen(o.observationDate, o.startTime) ??
                    "Not scheduled"}
                </p>
                <p
                  className={`mt-1 font-medium text-sm ${STATUS_COLORS[o.status]}`}
                >
                  {STATUS_LABELS[o.status]}
                </p>
              </div>
              <Action observation={o} />
            </li>
          ))}
        </ul>
      )}
    </Modal>
  );
}
