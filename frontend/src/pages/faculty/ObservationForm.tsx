import { type FormEvent, useEffect, useId, useRef, useState } from "react";
import {
  type Criterion,
  formatDate,
  formatTime,
  isConflict,
  type ObservationAnswer,
  type ObservationDetail,
  saveObservationResponses,
  submitObservation,
} from "../../api/observations";
import { observerChanged } from "../../api/observerEligibility";

/** The size of the comments column. */
const MAX_COMMENTS = 4000;
/** The count of characters left appears once fewer than this many remain. */
const COUNT_FROM = 500;

const primaryButton =
  "rounded-lg bg-brand-green px-4 py-2 font-semibold text-sm text-white hover:opacity-90 disabled:cursor-not-allowed disabled:opacity-50";
const secondaryButton =
  "rounded-lg border border-line bg-white px-4 py-2 font-semibold text-sm text-ink hover:bg-mist disabled:cursor-not-allowed disabled:opacity-50";

interface Draft {
  rating: number | null;
  comments: string;
}
type Drafts = Record<number, Draft>;

const EMPTY: Draft = { rating: null, comments: "" };

function draftsFrom(responses: ObservationAnswer[]): Drafts {
  return Object.fromEntries(
    responses.map((answer) => [
      answer.criterionId,
      { rating: answer.rating, comments: answer.comments ?? "" },
    ]),
  );
}

/** What the API stores: one answer per question with a rating or written comments, in template order. */
function answersFrom(criteria: Criterion[], drafts: Drafts) {
  return criteria.flatMap((criterion): ObservationAnswer[] => {
    const draft = drafts[criterion.criterionId] ?? EMPTY;
    const rating = criterion.questionType === "RATING" ? draft.rating : null;
    const comments = draft.comments.trim() || null;
    return rating === null && comments === null
      ? []
      : [{ criterionId: criterion.criterionId, rating, comments }];
  });
}

function isAnswered(criterion: Criterion, draft = EMPTY) {
  return criterion.questionType === "RATING"
    ? draft.rating !== null
    : draft.comments.trim() !== "";
}

function scale(criterion: Criterion) {
  const min = criterion.ratingMin ?? 1;
  const max = criterion.ratingMax ?? 5;
  return Array.from({ length: Math.max(max - min + 1, 0) }, (_, i) => min + i);
}

/** A browser clock time in the same style as the server's times, e.g. "9:05 AM". */
function clockTime(date: Date) {
  return formatTime(`${date.getHours()}:${date.getMinutes()}`);
}

function messageOf(cause: unknown) {
  return cause instanceof Error ? cause.message : "Please try again.";
}

function QuestionTitle({
  number,
  criterion,
}: {
  number: number;
  criterion: Criterion;
}) {
  return (
    <>
      <span aria-hidden="true" className="mr-2 text-muted">
        {number}.
      </span>
      {criterion.criterionText}
      {criterion.required ? (
        <span aria-hidden="true" className="ml-1 text-brand-red">
          *
        </span>
      ) : (
        <span className="ml-2 rounded-full bg-mist px-2 py-0.5 font-normal text-muted text-xs">
          Optional
        </span>
      )}
    </>
  );
}

function FieldError({ id, children }: { id: string; children: string }) {
  return (
    <p id={id} className="mt-2 font-semibold text-brand-red text-sm">
      {children}
    </p>
  );
}

function CommentsBox({
  id,
  value,
  rows,
  required = false,
  errorId,
  onChange,
}: {
  id: string;
  value: string;
  rows: number;
  required?: boolean;
  /** Set while the field shows an error. */
  errorId?: string;
  onChange: (value: string) => void;
}) {
  const left = MAX_COMMENTS - value.length;
  const countId = `${id}-count`;
  const showCount = left < COUNT_FROM;
  const describedBy =
    [errorId, showCount ? countId : null].filter(Boolean).join(" ") ||
    undefined;
  return (
    <>
      <textarea
        id={id}
        value={value}
        rows={rows}
        maxLength={MAX_COMMENTS}
        aria-required={required || undefined}
        aria-invalid={errorId ? true : undefined}
        aria-describedby={describedBy}
        onChange={(event) => onChange(event.target.value)}
        className="mt-2 block w-full rounded-lg border border-line bg-white px-3 py-2 text-sm aria-invalid:border-brand-red"
      />
      {showCount && (
        <p
          id={countId}
          className={`mt-1 text-sm ${left === 0 ? "text-brand-red" : "text-muted"}`}
        >
          {left === 0
            ? "You have reached the 4,000 character limit."
            : `${left} characters left (4,000 maximum)`}
        </p>
      )}
    </>
  );
}

function RatingQuestion({
  id,
  number,
  criterion,
  draft,
  invalid,
  onChange,
}: {
  id: string;
  number: number;
  criterion: Criterion;
  draft: Draft;
  invalid: boolean;
  onChange: (changes: Partial<Draft>) => void;
}) {
  const values = scale(criterion);
  const titleId = `${id}-title`;
  const scaleId = `${id}-scale`;
  const errorId = `${id}-error`;
  return (
    <>
      {/* A radio group rather than a fieldset, so that it can be marked required and invalid. */}
      <div
        role="radiogroup"
        aria-labelledby={titleId}
        aria-required={criterion.required}
        aria-invalid={invalid || undefined}
        aria-describedby={invalid ? `${scaleId} ${errorId}` : scaleId}
      >
        <p id={titleId} className="font-semibold">
          <QuestionTitle number={number} criterion={criterion} />
        </p>
        <p id={scaleId} className="mt-1 text-muted text-sm">
          {values[0]} = Needs improvement, {values.at(-1)} = Excellent
        </p>
        {invalid && <FieldError id={errorId}>Choose a rating.</FieldError>}
        <div className="mt-3 flex flex-wrap items-center gap-2">
          {values.map((value) => (
            <label
              key={value}
              htmlFor={`${id}-${value}`}
              className="flex size-11 cursor-pointer items-center justify-center rounded-lg border border-line bg-white font-semibold tabular-nums hover:bg-mist has-checked:border-brand-green has-checked:bg-brand-green has-checked:text-white has-focus-visible:outline-3 has-focus-visible:outline-utd-green has-focus-visible:outline-offset-3"
            >
              <input
                id={`${id}-${value}`}
                type="radio"
                name={id}
                value={value}
                checked={draft.rating === value}
                onChange={() => onChange({ rating: value })}
                className="sr-only"
              />
              {value}
            </label>
          ))}
        </div>
      </div>
      {!criterion.required && draft.rating !== null && (
        <button
          type="button"
          className="mt-2 text-brand-green text-sm underline"
          onClick={() => onChange({ rating: null })}
        >
          Clear rating
        </button>
      )}
      <label htmlFor={`${id}-notes`} className="mt-4 block font-medium text-sm">
        Notes<span className="sr-only"> on {criterion.criterionText}</span>{" "}
        <span className="font-normal text-muted">(optional)</span>
      </label>
      <CommentsBox
        id={`${id}-notes`}
        value={draft.comments}
        rows={2}
        onChange={(comments) => onChange({ comments })}
      />
    </>
  );
}

function TextQuestion({
  id,
  number,
  criterion,
  draft,
  invalid,
  onChange,
}: {
  id: string;
  number: number;
  criterion: Criterion;
  draft: Draft;
  invalid: boolean;
  onChange: (changes: Partial<Draft>) => void;
}) {
  const errorId = `${id}-error`;
  return (
    <>
      <label htmlFor={id} className="block font-semibold">
        <QuestionTitle number={number} criterion={criterion} />
      </label>
      {invalid && <FieldError id={errorId}>Write an answer.</FieldError>}
      <CommentsBox
        id={id}
        value={draft.comments}
        rows={4}
        required={criterion.required}
        errorId={invalid ? errorId : undefined}
        onChange={(comments) => onChange({ comments })}
      />
    </>
  );
}

/**
 * The observer's editable form. Answers are kept here until a save or submit sends them, so a failed request
 * loses nothing. A resumed draft starts from the saved answers.
 */
export function ObservationForm({
  observation,
  onChange,
  onDirtyChange,
  onStale,
}: {
  observation: ObservationDetail;
  onChange: (observation: ObservationDetail) => void;
  /** Told whether some answers are not saved yet, and false once the form closes. */
  onDirtyChange?: (dirty: boolean) => void;
  /** Loads the observation again after the server said it changed elsewhere, for example submitted in another tab. */
  onStale?: () => void;
}) {
  const ids = useId();
  const { criteria } = observation.template;
  const [drafts, setDrafts] = useState(() => draftsFrom(observation.responses));
  const [pending, setPending] = useState<"save" | "submit" | null>(null);
  const [apiError, setApiError] = useState("");
  const [savedAt, setSavedAt] = useState<Date | null>(null);
  const [attempted, setAttempted] = useState(false);
  const [confirming, setConfirming] = useState(false);
  const confirmHeading = useRef<HTMLHeadingElement>(null);
  const submitButton = useRef<HTMLButtonElement>(null);
  const returnFocus = useRef(false);

  const answers = answersFrom(criteria, drafts);
  const dirty =
    JSON.stringify(answers) !==
    JSON.stringify(answersFrom(criteria, draftsFrom(observation.responses)));
  const unanswered = () =>
    criteria.filter(
      (criterion) =>
        criterion.required &&
        !isAnswered(criterion, drafts[criterion.criterionId]),
    );
  const missing = attempted ? unanswered() : [];
  const busy = pending !== null;
  const fieldId = (criterion: Criterion) => `${ids}-q${criterion.criterionId}`;

  useEffect(() => {
    onDirtyChange?.(dirty);
    return () => onDirtyChange?.(false);
  }, [dirty, onDirtyChange]);

  useEffect(() => {
    if (confirming) {
      confirmHeading.current?.focus();
    } else if (returnFocus.current) {
      returnFocus.current = false;
      submitButton.current?.focus();
    }
  }, [confirming]);

  function update(criterionId: number, changes: Partial<Draft>) {
    setDrafts((current) => ({
      ...current,
      [criterionId]: { ...(current[criterionId] ?? EMPTY), ...changes },
    }));
  }

  /** Moves focus to a question: its first rating, or its text box. */
  function focusQuestion(criterion: Criterion) {
    const id =
      criterion.questionType === "RATING"
        ? `${fieldId(criterion)}-${scale(criterion)[0]}`
        : fieldId(criterion);
    document.getElementById(id)?.focus();
  }

  /** Shows the errors and returns false when a required question is unanswered. */
  function checkComplete() {
    const [first] = unanswered();
    setAttempted(true);
    if (!first) return true;
    setConfirming(false);
    focusQuestion(first);
    return false;
  }

  async function save() {
    setPending("save");
    setApiError("");
    try {
      const result = await saveObservationResponses(
        observation.observationId,
        answers,
      );
      setSavedAt(new Date());
      onChange(result);
    } catch (cause) {
      setApiError(`The draft was not saved. ${messageOf(cause)}`);
      if (isConflict(cause)) onStale?.();
    } finally {
      setPending(null);
    }
  }

  function requestSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (busy) return;
    setApiError("");
    if (checkComplete()) setConfirming(true);
  }

  async function submit() {
    if (!checkComplete()) return;
    setPending("submit");
    setApiError("");
    try {
      onChange(await submitObservation(observation.observationId, answers));
      // The dashboard behind the dialog refreshes its observations.
      observerChanged();
    } catch (cause) {
      setApiError(`The observation was not submitted. ${messageOf(cause)}`);
      if (isConflict(cause)) onStale?.();
    } finally {
      setPending(null);
    }
  }

  return (
    <form noValidate onSubmit={requestSubmit} className="text-ink">
      <dl className="grid gap-x-8 gap-y-3 rounded-lg bg-mist px-5 py-4 text-sm sm:grid-cols-2">
        <div>
          <dt className="text-muted">Observee</dt>
          <dd className="font-semibold">{observation.observee.name}</dd>
          <dd className="text-muted">{observation.observee.department}</dd>
        </div>
        <div>
          <dt className="text-muted">Observed</dt>
          <dd className="font-semibold">
            {observation.observationDate
              ? formatDate(observation.observationDate)
              : "Not recorded"}
          </dd>
          {observation.startTime && (
            <dd className="text-muted">{formatTime(observation.startTime)}</dd>
          )}
        </div>
      </dl>

      <p className="mt-4 text-muted text-sm">
        Questions marked with an asterisk (*) are required. Save a draft at any
        time and come back to it later. When you are done, submit the form.
      </p>

      {missing.length > 0 && (
        <div
          role="alert"
          className="mt-4 rounded-lg border border-brand-red bg-peach px-4 py-3 text-sm"
        >
          <p className="font-semibold text-brand-red">
            {missing.length === 1
              ? "Answer this required question before you submit:"
              : `Answer these ${missing.length} required questions before you submit:`}
          </p>
          <ul className="mt-2 list-disc space-y-1 pl-5">
            {missing.map((criterion) => (
              <li key={criterion.criterionId}>
                <button
                  type="button"
                  className="text-left underline"
                  onClick={() => focusQuestion(criterion)}
                >
                  {criterion.criterionText}
                </button>
              </li>
            ))}
          </ul>
        </div>
      )}

      <ol className="mt-6 divide-y divide-line">
        {criteria.map((criterion, index) => {
          const props = {
            id: fieldId(criterion),
            number: index + 1,
            criterion,
            draft: drafts[criterion.criterionId] ?? EMPTY,
            invalid: missing.includes(criterion),
            onChange: (changes: Partial<Draft>) =>
              update(criterion.criterionId, changes),
          };
          return (
            <li key={criterion.criterionId} className="py-5 first:pt-0">
              {criterion.questionType === "RATING" ? (
                <RatingQuestion {...props} />
              ) : (
                <TextQuestion {...props} />
              )}
            </li>
          );
        })}
      </ol>

      <div className="border-line border-t pt-4">
        {apiError && (
          <p role="alert" className="mb-3 text-brand-red text-sm">
            {apiError} Your answers are still here.
          </p>
        )}
        {confirming ? (
          <section
            aria-labelledby={`${ids}-confirm`}
            className="rounded-lg border border-line bg-mist px-4 py-4"
          >
            <h3
              id={`${ids}-confirm`}
              ref={confirmHeading}
              tabIndex={-1}
              className="font-semibold"
            >
              Submit observation
            </h3>
            <p className="mt-1 text-sm">
              After you submit, you can no longer change your answers.
            </p>
            <div className="mt-4 flex flex-wrap justify-end gap-3">
              <button
                type="button"
                className={secondaryButton}
                disabled={busy}
                onClick={() => {
                  returnFocus.current = true;
                  setConfirming(false);
                }}
              >
                Keep editing
              </button>
              <button
                type="button"
                className={primaryButton}
                disabled={busy}
                onClick={submit}
              >
                {pending === "submit" ? "Submitting…" : "Submit"}
              </button>
            </div>
          </section>
        ) : (
          <div className="flex flex-wrap items-center justify-end gap-3">
            <div className="mr-auto text-sm">
              <p role="status" className="text-brand-green">
                {!dirty && savedAt
                  ? `Draft saved at ${clockTime(savedAt)}`
                  : ""}
              </p>
              {dirty && <p className="text-brand-orange">Unsaved changes</p>}
            </div>
            <button
              type="button"
              className={secondaryButton}
              disabled={busy}
              onClick={save}
            >
              {pending === "save" ? "Saving…" : "Save draft"}
            </button>
            <button
              ref={submitButton}
              type="submit"
              className={primaryButton}
              disabled={busy}
            >
              Submit
            </button>
          </div>
        )}
      </div>
    </form>
  );
}
