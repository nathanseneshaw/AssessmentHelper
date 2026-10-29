import { useState } from "react";
import { apiFetch } from "../api/client";
import {
  type Cycle,
  observerBase,
  observerButton,
  observerChanged,
} from "../api/observerEligibility";

type Dates = Pick<
  Cycle,
  "signupDeadline" | "observationDeadline" | "feedbackDeadline"
>;

export function CycleDeadlines({
  cycle,
  onSaved,
}: {
  cycle?: Cycle;
  onSaved: () => void;
}) {
  const [draft, setDraft] = useState<Dates | null>(null);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  const [message, setMessage] = useState("");
  const fields = [
    ["signupDeadline", "Sign-up Deadline"],
    ["observationDeadline", "Observation Deadline"],
    ["feedbackDeadline", "Feedback/Survey Deadline"],
  ] as const;

  async function save() {
    if (!cycle || !draft || busy) return;
    setBusy(true);
    setError("");
    try {
      await apiFetch<Cycle>(
        `${observerBase}/cycles/${cycle.cycleId}/deadlines`,
        {
          method: "PUT",
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify(draft),
        },
      );
      setDraft(null);
      setMessage("Cycle deadlines updated.");
      observerChanged();
      onSaved();
    } catch (cause) {
      setError(
        cause instanceof Error ? cause.message : "Unable to update deadlines.",
      );
    } finally {
      setBusy(false);
    }
  }

  return (
    <section className="mt-7 rounded-lg border border-line bg-mist p-6">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <h2 className="font-semibold text-brand-green">
          {cycle?.label ?? "Current cycle"} deadlines
        </h2>
        {cycle && !draft && (
          <button
            type="button"
            className={observerButton}
            onClick={() => {
              setDraft({
                signupDeadline: cycle.signupDeadline,
                observationDeadline: cycle.observationDeadline,
                feedbackDeadline: cycle.feedbackDeadline ?? "",
              });
              setError("");
              setMessage("");
            }}
          >
            Edit Deadlines
          </button>
        )}
      </div>
      {error && (
        <p role="alert" className="mt-3 text-brand-red">
          {error}
        </p>
      )}
      {message && (
        <p role="status" className="mt-3 text-brand-green">
          {message}
        </p>
      )}
      {!cycle ? (
        <p className="mt-4">Loading cycle deadlines...</p>
      ) : draft ? (
        <form
          className="mt-4"
          onSubmit={(e) => {
            e.preventDefault();
            void save();
          }}
        >
          <p className="mb-4 text-sm">
            Changes apply to everyone in this evaluation cycle. Signup must be
            before observation, and observation before feedback.
          </p>
          <fieldset disabled={busy} className="grid gap-4 sm:grid-cols-3">
            {fields.map(([key, label]) => (
              <label
                key={key}
                className="flex flex-col gap-2 text-sm font-semibold"
              >
                {label}
                <input
                  type="date"
                  required
                  value={draft[key]}
                  min={key === "signupDeadline" ? cycle.startDate : undefined}
                  onChange={(e) =>
                    setDraft({ ...draft, [key]: e.target.value })
                  }
                  className="rounded border border-line bg-white p-2 font-normal"
                />
              </label>
            ))}
          </fieldset>
          <div className="mt-5 flex justify-end gap-3">
            <button
              type="button"
              disabled={busy}
              onClick={() => {
                setDraft(null);
                setError("");
              }}
              className="cursor-pointer rounded-lg border border-line px-5 py-3 text-sm disabled:cursor-not-allowed"
            >
              Cancel
            </button>
            <button type="submit" disabled={busy} className={observerButton}>
              {busy ? "Saving..." : "Save Deadlines"}
            </button>
          </div>
        </form>
      ) : (
        <ul className="mt-4 flex flex-wrap gap-x-6 gap-y-2">
          {fields.map(([key, label]) => (
            <li key={key}>
              {label}: {cycle[key] || "Not configured"}
            </li>
          ))}
        </ul>
      )}
    </section>
  );
}
