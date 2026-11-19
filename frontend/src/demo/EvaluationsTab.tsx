import { useEffect, useState } from "react";
import {
  completeEvaluation,
  getOpenEvaluations,
  type OpenEvaluation,
  stepEvaluation,
} from "../api/demo";
import { attempt, type Outcome, OutcomeText } from "./outcome";

function progress(evaluation: OpenEvaluation) {
  if (!evaluation.observer) return "No observer yet";
  const status = (evaluation.observationStatus ?? "")
    .toLowerCase()
    .replace("_", " ");
  return `${evaluation.observer}, ${status}`;
}

const STEP_DONE: Record<string, (observee: string) => string> = {
  ACCEPTED: (observee) => `Assigned an observer to ${observee}.`,
  IN_PROGRESS: (observee) => `Started ${observee}'s observation.`,
  SUBMITTED: (observee) => `The observer submitted ${observee}'s observation.`,
  COMPLETED: (observee) => `${observee} signed. The observation is locked.`,
};

export function EvaluationsTab({
  onDataChanged,
}: {
  onDataChanged: () => void;
}) {
  const [evaluations, setEvaluations] = useState<OpenEvaluation[] | null>(null);
  const [loadError, setLoadError] = useState("");
  const [working, setWorking] = useState<string | null>(null);
  const [outcome, setOutcome] = useState<Outcome | null>(null);

  useEffect(() => {
    const controller = new AbortController();
    getOpenEvaluations(controller.signal).then(setEvaluations, (e) => {
      if (!controller.signal.aborted)
        setLoadError(
          e instanceof Error ? e.message : "Could not load evaluations.",
        );
    });
    return () => controller.abort();
  }, []);

  async function run(
    key: string,
    action: () => Promise<string>,
    fallback: string,
  ) {
    setWorking(key);
    setOutcome(
      await attempt(async () => {
        const done = await action();
        setEvaluations(await getOpenEvaluations());
        onDataChanged();
        return done;
      }, fallback),
    );
    setWorking(null);
  }

  const step = (evaluation: OpenEvaluation) =>
    run(
      `step-${evaluation.signupId}`,
      async () => {
        const { status } = await stepEvaluation(evaluation.signupId);
        return (
          STEP_DONE[status]?.(evaluation.observee) ??
          `Moved ${evaluation.observee}'s observation to the next step.`
        );
      },
      "Could not move the evaluation forward.",
    );

  const complete = (evaluation: OpenEvaluation) =>
    run(
      `complete-${evaluation.signupId}`,
      async () => {
        await completeEvaluation(evaluation.signupId);
        return `Completed ${evaluation.observee}'s observation.`;
      },
      "Could not complete the evaluation.",
    );

  if (loadError) {
    return (
      <p role="alert" className="text-brand-red text-sm">
        {loadError}
      </p>
    );
  }
  if (!evaluations) return <p className="text-muted text-sm">Loading…</p>;

  return (
    <div>
      <OutcomeText outcome={outcome} />
      <ul className="mt-2 divide-y divide-line">
        {evaluations.map((evaluation) => (
          <li
            key={evaluation.signupId}
            className="flex items-center justify-between gap-3 py-2"
          >
            <span className="min-w-0">
              <span className="block font-semibold text-sm">
                {evaluation.observee}
              </span>
              <span className="block truncate text-muted text-xs">
                {evaluation.course} · {progress(evaluation)}
              </span>
            </span>
            <span className="flex shrink-0 gap-1.5">
              <button
                type="button"
                disabled={working !== null}
                onClick={() => step(evaluation)}
                aria-label={`Move ${evaluation.observee}'s evaluation to the next step`}
                className="rounded-md border border-line px-2.5 py-1.5 font-semibold text-xs hover:bg-mist disabled:opacity-50"
              >
                {working === `step-${evaluation.signupId}`
                  ? "Working…"
                  : "Next step"}
              </button>
              <button
                type="button"
                disabled={working !== null}
                onClick={() => complete(evaluation)}
                aria-label={`Complete ${evaluation.observee}'s evaluation`}
                className="rounded-md bg-brand-green px-2.5 py-1.5 font-semibold text-white text-xs hover:opacity-90 disabled:opacity-50"
              >
                {working === `complete-${evaluation.signupId}`
                  ? "Completing…"
                  : "Complete"}
              </button>
            </span>
          </li>
        ))}
      </ul>
      {evaluations.length === 0 && (
        <p className="mt-2 text-muted text-sm">Nothing left to complete.</p>
      )}
    </div>
  );
}
