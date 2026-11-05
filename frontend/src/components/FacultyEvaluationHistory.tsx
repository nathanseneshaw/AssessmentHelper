import { useEffect, useId, useRef } from "react";
import {
  type EvaluationRecord,
  evaluationRecordsPath,
  evaluationStatusLabel,
} from "../api/evaluations";
import { useApiResource } from "../api/observations";
import { DeferralDetails } from "./DeferralDetails";

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
            <li
              key={record.cycleId}
              className="rounded-lg border border-line p-4"
            >
              <h3 className="font-semibold">
                {record.term} {record.academicYear}
              </h3>
              <p className="mt-1 text-sm">
                {evaluationStatusLabel(record.status)} ?{" "}
                {record.observationCount} observation(s)
              </p>
              {record.deferral && <DeferralDetails details={record.deferral} />}
            </li>
          ))}
        </ul>
      )}
    </dialog>
  );
}
