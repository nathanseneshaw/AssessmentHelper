import { type FormEvent, useEffect, useRef, useState } from "react";
import { ApiError, apiFetch } from "../../api/client";
import type { ImportError } from "../../api/imports";
import { CycleImportProblems } from "./CycleImportProblems";
import { TemplateDownload } from "./TemplateDownload";

export function CycleFilesDialog({
  cycleId,
  label,
  onClose,
  onSaved,
}: {
  cycleId: number;
  label: string;
  onClose: () => void;
  onSaved: () => void;
}) {
  const dialog = useRef<HTMLDialogElement>(null);
  const [professors, setProfessors] = useState<File | null>(null);
  const [sections, setSections] = useState<File | null>(null);
  const [error, setError] = useState("");
  const [problems, setProblems] = useState<ImportError[]>([]);
  const [busy, setBusy] = useState(false);
  useEffect(() => {
    dialog.current?.showModal();
  }, []);
  async function submit(event: FormEvent) {
    event.preventDefault();
    if (!professors || !sections) return;
    const body = new FormData();
    body.append("professors", professors);
    body.append("sections", sections);
    setBusy(true);
    setError("");
    setProblems([]);
    try {
      await apiFetch(`/api/ac/cycles/${cycleId}/files`, {
        method: "PUT",
        body,
      });
      onSaved();
    } catch (cause) {
      setError(
        cause instanceof Error ? cause.message : "Could not replace files.",
      );
      if (cause instanceof ApiError) setProblems(cause.problems);
    } finally {
      setBusy(false);
    }
  }
  return (
    <dialog
      ref={dialog}
      aria-labelledby="replace-cycle-files-title"
      onCancel={(event) => {
        event.preventDefault();
        if (!busy) onClose();
      }}
      className="fixed inset-0 m-auto max-h-[90vh] w-[calc(100%-2rem)] max-w-xl overflow-y-auto rounded-xl border border-line bg-white p-6 shadow-xl backdrop:bg-ink/40"
    >
      <h2 id="replace-cycle-files-title" className="font-bold text-xl">
        Replace {label} files
      </h2>
      <p className="mt-2 text-sm text-muted">
        Upload both complete files. They replace the cycle roster and its
        previous teaching assignments.
      </p>
      <form onSubmit={(event) => void submit(event)} className="mt-4 space-y-4">
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
            type="submit"
            disabled={busy}
            className="rounded bg-brand-orange px-4 py-2 text-white"
          >
            {busy ? "Uploading…" : "Replace files"}
          </button>
        </div>
      </form>
    </dialog>
  );
}
