import { type ReactNode, useId } from "react";
import {
  type Criterion,
  formatDate,
  formatTime,
  formatTimestamp,
  type ObservationAnswer,
  type ObservationDetail,
  type ObservationStatus,
  STATUS_LABELS,
} from "../api/observations";

export const STATUS_COLORS: Record<ObservationStatus, string> = {
  ACCEPTED: "text-muted",
  SCHEDULED: "text-muted",
  IN_PROGRESS: "text-brand-orange",
  SUBMITTED: "text-brand-green",
  COMPLETED: "text-brand-green",
};

function SummaryItem({
  label,
  value,
  detail,
}: {
  label: string;
  value: ReactNode;
  detail?: string | null;
}) {
  return (
    <div>
      <dt className="text-muted text-sm">{label}</dt>
      <dd className="mt-1 font-semibold">{value}</dd>
      {detail && <dd className="text-muted text-sm">{detail}</dd>}
    </div>
  );
}

function NoResponse() {
  return <p className="text-muted italic">No response</p>;
}

function Comments({ text }: { text: string }) {
  return <p className="wrap-break-word whitespace-pre-wrap">{text}</p>;
}

/** The score as filled segments, one per point on the scale. */
function Rating({
  rating,
  criterion,
}: {
  rating: number;
  criterion: Criterion;
}) {
  const min = criterion.ratingMin ?? 1;
  const max = criterion.ratingMax;
  if (max === null) return <p className="font-semibold">{rating}</p>;
  const points = Array.from({ length: max - min + 1 }, (_, i) => min + i);
  return (
    <p className="flex flex-wrap items-center gap-3">
      <span aria-hidden="true" className="flex gap-1">
        {points.map((point) => (
          <span
            key={point}
            className={`h-2.5 w-7 rounded-sm ${point <= rating ? "bg-brand-green" : "bg-line"}`}
          />
        ))}
      </span>
      <span aria-hidden="true" className="font-semibold tabular-nums">
        {rating} / {max}
      </span>
      <span className="sr-only">
        Rated {rating} out of {max}
      </span>
    </p>
  );
}

function Answer({
  criterion,
  answer,
}: {
  criterion: Criterion;
  answer?: ObservationAnswer;
}) {
  const comments = answer?.comments?.trim() ? answer.comments : null;
  if (criterion.questionType === "TEXT") {
    return comments ? <Comments text={comments} /> : <NoResponse />;
  }
  return (
    <>
      {answer?.rating == null ? (
        <NoResponse />
      ) : (
        <Rating rating={answer.rating} criterion={criterion} />
      )}
      {comments && (
        <div className="mt-3">
          <p className="text-muted text-sm">Notes</p>
          <div className="mt-1">
            <Comments text={comments} />
          </div>
        </div>
      )}
    </>
  );
}

function signedOn(timestamp: string | null) {
  return timestamp ? `Signed ${formatTimestamp(timestamp)}` : "Not signed yet";
}

/**
 * A read-only view of a submitted or completed observation form, for its observer and its observee: who, what and
 * when, every answer, and who has signed off.
 */
export function ObservationRecord({
  observation,
}: {
  observation: ObservationDetail;
}) {
  const ids = useId();
  const { template } = observation;
  const answers = new Map(
    observation.responses.map((answer) => [answer.criterionId, answer]),
  );

  return (
    // Columns follow the width the record is given rather than the window, so a dialog gets the narrow layout.
    <div className="@container text-ink">
      <section
        aria-labelledby={`${ids}-summary`}
        className="rounded-lg bg-mist px-5 py-4"
      >
        <h2 id={`${ids}-summary`} className="sr-only">
          Summary
        </h2>
        <dl className="grid gap-x-8 gap-y-4 @lg:grid-cols-2">
          <SummaryItem
            label="Observee"
            value={observation.observee.name}
            detail={observation.observee.department}
          />
          <SummaryItem
            label="Observer"
            value={observation.observer.name}
            detail={observation.observer.department}
          />
          <SummaryItem
            label="Course"
            value={observation.course.label}
            detail={observation.course.courseName}
          />
          <SummaryItem label="Semester" value={observation.semesterLabel} />
          <SummaryItem
            label="Observed"
            value={
              observation.observationDate ? (
                formatDate(observation.observationDate)
              ) : (
                <span className="font-normal text-muted">Not recorded</span>
              )
            }
            detail={observation.startTime && formatTime(observation.startTime)}
          />
          <SummaryItem
            label="Status"
            value={
              <span className={STATUS_COLORS[observation.status]}>
                {STATUS_LABELS[observation.status]}
              </span>
            }
          />
        </dl>
      </section>

      <section aria-labelledby={`${ids}-responses`} className="mt-8">
        <h2 id={`${ids}-responses`} className="font-semibold text-xl">
          Responses
        </h2>
        <p className="text-muted text-sm">
          {template.templateName}, version {template.version}
        </p>
        <ol className="mt-4 divide-y divide-line border-line border-y">
          {template.criteria.map((criterion, index) => (
            <li key={criterion.criterionId} className="py-5">
              <div className="mb-3">
                <h3 className="font-semibold">
                  <span aria-hidden="true" className="mr-2 text-muted">
                    {index + 1}.
                  </span>
                  {criterion.criterionText}
                </h3>
                {!criterion.required && (
                  <p className="text-muted text-sm">Optional</p>
                )}
              </div>
              <Answer
                criterion={criterion}
                answer={answers.get(criterion.criterionId)}
              />
            </li>
          ))}
        </ol>
      </section>

      <section aria-labelledby={`${ids}-sign-offs`} className="mt-8">
        <h2 id={`${ids}-sign-offs`} className="font-semibold text-xl">
          Sign-offs
        </h2>
        <dl className="mt-4 grid gap-x-8 gap-y-4 @lg:grid-cols-2">
          <SummaryItem
            label="Observer"
            value={observation.observer.name}
            detail={signedOn(observation.submittedAt)}
          />
          <SummaryItem
            label="Observee"
            value={observation.observee.name}
            detail={signedOn(observation.observeeSignedAt)}
          />
        </dl>
        {observation.observeeComments && (
          <div className="mt-4">
            <p className="text-muted text-sm">Observee's comments</p>
            <div className="mt-1">
              <Comments text={observation.observeeComments} />
            </div>
          </div>
        )}
        {observation.locked && (
          <p className="mt-4 rounded-lg bg-mist px-4 py-3 text-sm">
            Both have signed, so this record is locked. Nobody can change it.
          </p>
        )}
      </section>
    </div>
  );
}
