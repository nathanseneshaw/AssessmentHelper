import type { EvaluationDeferral } from "../api/evaluations";

export function DeferralDetails({ details }: { details: EvaluationDeferral }) {
  return (
    <section
      aria-label="Deferral details"
      className="mt-3 rounded-lg border border-line bg-mist p-4 text-sm"
    >
      <p className="font-semibold text-brand-green">
        Deferred to {details.targetSemester}
      </p>
      <dl className="mt-2 space-y-2">
        <div>
          <dt className="font-medium">Reason</dt>
          <dd>{details.reason}</dd>
        </div>
        <div>
          <dt className="font-medium">Deferred by</dt>
          <dd>{details.deferredBy}</dd>
        </div>
        <div>
          <dt className="font-medium">Decision date</dt>
          <dd>{details.deferredAt.replace("T", " ").slice(0, 19)}</dd>
        </div>
      </dl>
    </section>
  );
}
