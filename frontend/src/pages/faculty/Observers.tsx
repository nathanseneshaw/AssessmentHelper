import { useState } from "react";
import { Link } from "react-router-dom";
import {
  type FacultyState,
  observerButton,
  observerChanged,
  postObserver,
  useObserverData,
} from "../../api/observerEligibility";
import { useAuth } from "../../auth/AuthContext";
import { Modal } from "../../components/Modal";

export function FacultyObservers() {
  const { user } = useAuth();
  const id = user?.professorId ?? 1;
  const { data, error } = useObserverData<FacultyState>(`/faculty/${id}`);
  const [selected, setSelected] = useState<number[]>([]);
  const [busy, setBusy] = useState(false);
  const [failure, setFailure] = useState("");
  const [sent, setSent] = useState<string[] | null>(null);
  const signup = data?.signup;
  async function send() {
    if (!signup) return;
    setBusy(true);
    setFailure("");
    try {
      await postObserver(`/faculty/${id}/signups/${signup.signupId}/requests`, {
        observerIds: selected,
      });
      setSent(
        signup.candidates
          .filter((c) => selected.includes(c.observerId))
          .map((c) => c.name),
      );
      observerChanged();
    } catch (cause) {
      setFailure(
        cause instanceof Error ? cause.message : "Unable to send requests.",
      );
    } finally {
      setBusy(false);
    }
  }
  if (sent)
    return (
      <Modal title="Observer Requests Sent">
        <p>
          Your observer selection has been saved and the requests have been
          sent.
        </p>
        <p className="my-6 rounded-lg bg-mist p-5 text-sm font-semibold">
          Observers selected: {sent.join(", ")}
        </p>
        <Link to="/faculty" className={observerButton}>
          Done
        </Link>
      </Modal>
    );
  return (
    <Modal
      title="Choose Observers"
      subtitle={
        signup
          ? `System-generated eligible observers for ${signup.section.label}`
          : undefined
      }
    >
      {(error || failure) && (
        <p role="alert" className="mb-4 text-brand-red">
          {failure || error}
        </p>
      )}
      {!data?.cycle ? (
        <p>Loading observers…</p>
      ) : !signup ? (
        <p>Sign up for evaluation first.</p>
      ) : !signup.released ? (
        <p>
          Your list is awaiting AC review. This page updates automatically when
          it is sent.
        </p>
      ) : (
        <>
          <div className="mb-6 rounded-lg bg-mist p-4 text-sm">
            <p className="mb-2 font-semibold text-brand-green">
              Eligibility used
            </p>
            Same department • Same course level • Available during a class
            meeting • Not self
          </div>
          <p className="mb-3 text-sm">
            Select one or more observers to request
          </p>
          <div className="space-y-3">
            {signup.candidates.map((c) => {
              const request = data.outgoing.find(
                (r) => r.professorId === c.professorId,
              );
              return (
                <label
                  key={c.observerId}
                  className="flex items-center gap-4 rounded-lg border border-line p-4 text-sm"
                >
                  <input
                    type="checkbox"
                    disabled={!!request || busy || !data.cycle.requestsOpen}
                    checked={selected.includes(c.observerId) || !!request}
                    onChange={(e) =>
                      setSelected((ids) =>
                        e.target.checked
                          ? [...ids, c.observerId]
                          : ids.filter((id) => id !== c.observerId),
                      )
                    }
                    className="size-4 accent-brand-green"
                  />
                  <span className="flex-1 font-semibold">{c.name}</span>
                  <span className="text-muted">{c.department}</span>
                  <span className="text-brand-green">
                    {request?.status ?? "Available"}
                  </span>
                </label>
              );
            })}
          </div>
          {!data.cycle.requestsOpen && (
            <p className="mt-4">The observation period is closed.</p>
          )}
          <div className="mt-8 flex items-center justify-between gap-3">
            <p className="text-xs text-muted">
              {signup.candidates.length} eligible observers generated •{" "}
              {selected.length} selected
            </p>
            <button
              type="button"
              className={observerButton}
              disabled={busy || !selected.length || !data.cycle.requestsOpen}
              onClick={send}
            >
              {busy ? "Sending…" : "Send Observer Requests"}
            </button>
          </div>
        </>
      )}
    </Modal>
  );
}
