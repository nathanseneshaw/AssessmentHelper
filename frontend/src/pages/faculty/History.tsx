import { Link, useSearchParams } from "react-router-dom";
import {
  type EvaluationRecord,
  evaluationRecordsPath,
  evaluationStatusLabel,
  observationCountLabel,
} from "../../api/evaluations";
import {
  formatTimestamp,
  formatWhen,
  type ObservationSummary,
  professorObservationsPath,
  STATUS_LABELS,
  useApiResource,
} from "../../api/observations";
import { useAuth } from "../../auth/AuthContext";
import { Modal } from "../../components/Modal";

const cell = "border-line border-t px-3 py-3 align-top";

function StatusBadge({ status }: { status: string | null }) {
  const completed = status?.toUpperCase() === "COMPLETED";
  return (
    <span
      className={`rounded-md px-3 py-1 font-medium text-xs ${
        completed ? "bg-mist text-brand-green" : "bg-peach text-brand-orange"
      }`}
    >
      {evaluationStatusLabel(status)}
    </span>
  );
}

const termOf = (record: EvaluationRecord) =>
  `${record.term} ${record.academicYear}`;

function RecordsTable({
  records,
  selected,
}: {
  records: EvaluationRecord[];
  selected: number | null;
}) {
  return (
    <div className="overflow-x-auto">
      <table className="w-full text-left text-sm">
        <caption className="sr-only">Evaluation cycles, newest first</caption>
        <thead className="text-ink/60 text-xs">
          <tr>
            <th scope="col" className="px-3 pb-2 font-medium">
              Semester
            </th>
            <th scope="col" className="px-3 pb-2 font-medium">
              Status
            </th>
            <th scope="col" className="px-3 pb-2 font-medium">
              Observations
            </th>
            <th scope="col" className="px-3 pb-2 font-medium">
              Last observation completed
            </th>
            <th scope="col" className="px-3 pb-2 font-medium">
              <span className="sr-only">Details</span>
            </th>
          </tr>
        </thead>
        <tbody>
          {records.map((record) => (
            <tr
              key={record.cycleId}
              className={record.cycleId === selected ? "bg-mist/40" : ""}
            >
              <th scope="row" className={`${cell} font-semibold`}>
                {termOf(record)}
              </th>
              <td className={cell}>
                <StatusBadge status={record.status} />
              </td>
              <td className={cell}>
                {observationCountLabel(record.observationCount)}
              </td>
              <td className={`${cell} text-ink/70`}>
                {record.lastObservationCompletedAt
                  ? formatTimestamp(record.lastObservationCompletedAt)
                  : "Not completed"}
              </td>
              <td className={cell}>
                <Link
                  to={`/faculty/history?cycle=${record.cycleId}`}
                  aria-current={
                    record.cycleId === selected ? "true" : undefined
                  }
                  className="font-semibold text-brand-green text-xs hover:underline"
                >
                  Details<span className="sr-only"> ({termOf(record)})</span>
                </Link>
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}

/** The observations of the professor's own classes in one cycle. */
function CycleDetail({ record }: { record: EvaluationRecord }) {
  const { user } = useAuth();
  const id = user?.professorId;
  const { data, error, reload } = useApiResource<ObservationSummary[]>(
    id ? professorObservationsPath(id) : null,
  );
  const mine = data?.filter(
    (o) => o.role === "OBSERVEE" && o.cycleId === record.cycleId,
  );
  return (
    <section aria-labelledby="cycle-detail-title" className="mt-6">
      <h3 id="cycle-detail-title" className="font-semibold text-lg">
        {termOf(record)} observations
      </h3>
      {error ? (
        <p role="alert" className="mt-2 text-brand-red text-sm">
          {error}{" "}
          <button type="button" className="underline" onClick={reload}>
            Retry
          </button>
        </p>
      ) : !mine ? (
        <p className="mt-2 text-ink/60 text-sm">Loading observations...</p>
      ) : !mine.length ? (
        <p className="mt-2 text-ink/60 text-sm">
          None of your classes were observed in this cycle.
        </p>
      ) : (
        <ul className="mt-3 flex flex-col">
          {mine.map((o) => {
            const viewable =
              o.status === "SUBMITTED" || o.status === "COMPLETED";
            return (
              <li
                key={o.observationId}
                className="flex flex-wrap items-center justify-between gap-3 border-line border-t py-3"
              >
                <div>
                  <p className="font-semibold text-sm">{o.courseLabel}</p>
                  <p className="text-ink/60 text-xs">
                    Observer: {o.observerName}
                  </p>
                  <p className="text-ink/60 text-xs">
                    {STATUS_LABELS[o.status]} •{" "}
                    {formatWhen(o.observationDate, o.startTime) ??
                      "Not scheduled"}
                  </p>
                </div>
                {viewable ? (
                  <Link
                    to={`/faculty/observations/${o.observationId}`}
                    className="rounded-md bg-brand-green px-4 py-2 font-medium text-white text-xs"
                  >
                    View<span className="sr-only"> {o.courseLabel}</span>
                  </Link>
                ) : (
                  <p className="text-muted text-xs">Not submitted yet</p>
                )}
              </li>
            );
          })}
        </ul>
      )}
    </section>
  );
}

export function FacultyHistory() {
  const { user } = useAuth();
  const id = user?.professorId;
  const [params] = useSearchParams();
  const requested = Number(params.get("cycle")) || null;
  const { data, error, reload } = useApiResource<EvaluationRecord[]>(
    id ? evaluationRecordsPath(id) : null,
  );
  const selected = data?.find((r) => r.cycleId === requested) ?? null;

  return (
    <Modal
      title="Evaluation History"
      subtitle="Your evaluation cycles, newest first."
      wide
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
        <p className="text-ink/60 text-sm">Loading evaluation history...</p>
      ) : !data.length ? (
        <p className="text-ink/60 text-sm">
          You have not been part of an evaluation cycle yet.
        </p>
      ) : (
        <>
          <RecordsTable records={data} selected={selected?.cycleId ?? null} />
          {selected && <CycleDetail record={selected} />}
          {requested && !selected && (
            <p role="alert" className="mt-4 text-brand-red text-sm">
              You have no evaluation for that cycle.
            </p>
          )}
        </>
      )}
    </Modal>
  );
}
