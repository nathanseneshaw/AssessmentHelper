import { useEffect, useState } from "react";
import { Link, useSearchParams } from "react-router-dom";
import {
  type FeedbackSurvey,
  feedbackPath,
  feedbackQuestions,
  submitFeedback,
} from "../../api/feedback";
import { formatDate, useApiResource } from "../../api/observations";
import { observerButton, observerChanged } from "../../api/observerEligibility";
import { Modal } from "../../components/Modal";

export function FacultyFeedback() {
  const { data, error, reload, setData } =
    useApiResource<FeedbackSurvey[]>(feedbackPath);
  const [params] = useSearchParams();
  const [selected, setSelected] = useState(Number(params.get("cycle")) || 0);
  const [ratings, setRatings] = useState<number[]>(Array(6).fill(0));
  const [comments, setComments] = useState("");
  const [busy, setBusy] = useState(false);
  const [failure, setFailure] = useState("");
  const [result, setResult] = useState<"complete" | "canceled" | null>(null);
  const survey =
    data?.find((s) => s.cycleId === selected) ??
    data?.find((s) => s.status === "AVAILABLE") ??
    data?.[0];
  const dirty = ratings.some(Boolean) || comments.length > 0;
  useEffect(() => {
    if (!dirty || result) return;
    const warn = (event: BeforeUnloadEvent) => {
      event.preventDefault();
      event.returnValue = "";
    };
    window.addEventListener("beforeunload", warn);
    return () => window.removeEventListener("beforeunload", warn);
  }, [dirty, result]);

  async function submit() {
    if (!survey || busy) return;
    setBusy(true);
    setFailure("");
    try {
      const submitted = await submitFeedback(survey.cycleId, ratings, comments);
      setData(
        (previous) =>
          previous?.map((s) =>
            s.cycleId === submitted.cycleId ? submitted : s,
          ) ?? [submitted],
      );
      setSelected(submitted.cycleId);
      setRatings(Array(6).fill(0));
      setComments("");
      setResult("complete");
      observerChanged();
    } catch (cause) {
      setFailure(
        cause instanceof Error ? cause.message : "Unable to submit feedback.",
      );
      reload();
    } finally {
      setBusy(false);
    }
  }

  if (result)
    return (
      <Modal
        title={
          result === "complete"
            ? "Feedback Survey Complete"
            : "Feedback Survey Canceled"
        }
      >
        <p role="status">
          {result === "complete"
            ? "Your feedback has been submitted. Thank you!"
            : "Your feedback has not been submitted. Please return before the feedback-survey deadline."}
        </p>
        {result === "complete" && (
          <button
            type="button"
            onClick={() => setResult(null)}
            className="mt-5 mr-3 cursor-pointer rounded-lg bg-brand-green px-5 py-3 text-sm text-white"
          >
            View Submitted Survey
          </button>
        )}
        <Link to="/faculty" className={`${observerButton} mt-5 inline-block`}>
          Done
        </Link>
      </Modal>
    );

  return (
    <Modal
      title="Process Feedback Survey"
      wide
      subtitle={survey?.label}
      leaveWarning={
        busy
          ? "Your submission is still in progress. Leaving will not cancel it."
          : dirty
            ? "Your survey responses have not been submitted. You can return before the deadline."
            : undefined
      }
    >
      {error && (
        <p role="alert">
          {error}{" "}
          <button type="button" className="underline" onClick={reload}>
            Retry
          </button>
        </p>
      )}
      {!data && !error && <p>Loading feedback surveys...</p>}
      {data?.length === 0 && (
        <p>No feedback surveys are available for your evaluation cycles.</p>
      )}
      {data && data.length > 1 && (
        <label className="mb-4 block">
          Evaluation cycle
          <select
            className="ml-3 rounded border border-line p-2"
            value={survey?.cycleId}
            disabled={busy}
            onChange={(e) => {
              if (
                dirty &&
                !window.confirm(
                  "Discard your unsubmitted survey responses and change cycle?",
                )
              )
                return;
              setSelected(Number(e.target.value));
              setRatings(Array(6).fill(0));
              setComments("");
              setFailure("");
            }}
          >
            {data.map((s) => (
              <option key={s.cycleId} value={s.cycleId}>
                {s.label}
              </option>
            ))}
          </select>
        </label>
      )}
      {survey?.status === "NOT_OPEN" && (
        <p>
          The survey becomes available after the observation deadline,{" "}
          {formatDate(survey.observationDeadline ?? "")}. Submit by{" "}
          {formatDate(survey.feedbackDeadline ?? "")}.
        </p>
      )}
      {survey?.status === "CLOSED" && (
        <p>
          The feedback-survey deadline passed on{" "}
          {formatDate(survey.feedbackDeadline ?? "")}. Submissions are closed.
        </p>
      )}
      {survey?.status === "SUBMITTED" && (
        <section>
          <p role="status" className="font-semibold text-brand-green">
            You have already submitted feedback for this evaluation cycle. Thank
            you!
          </p>
          {survey.submittedDate && (
            <p className="mt-2 text-sm">
              Submitted {formatDate(survey.submittedDate)}
            </p>
          )}
          <p className="mt-3 text-sm text-muted">
            1 = strongly disagree; 5 = strongly agree.
          </p>
          <dl className="mt-4 space-y-4">
            {feedbackQuestions.map((question, index) => (
              <div key={question} className="rounded border border-line p-3">
                <dt className="text-sm font-semibold">{question}</dt>
                <dd className="mt-2 text-brand-green">
                  {survey.ratings?.[index] != null
                    ? `${survey.ratings[index]} / 5`
                    : "No rating recorded"}
                </dd>
              </div>
            ))}
            <div className="rounded border border-line p-3">
              <dt className="text-sm font-semibold">Additional Comments</dt>
              <dd className="mt-2 whitespace-pre-wrap text-sm">
                {survey.comments || "No additional comments."}
              </dd>
            </div>
          </dl>
        </section>
      )}
      {survey?.status === "UNCONFIGURED" && (
        <p>
          The survey deadlines have not been configured correctly. Please
          contact the Assessment Committee.
        </p>
      )}
      {failure && (
        <p role="alert" className="mt-3 text-brand-red">
          {failure}
        </p>
      )}
      {survey?.status === "AVAILABLE" && (
        <form
          onSubmit={(e) => {
            e.preventDefault();
            void submit();
          }}
        >
          <p className="border-t border-line pt-4 text-sm">
            Please provide feedback about your experience with the faculty
            evaluation and observation process.
          </p>
          <p className="mt-1 text-sm text-muted">
            1 = strongly disagree; 5 = strongly agree.
          </p>
          <p className="mt-2 text-sm text-brand-red">
            Due {formatDate(survey.feedbackDeadline ?? "")}.
          </p>
          <fieldset disabled={busy} className="mt-4 space-y-4">
            {feedbackQuestions.map((question, index) => (
              <fieldset
                key={question}
                className="rounded border border-line p-3"
              >
                <legend className="px-1 text-sm font-semibold">
                  {question}{" "}
                  <span aria-hidden="true" className="text-brand-red">
                    *
                  </span>
                </legend>
                <div className="mt-2 flex justify-around">
                  {[1, 2, 3, 4, 5].map((value) => (
                    <label
                      key={value}
                      className="flex cursor-pointer flex-col items-center gap-1 px-3 py-1 text-sm"
                    >
                      <input
                        type="radio"
                        required
                        name={`rating-${index}`}
                        value={value}
                        checked={ratings[index] === value}
                        className="accent-brand-green"
                        onChange={() =>
                          setRatings((previous) =>
                            previous.map((r, i) => (i === index ? value : r)),
                          )
                        }
                      />
                      {value}
                    </label>
                  ))}
                </div>
              </fieldset>
            ))}
            <label
              className="block text-sm font-semibold"
              htmlFor="feedback-comments"
            >
              Additional Comments{" "}
              <span className="font-normal text-muted">(optional)</span>
            </label>
            <textarea
              id="feedback-comments"
              maxLength={4000}
              rows={4}
              value={comments}
              onChange={(e) => setComments(e.target.value)}
              placeholder="Share any additional feedback about the evaluation process."
              className="w-full rounded border border-line p-3 text-sm"
            />
          </fieldset>
          <div className="mt-4 flex justify-end gap-3">
            <button
              type="button"
              disabled={busy}
              onClick={() => setResult("canceled")}
              className="cursor-pointer rounded-lg bg-peach px-5 py-3 text-sm disabled:cursor-not-allowed"
            >
              Cancel
            </button>
            <button
              type="submit"
              disabled={busy || ratings.some((r) => !r)}
              className="rounded-lg bg-brand-green px-5 py-3 text-sm font-medium text-white hover:opacity-90 disabled:cursor-not-allowed disabled:bg-btn-gray"
            >
              {busy ? "Submitting..." : "Submit"}
            </button>
          </div>
        </form>
      )}
    </Modal>
  );
}
