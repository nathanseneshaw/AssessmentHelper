import { type ReactNode, useEffect, useId, useRef, useState } from "react";
import {
  type Criterion,
  type FormTemplate,
  getFormTemplate,
  getFormTemplateHistory,
  restoreFormTemplate,
  saveFormTemplate,
} from "../../api/formTemplate";
import { PageHeader } from "../../components/PageHeader";
import { FormTemplatePreview } from "./FormTemplatePreview";

const primary =
  "rounded-lg bg-brand-orange px-5 py-2.5 text-sm font-semibold text-white transition hover:opacity-90 disabled:cursor-not-allowed disabled:bg-btn-gray";
const secondary =
  "rounded-lg border border-line bg-white px-5 py-2.5 text-sm font-semibold transition hover:bg-mist disabled:opacity-50";
const errorMessage = (cause: unknown) =>
  cause instanceof Error
    ? cause.message
    : "Unable to update the form template.";

function Overlay({
  title,
  subtitle,
  onClose,
  busy = false,
  compact = false,
  children,
}: {
  title: string;
  subtitle: string;
  onClose: () => void;
  busy?: boolean;
  compact?: boolean;
  children: ReactNode;
}) {
  const ref = useRef<HTMLDialogElement>(null);
  const heading = useId();
  useEffect(() => {
    const dialog = ref.current;
    dialog?.showModal();
    return () => dialog?.close();
  }, []);
  return (
    <dialog
      ref={ref}
      aria-labelledby={heading}
      onCancel={(event) => {
        event.preventDefault();
        event.stopPropagation();
        if (!busy) onClose();
      }}
      className="m-auto max-h-[90dvh] w-[calc(100%-2rem)] max-w-3xl overflow-y-auto rounded-2xl border border-line bg-white p-0 text-ink shadow-2xl backdrop:bg-ink/40"
    >
      <header className="flex items-start justify-between gap-5 border-b border-line px-6 py-5 sm:px-8">
        <div>
          {!compact && (
            <p className="mb-1 text-xs font-semibold uppercase tracking-widest text-brand-green">
              Observation template
            </p>
          )}
          <h2
            id={heading}
            className={
              compact ? "text-lg font-semibold" : "text-2xl font-semibold"
            }
          >
            {title}
          </h2>
          <p className="mt-1 text-sm text-muted">{subtitle}</p>
        </div>
        <button
          type="button"
          className={secondary}
          disabled={busy}
          onClick={onClose}
        >
          Close
        </button>
      </header>
      {children}
    </dialog>
  );
}

function CriteriaEditor({
  template,
  onClose,
  onSaved,
}: {
  template: FormTemplate;
  onClose: () => void;
  onSaved: (saved: FormTemplate) => void;
}) {
  const nextKey = useRef(template.criteria.length);
  const focusNew = useRef<number | null>(null);
  const [rows, setRows] = useState(() =>
    template.criteria.map((criterion, key) => ({ ...criterion, key })),
  );
  const [busy, setBusy] = useState(false);
  const [removing, setRemoving] = useState<number | null>(null);
  const criterionToRemove = rows.find((row) => row.key === removing);
  const saving = useRef(false);
  const [error, setError] = useState("");
  const selected = rows.filter((row) => row.selected).length;
  const dirty =
    JSON.stringify(
      rows.map(({ title, selected, notes }) => ({ title, selected, notes })),
    ) !== JSON.stringify(template.criteria);
  const update = (key: number, values: Partial<Criterion>) =>
    setRows((previous) =>
      previous.map((row) => (row.key === key ? { ...row, ...values } : row)),
    );
  async function save() {
    if (saving.current || !dirty) return;
    if (!selected) {
      setError("Select at least one criterion for the observation form.");
      return;
    }
    if (rows.some((row) => !row.title.trim())) {
      setError("Give every criterion a name before saving.");
      return;
    }
    const names = rows.map((row) => row.title.trim().toLowerCase());
    if (new Set(names).size !== names.length) {
      setError("Criterion names must be unique.");
      return;
    }
    saving.current = true;
    setBusy(true);
    setError("");
    try {
      onSaved(
        await saveFormTemplate({
          ...template,
          criteria: rows.map(({ title, selected, notes }) => ({
            title: title.trim(),
            selected,
            notes,
          })),
        }),
      );
    } catch (cause) {
      setError(errorMessage(cause));
    } finally {
      saving.current = false;
      setBusy(false);
    }
  }
  return (
    <Overlay
      title="Edit Observation Form Criteria"
      subtitle="Choose what to include, edit names, and enable optional notes."
      onClose={onClose}
      busy={busy}
    >
      <form
        onSubmit={(event) => {
          event.preventDefault();
          void save();
        }}
      >
        <fieldset disabled={busy} className="min-w-0 px-6 py-6 sm:px-8">
          <div className="mb-5 flex flex-wrap items-center justify-between gap-2">
            <span className="rounded-full bg-mist px-3 py-1 text-xs font-semibold text-brand-green">
              {selected} of {rows.length} criteria selected
            </span>
            <span className="text-xs text-muted">
              Changes apply after saving
            </span>
          </div>
          <table className="w-full table-fixed border-separate border-spacing-x-0 border-spacing-y-2">
            <colgroup>
              <col className="w-11" />
              <col />
              <col className="w-24 sm:w-28" />
              <col className="w-16 sm:w-20" />
            </colgroup>
            <thead>
              <tr className="text-xs font-semibold text-muted">
                <th scope="col">
                  <span className="sr-only">Include</span>
                </th>
                <th scope="col" className="px-2 pb-1 text-left">
                  Criterion
                </th>
                <th scope="col" className="px-1 pb-1 text-center">
                  Optional Notes
                </th>
                <th scope="col" className="px-1 pb-1 text-center">
                  Remove
                </th>
              </tr>
            </thead>
            <tbody>
              {rows.map((row, index) => (
                <tr
                  key={row.key}
                  className={row.selected ? "bg-mist/60" : "bg-neutral-50"}
                >
                  <td className="rounded-l-xl py-3">
                    <div className="flex items-center justify-center">
                      <input
                        type="checkbox"
                        aria-label={`Include criterion ${index + 1}`}
                        checked={row.selected}
                        onChange={(event) =>
                          update(row.key, { selected: event.target.checked })
                        }
                        className="size-5 cursor-pointer accent-brand-green"
                      />
                    </div>
                  </td>
                  <td className="min-w-0 py-3">
                    <input
                      aria-label={`Criterion ${index + 1} name`}
                      value={row.title}
                      required
                      maxLength={500}
                      placeholder="Enter criterion name"
                      ref={(input) => {
                        if (input && focusNew.current === row.key) {
                          input.focus();
                          focusNew.current = null;
                        }
                      }}
                      onChange={(event) =>
                        update(row.key, { title: event.target.value })
                      }
                      className="w-full min-w-0 rounded-md border border-transparent bg-transparent px-2 py-2 text-sm font-medium outline-none transition hover:border-line focus:border-brand-green focus:bg-white focus:ring-2 focus:ring-brand-green/15"
                    />
                  </td>
                  <td className="py-3">
                    <div className="flex items-center justify-center">
                      <input
                        type="checkbox"
                        aria-label={`Optional notes for criterion ${index + 1}`}
                        checked={row.notes}
                        onChange={(event) =>
                          update(row.key, { notes: event.target.checked })
                        }
                        className="size-5 cursor-pointer appearance-none justify-self-center rounded-full border border-neutral-400 bg-white transition checked:border-brand-green checked:bg-brand-green focus-visible:outline-2 focus-visible:outline-offset-4 focus-visible:outline-brand-green disabled:cursor-not-allowed"
                      />
                    </div>
                  </td>
                  <td className="rounded-r-xl py-3">
                    <div className="flex items-center justify-center">
                      <button
                        type="button"
                        aria-label={`Delete criterion ${index + 1}`}
                        onClick={() => setRemoving(row.key)}
                        className="justify-self-center rounded-md p-1.5 text-muted transition hover:bg-red-50 hover:text-brand-red"
                      >
                        <svg
                          aria-hidden="true"
                          viewBox="0 0 24 24"
                          fill="none"
                          stroke="currentColor"
                          strokeWidth="1.7"
                          className="size-5"
                        >
                          <path d="M3 6h18M9 6V3h6v3M5 6l1 15h12l1-15M10 10v7m4-7v7" />
                        </svg>
                      </button>
                    </div>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
          {!rows.length && (
            <p className="rounded-lg border border-dashed border-line p-6 text-center text-sm text-muted">
              No criteria yet. Add one to get started.
            </p>
          )}
          <button
            type="button"
            disabled={rows.length >= 100}
            className={`${secondary} mt-5 text-brand-green`}
            onClick={() => {
              focusNew.current = nextKey.current;
              setRows((previous) => [
                ...previous,
                {
                  key: nextKey.current++,
                  title: "",
                  selected: true,
                  notes: false,
                },
              ]);
            }}
          >
            + Add New Criterion
          </button>
          <p className="mt-4 text-xs leading-5 text-muted">
            Uncheck a criterion to leave it out of the form. Delete removes it
            from this template. Existing observations keep their original
            template.
          </p>
          {error && (
            <p
              role="alert"
              className="mt-4 rounded-lg bg-peach px-4 py-3 text-sm text-brand-red"
            >
              {error}
            </p>
          )}
        </fieldset>
        <footer className="sticky bottom-0 flex justify-end gap-3 border-t border-line bg-white px-6 py-4 sm:px-8">
          <button
            type="button"
            className={secondary}
            disabled={busy}
            onClick={onClose}
          >
            Cancel
          </button>
          <button type="submit" className={primary} disabled={busy || !dirty}>
            {busy ? "Saving..." : "Save Changes"}
          </button>
        </footer>
      </form>
      {criterionToRemove && (
        <Overlay
          title="Remove Criterion"
          subtitle="Review before removing"
          onClose={() => setRemoving(null)}
        >
          <div className="px-6 py-6 sm:px-8">
            <p className="text-sm leading-6">
              Remove:{" "}
              <strong className="break-words">
                {criterionToRemove.title.trim() || "this unnamed criterion"}
              </strong>
              ?
            </p>
            <p className="mt-2 text-sm text-muted">
              This change takes effect when you save the template.
            </p>
          </div>
          <footer className="flex justify-end gap-3 border-t border-line px-6 py-4 sm:px-8">
            <button
              type="button"
              className={secondary}
              onClick={() => setRemoving(null)}
            >
              Cancel
            </button>
            <button
              type="button"
              className={primary}
              onClick={() => {
                setRows((previous) =>
                  previous.filter((row) => row.key !== removing),
                );
                setRemoving(null);
              }}
            >
              Remove Criterion
            </button>
          </footer>
        </Overlay>
      )}
    </Overlay>
  );
}

function VersionHistory({
  current,
  onClose,
  onRestored,
}: {
  current: FormTemplate;
  onClose: () => void;
  onRestored: (saved: FormTemplate) => void;
}) {
  const [versions, setVersions] = useState<FormTemplate[]>([]);
  const [selectedId, setSelectedId] = useState(current.templateId);
  const [error, setError] = useState("");
  const [loading, setLoading] = useState(true);
  const [retry, setRetry] = useState(0);
  const [confirming, setConfirming] = useState(false);
  const [busy, setBusy] = useState(false);
  const restoring = useRef(false);
  const selected = versions.find(
    (version) => version.templateId === selectedId,
  );
  // biome-ignore lint/correctness/useExhaustiveDependencies: retry refreshes the version list.
  useEffect(() => {
    const controller = new AbortController();
    setLoading(true);
    setError("");
    getFormTemplateHistory(controller.signal)
      .then((result) => {
        if (!controller.signal.aborted) setVersions(result);
      })
      .catch((cause) => {
        if (!controller.signal.aborted) setError(errorMessage(cause));
      })
      .finally(() => {
        if (!controller.signal.aborted) setLoading(false);
      });
    return () => controller.abort();
  }, [retry]);
  async function restore() {
    if (!selected || restoring.current) return;
    restoring.current = true;
    setBusy(true);
    setError("");
    try {
      onRestored(
        await restoreFormTemplate(selected.templateId, current.templateId),
      );
    } catch (cause) {
      setError(errorMessage(cause));
      setConfirming(false);
    } finally {
      restoring.current = false;
      setBusy(false);
    }
  }
  return (
    <Overlay
      title="Template Version History"
      subtitle="Review and restore previous criteria."
      onClose={onClose}
      busy={busy}
    >
      <div className="space-y-5 px-6 py-6 sm:px-8">
        {loading && <p role="status">Loading previous versions...</p>}
        {error && (
          <p role="alert" className="rounded-lg bg-peach p-3 text-sm">
            {error}{" "}
            <button
              type="button"
              className="font-semibold underline"
              onClick={() => setRetry((value) => value + 1)}
            >
              Retry
            </button>
          </p>
        )}
        {!loading && versions.length > 0 && (
          <>
            <label className="block text-sm font-semibold">
              Version
              <select
                className="mt-2 block w-full rounded-lg border border-line bg-white px-3 py-3"
                value={selectedId}
                onChange={(event) => setSelectedId(Number(event.target.value))}
              >
                {versions.map((version) => (
                  <option key={version.templateId} value={version.templateId}>
                    Version {version.version}
                    {version.templateId === current.templateId
                      ? " (current)"
                      : ""}
                  </option>
                ))}
              </select>
            </label>
            {selected && (
              <section aria-label={`Version ${selected.version} criteria`}>
                <p className="mb-3 text-sm text-muted">
                  {
                    selected.criteria.filter((criterion) => criterion.selected)
                      .length
                  }{" "}
                  of {selected.criteria.length} criteria selected
                </p>
                <ul className="space-y-3">
                  {selected.criteria.map((criterion) => (
                    <li
                      key={criterion.title}
                      className="rounded-lg border border-line p-4"
                    >
                      <h3 className="font-semibold">{criterion.title}</h3>
                      <p className="mt-1 text-sm text-muted">
                        {criterion.selected ? "Selected" : "Not selected"} |
                        Optional notes:{" "}
                        {criterion.notes ? "enabled" : "disabled"}
                      </p>
                    </li>
                  ))}
                </ul>
              </section>
            )}
          </>
        )}
        {!loading && !error && versions.length === 0 && (
          <p>No saved versions yet.</p>
        )}
      </div>
      <footer className="sticky bottom-0 flex justify-end gap-3 border-t border-line bg-white px-6 py-4 sm:px-8">
        <button
          type="button"
          className={secondary}
          onClick={onClose}
          disabled={busy}
        >
          Close History
        </button>
        <button
          type="button"
          className={primary}
          disabled={
            loading ||
            busy ||
            !selected ||
            selected.templateId === current.templateId ||
            !!error
          }
          onClick={() => setConfirming(true)}
        >
          Restore This Version
        </button>
      </footer>
      {confirming && selected && (
        <Overlay
          title={`Restore version ${selected.version}?`}
          subtitle="This will update the shared form template for all committee accounts."
          onClose={() => setConfirming(false)}
          busy={busy}
        >
          <p className="px-6 py-6 text-sm leading-6 sm:px-8">
            The criteria, selections, and optional notes will be reverted to
            version {selected.version}.
          </p>
          <footer className="flex justify-end gap-3 border-t border-line px-6 py-4 sm:px-8">
            <button
              type="button"
              className={secondary}
              disabled={busy}
              onClick={() => setConfirming(false)}
            >
              Cancel
            </button>
            <button
              type="button"
              className={primary}
              disabled={busy}
              onClick={() => void restore()}
            >
              {busy ? "Restoring..." : "Confirm Restore"}
            </button>
          </footer>
        </Overlay>
      )}
    </Overlay>
  );
}

export function AcFormTemplate() {
  const [template, setTemplate] = useState<FormTemplate | null>(null);
  const [editing, setEditing] = useState<FormTemplate | null>(null);
  const [preview, setPreview] = useState(false);
  const [history, setHistory] = useState<FormTemplate | null>(null);
  const [error, setError] = useState("");
  const [success, setSuccess] = useState("");
  const [retry, setRetry] = useState(0);
  // biome-ignore lint/correctness/useExhaustiveDependencies: retry explicitly reloads the saved template.
  useEffect(() => {
    const controller = new AbortController();
    let pending = false;
    async function load() {
      if (pending) return;
      pending = true;
      try {
        const saved = await getFormTemplate(controller.signal);
        if (!controller.signal.aborted) {
          setTemplate(saved);
          setError("");
        }
      } catch (cause) {
        if (!controller.signal.aborted) setError(errorMessage(cause));
      } finally {
        pending = false;
      }
    }
    void load();
    const timer = setInterval(() => void load(), 5000);
    window.addEventListener("focus", load);
    return () => {
      controller.abort();
      clearInterval(timer);
      window.removeEventListener("focus", load);
    };
  }, [retry]);
  const includedCriteria =
    template?.criteria.filter((criterion) => criterion.selected) ?? [];
  const scrollCriteria = includedCriteria.length > 4;
  return (
    <div>
      <PageHeader
        title="Observation Form Template"
        subtitle="Manage the criteria shared across all assessment committee accounts."
      />
      {success && (
        <p
          role="status"
          className="mt-5 rounded-lg bg-mist p-4 text-sm text-brand-green"
        >
          {success}
        </p>
      )}
      {error && (
        <div role="alert" className="mt-5 rounded-lg bg-peach p-4 text-sm">
          {error}{" "}
          <button
            type="button"
            className="font-semibold underline"
            onClick={() => setRetry((value) => value + 1)}
          >
            Retry
          </button>
        </div>
      )}
      {!template && !error && (
        <p role="status" className="mt-8 text-muted">
          Loading form template...
        </p>
      )}
      {template && (
        <div className="mt-8 grid gap-7 xl:grid-cols-[minmax(0,2fr)_minmax(0,1fr)]">
          <section className="rounded-xl border border-line bg-white p-6">
            <div className="flex items-center justify-between gap-3">
              <h2 className="text-xl font-semibold">Observation criteria</h2>
              <span className="rounded-full bg-mist px-3 py-1 text-xs font-semibold text-brand-green">
                Version {template.version}
              </span>
            </div>
            <p className="mt-2 text-sm text-muted">
              {
                template.criteria.filter((criterion) => criterion.selected)
                  .length
              }{" "}
              selected for the observation form
            </p>
            <ul
              aria-label="Included observation criteria"
              tabIndex={scrollCriteria ? 0 : undefined}
              className={`mt-6 space-y-3 ${scrollCriteria ? "max-h-[34.25rem] overflow-y-auto overscroll-contain pr-2" : ""}`}
            >
              {includedCriteria.map((criterion) => (
                <li
                  key={criterion.title}
                  className="flex h-32 flex-col justify-center rounded-lg border border-brand-green/20 bg-mist/30 p-4"
                >
                  <div className="flex items-start justify-between gap-3">
                    <h3
                      className="line-clamp-2 break-words font-semibold"
                      title={criterion.title}
                    >
                      {criterion.title}
                    </h3>
                  </div>
                  <p className="mt-2 text-sm text-muted">
                    Rating Scale: 1-5 | Optional Notes:{" "}
                    {criterion.notes ? "enabled" : "disabled"}
                  </p>
                </li>
              ))}
            </ul>
            <button
              type="button"
              className={`${primary} mt-6`}
              disabled={!!error}
              onClick={() => {
                setSuccess("");
                setEditing(template);
              }}
            >
              Add/Edit Criterion
            </button>
          </section>
          <section className="flex flex-col rounded-xl border border-line bg-mist p-6">
            <h2 className="text-xl font-semibold text-brand-green">
              Template settings
            </h2>
            <dl className="mt-7 grid grid-cols-2 gap-y-5 text-sm">
              <dt>Rating scale</dt>
              <dd>1-5</dd>
              <dt>Optional notes</dt>
              <dd>Set per criterion</dd>
              <dt>Shared with</dt>
              <dd>All AC members</dd>
            </dl>
            <p className="mt-7 text-sm leading-6 text-muted">
              Saved changes appear for every committee member. Previous
              observations retain their original criteria.
            </p>
            <button
              type="button"
              className={`${primary} mt-8 self-start`}
              onClick={() => setPreview(true)}
            >
              Preview Form
            </button>
            <button
              type="button"
              className={`${secondary} mt-3 self-start`}
              disabled={!!error}
              onClick={() => {
                setSuccess("");
                setHistory(template);
              }}
            >
              Version History
            </button>
          </section>
        </div>
      )}
      {editing && (
        <CriteriaEditor
          template={editing}
          onClose={() => setEditing(null)}
          onSaved={(saved) => {
            setTemplate(saved);
            setRetry((value) => value + 1);
            setEditing(null);
            setError("");
            setSuccess(
              "Changes saved. The updated template is shared with all committee accounts.",
            );
          }}
        />
      )}
      {history && (
        <VersionHistory
          current={history}
          onClose={() => setHistory(null)}
          onRestored={(saved) => {
            setTemplate(saved);
            setRetry((value) => value + 1);
            setHistory(null);
            setError("");
            setSuccess(
              `Version ${saved.version} is now current. The restored template is shared with all committee accounts.`,
            );
          }}
        />
      )}
      {preview && template && (
        <Overlay
          title="Observation Form Preview"
          subtitle={`Current Version: ${template.version}`}
          compact
          onClose={() => setPreview(false)}
        >
          <FormTemplatePreview template={template} />
        </Overlay>
      )}
    </div>
  );
}
