import { useEffect, useRef, useState } from "react";
import { ApiError } from "../../api/client";
import {
  type ImportKind,
  type ImportResult,
  uploadImport,
} from "../../api/imports";
import { TemplateDownload } from "./TemplateDownload";

const KINDS: { kind: ImportKind; label: string; description: string }[] = [
  {
    kind: "professors",
    label: "Professors",
    description: "Names, emails, departments, ranks and hire dates.",
  },
  {
    kind: "sections",
    label: "Class sections",
    description:
      "Courses and sections for a semester, with meeting times and instructors.",
  },
];

const plural = (n: number, word: string) => `${n} ${word}${n === 1 ? "" : "s"}`;

export function ImportDialog({
  onClose,
  onImported,
}: {
  onClose: () => void;
  onImported: () => void;
}) {
  const dialog = useRef<HTMLDialogElement>(null);
  const [kind, setKind] = useState<ImportKind>("professors");
  const [file, setFile] = useState<File | null>(null);
  const [result, setResult] = useState<ImportResult | null>(null);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");

  useEffect(() => {
    dialog.current?.showModal();
  }, []);

  function close() {
    if (!busy) onClose();
  }

  function choose(next: { kind?: ImportKind; file?: File }) {
    if (next.kind) setKind(next.kind);
    if (next.file) setFile(next.file);
    setResult(null);
    setError("");
  }

  async function send() {
    if (!file) return;
    setBusy(true);
    setError("");
    try {
      const answer = await uploadImport(kind, file);
      setResult(answer);
      if (answer.saved) onImported();
    } catch (cause) {
      setError(
        cause instanceof ApiError
          ? cause.message
          : "The file could not be sent. Please try again.",
      );
    } finally {
      setBusy(false);
    }
  }

  return (
    <dialog
      ref={dialog}
      aria-labelledby="import-title"
      onCancel={(event) => {
        event.preventDefault();
        close();
      }}
      className="fixed inset-0 m-auto max-h-[90vh] w-[calc(100%-2rem)] max-w-2xl overflow-y-auto rounded-xl border border-line bg-white p-6 text-ink shadow-xl backdrop:bg-ink/40"
    >
      <div className="flex items-start justify-between gap-4">
        <div>
          <h2 id="import-title" className="font-bold text-xl">
            Upload CSV
          </h2>
          <p className="mt-1 text-sm text-muted">
            Add or update records from a spreadsheet saved as CSV.
          </p>
        </div>
        <button
          type="button"
          disabled={busy}
          onClick={close}
          className="rounded-md border border-line px-3 py-1 text-sm hover:bg-mist disabled:cursor-not-allowed disabled:opacity-50"
        >
          Close
        </button>
      </div>

      <fieldset disabled={busy} className="mt-6">
        <legend className="font-semibold text-sm">
          What are you importing?
        </legend>
        <ul className="mt-2 flex flex-col gap-2">
          {KINDS.map((option) => (
            <li
              key={option.kind}
              className="flex flex-wrap items-start justify-between gap-x-4 gap-y-1 rounded-lg border border-line p-3 has-checked:border-brand-green has-checked:bg-mist"
            >
              <label className="flex flex-1 cursor-pointer items-start gap-3">
                <input
                  type="radio"
                  name="import-kind"
                  aria-label={option.label}
                  checked={kind === option.kind}
                  onChange={() => choose({ kind: option.kind })}
                  className="mt-1"
                />
                <span>
                  <span className="block font-semibold">{option.label}</span>
                  <span className="block text-muted text-sm">
                    {option.description}
                  </span>
                </span>
              </label>
              <TemplateDownload
                kind={option.kind}
                className="ml-7 text-brand-green text-sm underline hover:no-underline sm:ml-0"
              />
            </li>
          ))}
        </ul>
        <p className="mt-2 text-muted text-sm">
          Upload professors before the class sections that name them as
          instructors. Departments and semesters must already exist.
        </p>
      </fieldset>

      <div className="mt-5 flex flex-wrap items-center gap-3">
        <label className="cursor-pointer rounded-md border border-line bg-cream px-4 py-2 text-sm hover:bg-mist has-disabled:cursor-not-allowed has-disabled:opacity-50 has-focus-visible:outline-3 has-focus-visible:outline-utd-green has-focus-visible:outline-offset-3">
          Choose CSV file
          <input
            type="file"
            accept=".csv,text/csv"
            disabled={busy}
            onChange={(event) => {
              const picked = event.target.files?.[0];
              event.target.value = "";
              if (picked) choose({ file: picked });
            }}
            className="sr-only"
          />
        </label>
        <span className="min-w-0 truncate text-sm">
          {file ? file.name : "No file chosen"}
        </span>
      </div>

      {error && (
        <p role="alert" className="mt-5 text-sm text-brand-red">
          {error}
        </p>
      )}

      {result && (
        <section className="mt-6 border-line border-t pt-5">
          <h3
            className={`font-semibold text-lg ${result.saved ? "text-brand-green" : "text-brand-red"}`}
          >
            {result.saved ? "Import complete" : "Problems found"}
          </h3>
          <p role="status" className="mt-1 text-sm">
            {result.saved
              ? `Saved ${plural(result.rowCount, "row")} from ${file?.name}.`
              : `Found ${plural(result.errors.length, "problem")}. Nothing was imported. Fix them in ${file?.name}, then choose it again.`}
          </p>
          {result.errors.length > 0 && (
            <div className="mt-4 max-h-80 overflow-auto rounded-lg border border-line">
              <table className="w-full text-left text-sm">
                <thead className="sticky top-0 bg-cream text-muted text-xs">
                  <tr>
                    <th className="px-3 py-2 font-normal">Row</th>
                    <th className="px-3 py-2 font-normal">Column</th>
                    <th className="px-3 py-2 font-normal">Problem</th>
                  </tr>
                </thead>
                <tbody>
                  {result.errors.map((problem) => (
                    <tr
                      key={`${problem.row}|${problem.column}|${problem.message}`}
                      className="border-line border-t align-top"
                    >
                      <td className="px-3 py-2 font-semibold">
                        {problem.row ?? "File"}
                      </td>
                      <td className="px-3 py-2 font-mono text-xs">
                        {problem.column ?? "—"}
                      </td>
                      <td className="px-3 py-2">{problem.message}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </section>
      )}

      <div className="mt-6 flex justify-end">
        {result?.saved ? (
          <button
            type="button"
            onClick={close}
            className="rounded-lg bg-brand-orange px-6 py-2 text-sm text-white hover:opacity-90"
          >
            Done
          </button>
        ) : (
          <button
            type="button"
            disabled={!file || result !== null || busy}
            onClick={() => void send()}
            className="rounded-lg bg-brand-orange px-6 py-2 text-sm text-white hover:opacity-90 disabled:cursor-not-allowed disabled:opacity-50"
          >
            {busy ? "Importing…" : "Import"}
          </button>
        )}
      </div>
    </dialog>
  );
}
