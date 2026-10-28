import { useState } from "react";
import { useSearchParams } from "react-router-dom";
import {
  type AcState,
  observerButton,
  postObserver,
  schedule,
  useObserverData,
} from "../../api/observerEligibility";
import { PageHeader } from "../../components/PageHeader";

export function AcObserverMatching() {
  const { data, error, reload } = useObserverData<AcState>("/ac");
  const [params] = useSearchParams();
  const [selected, setSelected] = useState<number | null>(
    Number(params.get("signup")) || null,
  );
  const [busy, setBusy] = useState(false);
  const [message, setMessage] = useState("");
  const [failure, setFailure] = useState("");
  const signups = data?.signups ?? [];
  const signup = signups.find((s) => s.signupId === selected) ?? signups[0];
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
  return (
    <div>
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
                      <span className="font-semibold">{c.name}</span>
                      <span className="text-sm text-muted">
                        {c.qualifyingCourses.join("; ")}
                      </span>
                      <span className="text-sm text-brand-green">
                        Available
                      </span>
                    </li>
                  ))}
                </ul>
              ) : (
                <p role="alert" className="mt-8 font-semibold text-brand-red">
                  No eligible observers. AC attention is required. This list
                  updates as more professors sign up.
                </p>
              )}
            </section>
            <section className="flex flex-col rounded-lg border border-line bg-mist p-6">
              <h2 className="text-xl font-semibold text-brand-green">
                Eligibility rules
              </h2>
              <ol className="mt-7 list-inside list-decimal space-y-5">
                <li>Same department</li>
                <li>Same course level this semester</li>
                <li>Available during at least one meeting day</li>
                <li>Observee cannot match themself</li>
                <li>Active professor signed up for evaluation</li>
              </ol>
              <div className="mt-auto pt-10">
                {!data.cycle.candidateListsOpen && !signup.released && (
                  <p className="mb-3 text-sm text-muted">
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
                    !signup.candidates.length ||
                    !data.cycle.candidateListsOpen
                  }
                  onClick={() => void act(true)}
                >
                  {signup.released
                    ? "Candidate List Sent"
                    : "Send Candidate List"}
                </button>
              </div>
            </section>
          </div>
        </>
      )}
    </div>
  );
}
