import { type FormEvent, useEffect, useRef, useState } from "react";
import { ApiError, apiFetch } from "../../api/client";
import type { ImportError } from "../../api/imports";
import { CycleImportProblems } from "./CycleImportProblems";
import { TemplateDownload } from "./TemplateDownload";

interface SemesterOption {
  term: string;
  year: number;
  startDate: string;
  endDate: string;
  estimated: boolean;
  hasCycle: boolean;
}

export function CycleSetupDialog({
  onClose,
  onCreated,
}: {
  onClose: () => void;
  onCreated: (cycleId: number) => void;
}) {
  const dialog = useRef<HTMLDialogElement>(null);
  const [options, setOptions] = useState<SemesterOption[]>([]);
  const [choice, setChoice] = useState("");
  const [startDate, setStartDate] = useState("");
  const [endDate, setEndDate] = useState("");
  const [signupDeadline, setSignupDeadline] = useState("");
  const [observationDeadline, setObservationDeadline] = useState("");
  const [feedbackDeadline, setFeedbackDeadline] = useState("");
  const [professors, setProfessors] = useState<File | null>(null);
  const [sections, setSections] = useState<File | null>(null);
  const [error, setError] = useState("");
  const [problems, setProblems] = useState<ImportError[]>([]);
  const [busy, setBusy] = useState(false);
  const selected = options.find(
    (option) => `${option.term}-${option.year}` === choice,
  );

  useEffect(() => {
    dialog.current?.showModal();
    apiFetch<SemesterOption[]>("/api/ac/cycles/upcoming-semesters")
      .then(setOptions)
      .catch((cause: unknown) =>
        setError(
          cause instanceof Error ? cause.message : "Could not load semesters.",
        ),
      );
  }, []);

  async function submit(event: FormEvent) {
    event.preventDefault();
    if (!selected || !professors || !sections) return;
    setBusy(true);
    setError("");
    setProblems([]);
    const body = new FormData();
    body.append(
      "setup",
      new Blob(
        [
          JSON.stringify({
            term: selected.term,
            year: selected.year,
            startDate,
            endDate,
            signupDeadline,
            observationDeadline,
            feedbackDeadline,
          }),
        ],
        { type: "application/json" },
      ),
    );
    body.append("professors", professors);
    body.append("sections", sections);
    try {
      const created = await apiFetch<{ cycleId: number }>("/api/ac/cycles", {
        method: "POST",
        body,
      });
      onCreated(created.cycleId);
    } catch (cause) {
      setError(
        cause instanceof Error ? cause.message : "Could not create the cycle.",
      );
      if (cause instanceof ApiError) setProblems(cause.problems);
    } finally {
      setBusy(false);
    }
  }

  const field = (
    label: string,
    value: string,
    change: (value: string) => void,
  ) => (
    <label className="block text-sm">
      {label}
      <input
        className="mt-1 block w-full rounded border border-line p-2"
        type="date"
        required
        value={value}
        onChange={(event) => change(event.target.value)}
      />
    </label>
  );
  return (
    <dialog
      ref={dialog}
      aria-labelledby="cycle-setup-title"
      onCancel={(event) => {
        event.preventDefault();
        if (!busy) onClose();
      }}
      className="fixed inset-0 m-auto max-h-[90vh] w-[calc(100%-2rem)] max-w-xl overflow-y-auto rounded-xl border border-line bg-white p-6 shadow-xl backdrop:bg-ink/40"
    >
      <h2 id="cycle-setup-title" className="font-bold text-xl">
        Create upcoming cycle
      </h2>
      <form onSubmit={(event) => void submit(event)} className="mt-4 space-y-4">
        <label className="block text-sm">
          Semester
          <select
            required
            value={choice}
            className="mt-1 block w-full rounded border border-line p-2"
            onChange={(event) => {
              const next = options.find(
                (option) =>
                  `${option.term}-${option.year}` === event.target.value,
              );
              setChoice(event.target.value);
              setStartDate(next?.startDate ?? "");
              setEndDate(next?.endDate ?? "");
            }}
          >
            <option value="">Select a semester</option>
            {options
              .filter((option) => !option.hasCycle)
              .map((option) => (
                <option
                  key={`${option.term}-${option.year}`}
                  value={`${option.term}-${option.year}`}
                >
                  {option.term} {option.year}
                </option>
              ))}
          </select>
        </label>
        {selected?.estimated && (
          <p className="text-sm text-muted">
            These dates are estimates. Review them before creating the cycle.
          </p>
        )}
        <div className="grid gap-3 sm:grid-cols-2">
          {field("Semester starts", startDate, setStartDate)}
          {field("Semester ends", endDate, setEndDate)}
        </div>
        <div className="grid gap-3 sm:grid-cols-3">
          {field("Signup deadline", signupDeadline, setSignupDeadline)}
          {field(
            "Observation deadline",
            observationDeadline,
            setObservationDeadline,
          )}
          {field("Feedback deadline", feedbackDeadline, setFeedbackDeadline)}
        </div>
        <div className="block text-sm">
          <TemplateDownload className="underline" kind="professors" />
          <label className="block">
            Professors CSV
            <input
              className="mt-1 block w-full"
              type="file"
              accept=".csv,text/csv"
              required
              onChange={(event) =>
                setProfessors(event.target.files?.[0] ?? null)
              }
            />
          </label>
        </div>
        <div className="block text-sm">
          <TemplateDownload className="underline" kind="sections" />
          <label className="block">
            Class sections CSV
            <input
              className="mt-1 block w-full"
              type="file"
              accept=".csv,text/csv"
              required
              onChange={(event) => setSections(event.target.files?.[0] ?? null)}
            />
          </label>
        </div>
        {error && (
          <p role="alert" className="text-sm text-brand-red">
            {error}
          </p>
        )}
        <CycleImportProblems problems={problems} />
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
            disabled={busy}
            type="submit"
            className="rounded bg-brand-orange px-4 py-2 text-white"
          >
            {busy ? "Creating…" : "Create cycle"}
          </button>
        </div>
      </form>
    </dialog>
  );
}
