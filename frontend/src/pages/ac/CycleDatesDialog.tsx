import { type FormEvent, useEffect, useRef, useState } from "react";
import { apiFetch } from "../../api/client";

export interface CycleDates {
  cycleId: number;
  term: string;
  year: number;
  startDate: string;
  endDate: string;
  signupDeadline: string;
  observationDeadline: string;
  feedbackDeadline: string;
}

export function CycleDatesDialog({
  cycle,
  onClose,
  onSaved,
}: {
  cycle: CycleDates;
  onClose: () => void;
  onSaved: () => void;
}) {
  const dialog = useRef<HTMLDialogElement>(null);
  const [values, setValues] = useState(cycle);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  useEffect(() => {
    dialog.current?.showModal();
  }, []);

  async function submit(event: FormEvent) {
    event.preventDefault();
    setBusy(true);
    setError("");
    try {
      await apiFetch(`/api/ac/cycles/${cycle.cycleId}/dates`, {
        method: "PUT",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify(values),
      });
      onSaved();
    } catch (cause) {
      setError(
        cause instanceof Error ? cause.message : "Could not save dates.",
      );
    } finally {
      setBusy(false);
    }
  }
  function field(
    label: string,
    name:
      | "startDate"
      | "endDate"
      | "signupDeadline"
      | "observationDeadline"
      | "feedbackDeadline",
  ) {
    return (
      <label className="block text-sm">
        {label}
        <input
          type="date"
          required
          className="mt-1 block w-full rounded border border-line p-2"
          value={values[name] ?? ""}
          onChange={(event) =>
            setValues((previous) => ({
              ...previous,
              [name]: event.target.value,
            }))
          }
        />
      </label>
    );
  }
  return (
    <dialog
      ref={dialog}
      aria-labelledby="edit-cycle-title"
      onCancel={(event) => {
        event.preventDefault();
        if (!busy) onClose();
      }}
      className="fixed inset-0 m-auto max-h-[90vh] w-[calc(100%-2rem)] max-w-xl overflow-y-auto rounded-xl border border-line bg-white p-6 shadow-xl backdrop:bg-ink/40"
    >
      <h2 id="edit-cycle-title" className="font-bold text-xl">
        Edit {cycle.term} {cycle.year} cycle dates
      </h2>
      <form onSubmit={(event) => void submit(event)} className="mt-4 space-y-4">
        <div className="grid gap-3 sm:grid-cols-2">
          {field("Semester starts", "startDate")}
          {field("Semester ends", "endDate")}
        </div>
        <div className="grid gap-3 sm:grid-cols-3">
          {field("Signup deadline", "signupDeadline")}
          {field("Observation deadline", "observationDeadline")}
          {field("Feedback deadline", "feedbackDeadline")}
        </div>
        {error && (
          <p role="alert" className="text-sm text-brand-red">
            {error}
          </p>
        )}
        <div className="flex justify-end gap-3">
          <button
            type="button"
            disabled={busy}
            onClick={onClose}
            className="rounded border border-line px-4 py-2"
          >
            Cancel
          </button>
          <button
            type="submit"
            disabled={busy}
            className="rounded bg-brand-orange px-4 py-2 text-white"
          >
            {busy ? "Saving…" : "Save dates"}
          </button>
        </div>
      </form>
    </dialog>
  );
}
