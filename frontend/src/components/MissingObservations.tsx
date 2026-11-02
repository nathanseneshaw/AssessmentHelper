import { useEffect } from "react";
import { Link } from "react-router-dom";
import { useApiResource } from "../api/observations";
import { onObserverChange } from "../api/observerEligibility";

export interface MissingObservationAlert {
  kind: "MISSING_SIGNUP" | "MISSING_SCHEDULE" | "LATE_SUBMISSION";
  observationId: number | null;
  dueDate: string;
  message: string;
}

export function MissingObservations({
  professorId,
  observerOnly = false,
}: {
  professorId: number | null | undefined;
  observerOnly?: boolean;
}) {
  const { data, error, reload } = useApiResource<MissingObservationAlert[]>(
    professorId ? `/api/professors/${professorId}/missing-observations` : null,
  );
  useEffect(() => onObserverChange(reload), [reload]);
  useEffect(() => {
    if (!professorId) return;
    const refresh = () => reload();
    window.addEventListener("focus", refresh);
    const timer = window.setInterval(refresh, 60000);
    return () => {
      window.removeEventListener("focus", refresh);
      window.clearInterval(timer);
    };
  }, [professorId, reload]);
  if (!professorId) return null;
  if (error)
    return (
      <p role="alert" className="my-4 text-brand-red text-sm">
        Could not check missing observations.{" "}
        <button type="button" className="underline" onClick={reload}>
          Retry
        </button>
      </p>
    );
  const alerts = (data ?? []).filter(
    (a) => !observerOnly || a.kind === "LATE_SUBMISSION",
  );
  if (!alerts.length) return null;
  return (
    <section
      aria-label="Missing observations"
      className="my-4 rounded-lg border border-brand-orange bg-peach p-4"
    >
      <h2 className="font-semibold">Missing observations</h2>
      <ul className="mt-2 space-y-3 text-sm">
        {alerts.map((a) => (
          <li key={`${a.kind}-${a.observationId ?? "evaluation"}`}>
            <p>{a.message}</p>
            <p className="text-xs">
              {a.kind === "LATE_SUBMISSION" ? "Scheduled date" : "Deadline"}:{" "}
              {a.dueDate}
            </p>
            {a.observationId !== null && (
              <Link
                className="font-semibold text-brand-green underline"
                to={`/faculty/observations/${a.observationId}`}
              >
                Complete observation
              </Link>
            )}
          </li>
        ))}
      </ul>
    </section>
  );
}
