import { useEffect, useId, useRef, useState } from "react";
import { useSearchParams } from "react-router-dom";
import {
  type AcState,
  type Candidate,
  getEligibleObservers,
  matchingReason,
  observerButton,
  postObserver,
  type Signup,
  schedule,
  useObserverData,
} from "../../api/observerEligibility";
import { PageHeader } from "../../components/PageHeader";

const secondaryButton =
  "rounded-lg border border-line px-5 py-3 text-sm font-medium hover:bg-mist disabled:cursor-not-allowed disabled:opacity-50";

function ObserverCourses({
  candidate,
  onClose,
}: {
  candidate: Candidate;
  onClose: () => void;
}) {
  const dialog = useRef<HTMLDialogElement>(null);
  const titleId = useId();
  useEffect(() => {
    const previous = document.activeElement as HTMLElement | null;
    const overflow = document.body.style.overflow;
    dialog.current?.showModal();
    document.body.style.overflow = "hidden";
    return () => {
      document.body.style.overflow = overflow;
      previous?.focus();
    };
  }, []);
  return (
    <dialog
      ref={dialog}
      aria-labelledby={titleId}
      onCancel={(event) => {
        event.preventDefault();
        onClose();
      }}
      className="fixed inset-0 m-auto max-h-[85vh] w-[calc(100%-2rem)] max-w-xl overflow-y-auto rounded-xl border border-line bg-white p-6 text-ink shadow-xl backdrop:bg-ink/40"
    >
      <div className="flex items-start justify-between gap-4">
        <div>
          <h2 id={titleId} className="font-bold text-xl">
            Qualifying courses
          </h2>
          <p className="mt-1 text-sm text-muted">{candidate.name}</p>
        </div>
        <button
          type="button"
          onClick={onClose}
          className="cursor-pointer rounded-md border border-line px-3 py-1 text-sm hover:bg-mist"
        >
          Close
        </button>
      </div>
      <p className="mt-4 text-sm text-muted">
        Teaching history that qualifies this professor to observe the selected
        course.
      </p>
      {candidate.qualifyingCourses.length ? (
        <ul className="mt-4 space-y-2">
          {[...new Set(candidate.qualifyingCourses)].map((course) => (
            <li
              key={course}
              className="rounded-lg border border-line p-3 text-sm"
            >
              {course}
            </li>
          ))}
        </ul>
      ) : (
        <p className="mt-4 text-sm">No qualifying courses recorded.</p>
      )}
    </dialog>
  );
}

/** Lists everyone who passes the eligibility rules, not only the drawn candidates, and confirms one as the observer. */
function AssignObserver({
  signup,
  onClose,
  onAssigned,
}: {
  signup: Signup;
  onClose: () => void;
  onAssigned: (name: string) => void;
}) {
  const dialog = useRef<HTMLDialogElement>(null);
  const titleId = useId();
  const [eligible, setEligible] = useState<Candidate[] | null>(null);
  const [loadError, setLoadError] = useState("");
  const [choice, setChoice] = useState<number | null>(null);
  const [saving, setSaving] = useState(false);
  const [failure, setFailure] = useState("");
  useEffect(() => {
    const previous = document.activeElement as HTMLElement | null;
    const overflow = document.body.style.overflow;
    dialog.current?.showModal();
    document.body.style.overflow = "hidden";
    return () => {
      document.body.style.overflow = overflow;
      previous?.focus();
    };
  }, []);
  useEffect(() => {
    const controller = new AbortController();
    getEligibleObservers(signup.signupId, controller.signal)
      .then(setEligible)
      .catch((cause) => {
        if (!controller.signal.aborted)
          setLoadError(
            cause instanceof Error
              ? cause.message
              : "Unable to load eligible observers.",
          );
      });
    return () => controller.abort();
  }, [signup.signupId]);
  const listed = new Set(signup.candidates.map((c) => c.observerId));
  const chosen = eligible?.find((c) => c.observerId === choice);
  async function assign() {
    if (!chosen || saving) return;
    setSaving(true);
    setFailure("");
    try {
      await postObserver(`/signups/${signup.signupId}/assign`, {
        observerId: chosen.observerId,
      });
      onAssigned(chosen.name);
    } catch (cause) {
      setFailure(
        cause instanceof Error ? cause.message : "Unable to assign observer.",
      );
      setSaving(false);
    }
  }
  return (
    <dialog
      ref={dialog}
      aria-labelledby={titleId}
      onCancel={(event) => {
        event.preventDefault();
        onClose();
      }}
      className="fixed inset-0 m-auto max-h-[85vh] w-[calc(100%-2rem)] max-w-2xl rounded-xl border border-line bg-white p-6 text-ink shadow-xl backdrop:bg-ink/40"
    >
      <h2 id={titleId} className="font-bold text-xl">
        Assign an observer
      </h2>
      <p className="mt-1 text-sm text-muted">
        {signup.name} | {signup.section.label} | {schedule(signup.section)}
      </p>
      <form
        className="mt-6"
        onSubmit={(event) => {
          event.preventDefault();
          void assign();
        }}
      >
        <fieldset disabled={saving}>
          <legend className="text-sm text-muted">
            Professors who pass every eligibility rule
          </legend>
          {loadError ? (
            <p role="alert" className="mt-4 text-brand-red">
              {loadError}
            </p>
          ) : !eligible ? (
            <p className="mt-4">Loading eligible professors...</p>
          ) : eligible.length === 0 ? (
            <p className="mt-4 font-semibold text-brand-red">
              No professors currently pass the eligibility rules for this
              signup.
            </p>
          ) : (
            <ul className="mt-4 max-h-[45vh] space-y-2 overflow-auto">
              {eligible.map((c) => (
                <li key={c.observerId}>
                  <label className="flex cursor-pointer items-start gap-3 rounded-lg border border-line p-3 has-checked:border-brand-green has-checked:bg-mist">
                    <input
                      type="radio"
                      name="observer"
                      className="mt-1"
                      checked={choice === c.observerId}
                      onChange={() => setChoice(c.observerId)}
                    />
                    <span className="min-w-0 flex-1">
                      <span className="font-semibold">{c.name}</span>
                      {listed.has(c.observerId) && (
                        <span className="ml-2 text-brand-green text-xs">
                          On candidate list
                        </span>
                      )}
                      <span className="block text-muted text-sm">
                        {c.qualifyingCourses.join("; ")}
                      </span>
                    </span>
                  </label>
                </li>
              ))}
            </ul>
          )}
        </fieldset>
        {chosen && (
          <p className="mt-4 text-sm">
            {chosen.name} becomes the observer right away and both professors
            are notified. {signup.name} will not need to send a request.
          </p>
        )}
        {failure && (
          <p role="alert" className="mt-4 text-brand-red">
            {failure}
          </p>
        )}
        <div className="mt-6 flex justify-end gap-3">
          <button type="button" onClick={onClose} className={secondaryButton}>
            Cancel
          </button>
          <button
            type="submit"
            className={observerButton}
            disabled={!chosen || saving}
          >
            {saving ? "Assigning..." : "Assign Observer"}
          </button>
        </div>
      </form>
    </dialog>
  );
}

export function AcObserverMatching() {
  const { data, error, reload } = useObserverData<AcState>("/ac");
  const [params] = useSearchParams();
  const [selected, setSelected] = useState<number | null>(
    Number(params.get("signup")) || null,
  );
  const [busy, setBusy] = useState(false);
  const [assigning, setAssigning] = useState(false);
  const [coursesFor, setCoursesFor] = useState<Candidate | null>(null);
  const [message, setMessage] = useState("");
  const [failure, setFailure] = useState("");
  const signups = data?.signups ?? [];
  const signup = signups.find((s) => s.signupId === selected) ?? signups[0];
  const matching = data?.matchingCases?.find(
    (c) => c.signupId === signup?.signupId,
  );
  const index = signups.findIndex((s) => s.signupId === signup?.signupId);
  const step = (amount: number) =>
    setSelected(
      signups[(index + amount + signups.length) % signups.length]?.signupId ??
        null,
    );
  async function act(release: boolean) {
    setBusy(true);
    setMessage("");
    setFailure("");
    try {
      await postObserver(
        release ? `/signups/${signup?.signupId}/release` : "/generate",
      );
      setMessage(
        release
          ? "Candidate list sent. The professor can now request an observer."
          : "Candidate lists updated. Existing candidates are preserved.",
      );
      reload();
    } catch (cause) {
      setFailure(
        cause instanceof Error ? cause.message : "Unable to update candidates.",
      );
    } finally {
      setBusy(false);
    }
  }
  async function regenerate() {
    if (!signup || busy) return;
    setBusy(true);
    setMessage("");
    setFailure("");
    try {
      await postObserver(`/signups/${signup.signupId}/regenerate`);
      setMessage(
        "New recommendations generated. Review the list, then send it to the observee.",
      );
      reload();
    } catch (cause) {
      setFailure(
        cause instanceof Error
          ? cause.message
          : "Unable to generate new recommendations.",
      );
    } finally {
      setBusy(false);
    }
  }
  return (
    <div>
      {coursesFor && (
        <ObserverCourses
          candidate={coursesFor}
          onClose={() => setCoursesFor(null)}
        />
      )}
      <PageHeader
        title="Observer Matching"
        subtitle="Review system-generated eligible observers and resolve unmatched signups."
        action={
          <button
            type="button"
            className={observerButton}
            disabled={busy || !data?.cycle?.requestsOpen || !!error}
            onClick={() => void act(false)}
          >
            Generate Matches
          </button>
        }
      />
      {(error || failure) && (
        <p role="alert" className="mt-4 text-brand-red">
          {failure || error}{" "}
          <button type="button" className="underline" onClick={reload}>
            Retry
          </button>
        </p>
      )}
      {message && (
        <p role="status" className="mt-4 text-brand-green">
          {message}
        </p>
      )}
      {!data ? (
        <p className="mt-8">Loading signups...</p>
      ) : !signup ? (
        <p className="mt-8">
          No evaluation signups yet. Candidates are generated automatically when
          faculty sign up.
        </p>
      ) : (
        <>
          <div className="mt-7 flex items-center gap-5">
            <button
              type="button"
              aria-label="Previous observee"
              onClick={() => step(-1)}
              className="p-3 text-2xl text-brand-green"
            >
              {"\u2190"}
            </button>
            <h2 className="text-2xl font-medium">Observee: {signup.name}</h2>
            <button
              type="button"
              aria-label="Next observee"
              onClick={() => step(1)}
              className="p-3 text-2xl text-brand-green"
            >
              {"\u2192"}
            </button>
          </div>
          <p className="text-muted">
            {signup.section.label} | {schedule(signup.section)}
          </p>
          <p className="mt-1 text-sm text-muted">
            Observation window: {signup.windowStart} - {signup.windowEnd}
          </p>
          {matching && (
            <section
              className="mt-4 rounded-lg border border-line bg-white p-4"
              aria-label="Matching status"
            >
              <p
                className={`font-semibold ${matching.status === "UNMATCHED" ? "text-brand-red" : "text-brand-green"}`}
              >
                {matching.status === "UNMATCHED"
                  ? `Unmatched: ${matchingReason(matching)}`
                  : matching.status === "MATCHED"
                    ? "Matched"
                    : matching.status === "AWAITING_RESPONSE"
                      ? "Awaiting observer response"
                      : "Awaiting invitation"}
              </p>
              {matching.status === "UNMATCHED" && (
                <p className="mt-2 text-sm">
                  {matching.reason === "RECOMMENDATIONS_AWAITING_RELEASE"
                    ? "Review the new recommendations below, then send the candidate list to notify the observee."
                    : matching.reason === "INVITATIONS_UNSUCCESSFUL"
                      ? "All recommended observers have declined or their invitations have expired. No observer is confirmed."
                      : "No professors currently meet the eligibility requirements for this observation window."}
                  {matching.reason === "INVITATIONS_UNSUCCESSFUL" &&
                    (matching.unusedEligibleObserverCount > 0
                      ? ` ${matching.unusedEligibleObserverCount} additional eligible observer(s) have not been recommended.`
                      : " No additional eligible observers remain. This case still needs AC review.")}
                </p>
              )}
              {matching.invitations.length > 0 && (
                <div className="mt-4 overflow-x-auto">
                  <h3 className="font-semibold text-sm">Invitation history</h3>
                  <table className="mt-2 w-full text-left text-sm">
                    <thead>
                      <tr>
                        <th className="p-2">Observer</th>
                        <th className="p-2">Status</th>
                        <th className="p-2">Sent</th>
                        <th className="p-2">Expires</th>
                      </tr>
                    </thead>
                    <tbody>
                      {matching.invitations.map((r) => (
                        <tr key={r.requestId} className="border-t border-line">
                          <td className="p-2">{r.observerName}</td>
                          <td className="p-2">
                            {r.status === "CONFIRMED"
                              ? "Accepted"
                              : r.status === "PENDING"
                                ? "Pending"
                                : r.status === "DECLINED"
                                  ? "Declined"
                                  : r.status === "EXPIRED"
                                    ? "Expired"
                                    : r.status}
                          </td>
                          <td className="p-2">
                            {r.requestedAt?.replace("T", " ").slice(0, 19) ??
                              "—"}
                          </td>
                          <td className="p-2">
                            {r.expiresAt?.replace("T", " ").slice(0, 19) ?? "—"}
                          </td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
              )}
              {matching.previousRecommendations.length > 0 && (
                <details className="mt-4 text-sm">
                  <summary className="cursor-pointer font-semibold">
                    Previous recommendations (
                    {matching.previousRecommendations.length})
                  </summary>
                  <ul className="mt-2 space-y-2">
                    {matching.previousRecommendations.map((c) => (
                      <li key={c.observerId}>{c.name}</li>
                    ))}
                  </ul>
                </details>
              )}
            </section>
          )}
          {signup.observerName && (
            <p className="mt-3 font-semibold text-brand-green">
              Observer: {signup.observerName}
            </p>
          )}
          {assigning && (
            <AssignObserver
              signup={signup}
              onClose={() => setAssigning(false)}
              onAssigned={(name) => {
                setAssigning(false);
                setFailure("");
                setMessage(
                  `${name} is now the observer for ${signup.name}. Both professors have been notified.`,
                );
                reload();
              }}
            />
          )}
          <div className="mt-8 grid gap-6 lg:grid-cols-[2fr_1fr]">
            <section className="min-h-100 rounded-lg border border-line bg-white p-6">
              <h2 className="text-xl font-medium">Eligible observers</h2>
              <p className="mt-2 text-sm text-muted">
                Same department + same course level + available during a class
                meeting
              </p>
              {signup.candidates.length ? (
                <ul className="mt-8 space-y-10">
                  {signup.candidates.map((c) => (
                    <li
                      key={c.observerId}
                      className="flex flex-wrap items-center justify-between gap-4"
                    >
                      <div className="min-w-0 flex-1">
                        <p className="font-semibold">{c.name}</p>
                        <button
                          type="button"
                          aria-label={`See courses for ${c.name}`}
                          onClick={() => setCoursesFor(c)}
                          className="mt-1 cursor-pointer text-xs text-brand-green underline hover:opacity-80"
                        >
                          See Courses
                        </button>
                      </div>
                      <span className="text-sm text-muted">
                        {(() => {
                          const invitation = matching?.invitations.find(
                            (r) => r.observerId === c.observerId,
                          );
                          return invitation?.status === "DECLINED"
                            ? "Declined"
                            : invitation?.status === "EXPIRED"
                              ? "Expired"
                              : invitation?.status === "PENDING"
                                ? "Invitation pending"
                                : invitation?.status === "CONFIRMED"
                                  ? "Accepted"
                                  : "Recommended";
                        })()}
                      </span>
                    </li>
                  ))}
                </ul>
              ) : (
                <p role="alert" className="mt-8 font-semibold text-brand-red">
                  {matching?.reason === "NO_ELIGIBLE_OBSERVERS"
                    ? "No eligible observers. AC attention is required."
                    : "No observers on the recommendation list."}{" "}
                  This list updates as more professors sign up.
                </p>
              )}
            </section>
            <section className="flex flex-col rounded-lg border border-line bg-mist p-6">
              <h2 className="text-xl font-semibold text-brand-green">
                Eligibility rules
              </h2>
              <ol className="mt-7 list-inside list-decimal space-y-5">
                <li>Same department</li>
                <li>
                  Same course level taught this semester or within the prior two
                  years
                </li>
                <li>Available during at least one meeting day</li>
                <li>Observee cannot match themself</li>
                <li>Active professor signed up for evaluation</li>
              </ol>
              <div className="mt-auto flex flex-wrap gap-3 pt-10">
                {matching?.reason === "INVITATIONS_UNSUCCESSFUL" && (
                  <button
                    type="button"
                    className={observerButton}
                    disabled={
                      busy ||
                      !!error ||
                      !data.cycle.requestsOpen ||
                      !signup.released ||
                      !!signup.observerName ||
                      matching.unusedEligibleObserverCount === 0
                    }
                    onClick={() => void regenerate()}
                  >
                    Generate New Recommendations
                  </button>
                )}
                {!data.cycle.candidateListsOpen && !signup.released && (
                  <p className="w-full text-sm text-muted">
                    Candidate lists can be sent after the signup deadline (
                    {data.cycle.signupDeadline}, America/Chicago), while
                    observations are open.
                  </p>
                )}
                <button
                  type="button"
                  className={observerButton}
                  disabled={
                    busy ||
                    !!error ||
                    signup.released ||
                    !!signup.observerName ||
                    !signup.candidates.length ||
                    !data.cycle.candidateListsOpen
                  }
                  onClick={() => void act(true)}
                >
                  {signup.released
                    ? "Candidate List Sent"
                    : "Send Candidate List"}
                </button>
                <button
                  type="button"
                  className={secondaryButton}
                  disabled={
                    busy ||
                    !!error ||
                    !!signup.observerName ||
                    !data.cycle.requestsOpen
                  }
                  onClick={() => {
                    setMessage("");
                    setAssigning(true);
                  }}
                >
                  Assign Observer
                </button>
              </div>
            </section>
          </div>
        </>
      )}
    </div>
  );
}
