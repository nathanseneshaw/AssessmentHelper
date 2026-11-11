import { useEffect, useId, useRef, useState } from "react";
import {
  type EvaluationRecord,
  evaluationRecordsPath,
  evaluationStatusLabel,
} from "../api/evaluations";
import {
  formatWhen,
  type ObservationDetail,
  type ObservationSummary,
  observationPath,
  professorObservationsPath,
  STATUS_LABELS,
  useApiResource,
} from "../api/observations";
import { DeferralDetails } from "./DeferralDetails";
import { ObservationRecord } from "./ObservationRecord";

function CycleObservations({
  professorId,
  cycleId,
}: {
  professorId: number;
  cycleId: number;
}) {
  const { data, error, reload } = useApiResource<ObservationSummary[]>(
    professorObservationsPath(professorId),
  );
  const [selected, setSelected] = useState<number | null>(null);
  const {
    data: form,
    error: formError,
    reload: reloadForm,
  } = useApiResource<ObservationDetail>(
    selected === null ? null : observationPath(selected),
  );
  const observations = data?.filter(
    (observation) =>
      observation.cycleId === cycleId && observation.role === "OBSERVEE",
  );
  return (
    <div className="mt-4 border-t border-line pt-4">
      {error ? (
        <p role="alert">
          {error}{" "}
          <button type="button" className="underline" onClick={reload}>
            Retry
          </button>
        </p>
      ) : !observations ? (
        <p role="status">Loading observations...</p>
      ) : observations.length === 0 ? (
        <p>No observations are recorded for this cycle.</p>
      ) : (
        <ul className="space-y-4">
          {observations.map((observation) => (
            <li
              key={observation.observationId}
              className="rounded-lg bg-mist p-4"
            >
              <h4 className="font-semibold">{observation.courseLabel}</h4>
              <p className="mt-1 text-sm">
                Observer: {observation.observerName}
              </p>
              <p className="text-sm">
                {formatWhen(
                  observation.observationDate,
                  observation.startTime,
                ) ?? "Not scheduled"}
              </p>
              <p className="text-sm">{STATUS_LABELS[observation.status]}</p>
              {observation.status === "COMPLETED" && (
                <button
                  type="button"
                  className="mt-3 rounded bg-brand-green px-4 py-2 text-sm text-white"
                  onClick={() => setSelected(observation.observationId)}
                >
                  View Observation
                </button>
              )}
            </li>
          ))}
        </ul>
      )}
      {selected !== null && (
        <section aria-label="Completed observation form" className="mt-5">
          <div className="mb-4 flex items-center justify-between gap-4">
            <h4 className="font-semibold">Observation Form</h4>
            <button
              type="button"
              className="underline"
              onClick={() => setSelected(null)}
            >
              Close form
            </button>
          </div>
          {formError ? (
            <p role="alert">
              {formError}{" "}
              <button type="button" className="underline" onClick={reloadForm}>
                Retry
              </button>
            </p>
          ) : !form ? (
            <p role="status">Loading observation form...</p>
          ) : form.status === "COMPLETED" ? (
            <ObservationRecord observation={form} />
          ) : (
            <p>This observation is not completed yet.</p>
          )}
        </section>
      )}
    </div>
  );
}

function EvaluationHistoryItem({
  record,
  professorId,
}: {
  record: EvaluationRecord;
  professorId: number;
}) {
  const [expanded, setExpanded] = useState(false);
  const overviewId = useId();
  return (
    <li className="rounded-lg border border-line p-4">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <div>
          <h3 className="font-semibold">
            {record.term} {record.academicYear}
          </h3>
          <p className="mt-1 text-sm">
            {evaluationStatusLabel(record.status)}
            {evaluationStatusLabel(record.status) !== "In progress" &&
              `: ${record.observationCount} observation(s)`}
          </p>
        </div>
        <button
          type="button"
          aria-expanded={expanded}
          aria-controls={overviewId}
          className="rounded bg-brand-green px-4 py-2 text-sm text-white"
          onClick={() => setExpanded(!expanded)}
        >
          View Observation(s)
        </button>
      </div>
      {record.deferral && <DeferralDetails details={record.deferral} />}
      <div id={overviewId}>
        {expanded && (
          <CycleObservations
            professorId={professorId}
            cycleId={record.cycleId}
          />
        )}
      </div>
    </li>
  );
}

export function FacultyEvaluationHistory({
  professorId,
  name,
  onClose,
}: {
  professorId: number;
  name: string;
  onClose: () => void;
}) {
  const dialog = useRef<HTMLDialogElement>(null);
  const titleId = useId();
  const { data, error, reload } = useApiResource<EvaluationRecord[]>(
    evaluationRecordsPath(professorId),
  );
  useEffect(() => {
    const previous = document.activeElement as HTMLElement | null;
    dialog.current?.showModal();
    return () => previous?.focus();
  }, []);
  return (
    <dialog
      ref={dialog}
      aria-labelledby={titleId}
      onCancel={(event) => {
        event.preventDefault();
        onClose();
      }}
      className="fixed inset-0 m-auto max-h-[85vh] w-[calc(100%-2rem)] max-w-2xl overflow-y-auto rounded-xl border border-line bg-white p-6 text-ink shadow-xl backdrop:bg-ink/40"
    >
      <div className="flex items-start justify-between gap-4">
        <div>
          <h2 id={titleId} className="text-xl font-bold">
            Evaluation History
          </h2>
          <p className="mt-1 text-muted">{name}</p>
        </div>
        <button
          type="button"
          className="cursor-pointer rounded border border-line px-4 py-2 hover:bg-mist"
          onClick={onClose}
        >
          Close
        </button>
      </div>
      {error ? (
        <p role="alert" className="mt-4 text-brand-red">
          {error}{" "}
          <button type="button" className="underline" onClick={reload}>
            Retry
          </button>
        </p>
      ) : !data ? (
        <p role="status" className="mt-4">
          Loading evaluation history...
        </p>
      ) : !data.length ? (
        <p className="mt-4">No evaluation history is recorded.</p>
      ) : (
        <ul className="mt-4 space-y-4">
          {data.map((record) => (
            <EvaluationHistoryItem
              key={record.cycleId}
              record={record}
              professorId={professorId}
            />
          ))}
        </ul>
      )}
    </dialog>
  );
}
