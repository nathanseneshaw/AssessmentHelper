import { useEffect, useRef, useState } from "react";
import { Link } from "react-router-dom";
import {
  getRequestOptions,
  type RequestOptions,
  sendObserverRequest,
} from "../../api/observerRequests";
import { useAuth } from "../../auth/AuthContext";
import { Modal } from "../../components/Modal";

const buttonClass =
  "rounded-lg bg-brand-green px-4 py-2 font-semibold text-sm text-white disabled:cursor-not-allowed disabled:opacity-50";
const statusLabels = {
  PENDING: "Pending",
  CONFIRMED: "Confirmed",
  DECLINED: "Declined",
  EXPIRED: "Expired",
};
const statusClasses = {
  PENDING: "bg-peach text-brand-orange",
  CONFIRMED: "bg-mist text-brand-green",
  DECLINED: "bg-btn-light text-ink/70",
  EXPIRED: "bg-btn-light text-ink/70",
};

function message(error: unknown) {
  return error instanceof Error
    ? error.message
    : "Unable to load evaluation requests.";
}

function dateTime(value: string) {
  return value.replace("T", " ").slice(0, 16);
}

function RequestForm({ professorId }: { professorId: number }) {
  const [options, setOptions] = useState<RequestOptions[]>([]);
  const [signupId, setSignupId] = useState("");
  const [matchIds, setMatchIds] = useState<string[]>([]);
  const [refresh, setRefresh] = useState(0);
  const [loading, setLoading] = useState(true);
  const [loadError, setLoadError] = useState("");
  const [sendError, setSendError] = useState("");
  const [success, setSuccess] = useState("");
  const [sending, setSending] = useState(false);
  const sendingRef = useRef(false);
  const version = useRef(0);

  // biome-ignore lint/correctness/useExhaustiveDependencies: Refresh explicitly reloads server-calculated statuses.
  useEffect(() => {
    const current = ++version.current;
    setLoading(true);
    setLoadError("");
    setSendError("");
    setSuccess("");
    setMatchIds([]);
    getRequestOptions(professorId)
      .then((result) => {
        if (current !== version.current) return;
        setOptions(result);
        setSignupId((previous) =>
          result.some((item) => String(item.signupId) === previous)
            ? previous
            : String(
                result.find((item) => !item.unavailableReason)?.signupId ??
                  result[0]?.signupId ??
                  "",
              ),
        );
      })
      .catch((error: unknown) => {
        if (current === version.current) setLoadError(message(error));
      })
      .finally(() => {
        if (current === version.current) setLoading(false);
      });
    return () => {
      version.current++;
    };
  }, [professorId, refresh]);

  const selected = options.find((item) => String(item.signupId) === signupId);
  const candidates =
    selected?.candidates.filter((item) =>
      matchIds.includes(String(item.matchId)),
    ) ?? [];
  const activeRequests =
    selected?.requests.filter(
      ({ request }) =>
        request.status === "PENDING" || request.status === "CONFIRMED",
    ) ?? [];
  const confirmed = activeRequests.find(
    ({ request }) => request.status === "CONFIRMED",
  );
  const remaining = Math.max(0, 5 - activeRequests.length);
  const blocked = Boolean(
    selected?.unavailableReason || confirmed || remaining === 0,
  );

  async function send() {
    if (
      !selected ||
      candidates.length === 0 ||
      candidates.length > remaining ||
      blocked ||
      sendingRef.current
    )
      return;
    sendingRef.current = true;
    setSending(true);
    setSendError("");
    setSuccess("");
    const current = version.current;
    try {
      for (const candidate of candidates) {
        const saved = await sendObserverRequest(
          professorId,
          selected.signupId,
          candidate.matchId,
        );
        if (current !== version.current) return;
        setOptions((previous) =>
          previous.map((item) =>
            item.signupId === saved.signupId
              ? {
                  ...item,
                  requests: [
                    ...item.requests.filter(
                      ({ request }) => request.requestId !== saved.requestId,
                    ),
                    { request: saved, observerName: candidate.observerName },
                  ],
                }
              : item,
          ),
        );
        setMatchIds((previous) =>
          previous.filter((id) => id !== String(candidate.matchId)),
        );
      }
      setSuccess(
        `Requests recorded for ${candidates.map((candidate) => candidate.observerName).join(", ")}.`,
      );
    } catch (error) {
      if (current === version.current) setSendError(message(error));
    } finally {
      sendingRef.current = false;
      if (current === version.current) setSending(false);
    }
  }

  return (
    <div className="space-y-5">
      <div className="flex items-center justify-between gap-3">
        <p className="text-ink/60 text-sm">
          Choose up to 5 proposed observers for your saved evaluation signup.
        </p>
        <button
          type="button"
          disabled={loading || sending}
          onClick={() => setRefresh((value) => value + 1)}
          className="shrink-0 rounded-lg border border-line px-3 py-2 text-sm disabled:opacity-50"
        >
          Refresh status
        </button>
      </div>
      {loading && <p role="status">Loading evaluation requests…</p>}
      {loadError && (
        <div role="alert" className="rounded-lg bg-peach p-3 text-sm">
          <p>{loadError}</p>
          <button
            type="button"
            onClick={() => setRefresh((value) => value + 1)}
            className="mt-2 font-semibold underline"
          >
            Retry loading
          </button>
        </div>
      )}
      {!loading && !loadError && options.length === 0 && (
        <p className="rounded-lg bg-mist p-4 text-sm">
          No evaluation signups yet.{" "}
          <Link
            to="/faculty/signup"
            className="font-semibold text-brand-green underline"
          >
            Sign up for an evaluation
          </Link>{" "}
          to get started.
        </p>
      )}
      {!loading && !loadError && selected && (
        <>
          <label className="block font-semibold text-sm">
            Evaluation signup
            <select
              value={signupId}
              disabled={sending}
              onChange={(event) => {
                setSignupId(event.target.value);
                setMatchIds([]);
                setSendError("");
                setSuccess("");
              }}
              className="mt-1 w-full rounded-lg border border-line bg-white px-3 py-2 font-normal"
            >
              {options.map((item) => (
                <option key={item.signupId} value={item.signupId}>
                  {item.semesterLabel} — {item.courseLabel}
                </option>
              ))}
            </select>
          </label>
          {selected.unavailableReason && (
            <p className="rounded-lg bg-mist p-3 text-sm">
              {selected.unavailableReason}
            </p>
          )}
          {activeRequests.length > 0 && (
            <p className="rounded-lg bg-mist p-3 text-sm">
              {confirmed
                ? `${confirmed.observerName} has confirmed this request.`
                : `${activeRequests.length} of 5 requests pending. The first acceptance confirms your observer and expires the other pending requests.`}
            </p>
          )}
          {!blocked && (
            <fieldset disabled={sending}>
              <legend className="mb-3 font-semibold">Proposed observers</legend>
              {selected.candidates.length === 0 ? (
                <p className="text-ink/60 text-sm">
                  No proposed observers yet. The assessment committee needs to
                  generate candidates for this signup.
                </p>
              ) : (
                <div className="space-y-2">
                  {selected.candidates.map((item) => (
                    <label
                      key={item.matchId}
                      className="flex cursor-pointer items-center gap-3 rounded-lg border border-line p-3"
                    >
                      <input
                        type="checkbox"
                        name="observer"
                        value={item.matchId}
                        checked={matchIds.includes(String(item.matchId))}
                        disabled={
                          activeRequests.some(
                            ({ request }) =>
                              request.observerId === item.observerId,
                          ) ||
                          (!matchIds.includes(String(item.matchId)) &&
                            matchIds.length >= remaining)
                        }
                        onChange={(event) => {
                          const id = event.target.value;
                          const checked = event.target.checked;
                          setMatchIds((previous) =>
                            checked
                              ? previous.length < remaining
                                ? [...previous, id]
                                : previous
                              : previous.filter((value) => value !== id),
                          );
                          setSendError("");
                        }}
                      />
                      <span>
                        <span className="block font-semibold text-sm">
                          {item.observerName}
                        </span>
                        <span className="block text-ink/60 text-xs">
                          {item.department}
                        </span>
                      </span>
                    </label>
                  ))}
                </div>
              )}
              <p className="mt-3 text-ink/70 text-sm">
                {matchIds.length} selected; up to {remaining} more requests
                available. The response window is 7 days.
              </p>
              <button
                type="button"
                onClick={send}
                disabled={candidates.length === 0 || sending}
                className={`mt-4 ${buttonClass}`}
              >
                {sending ? "Sending request…" : "Send evaluation request"}
              </button>
            </fieldset>
          )}
          {sendError && (
            <p role="alert" className="rounded-lg bg-peach p-3 text-sm">
              {sendError} Any requests already sent remain in the history. You
              can retry the remaining selections or refresh the status.
            </p>
          )}
          {success && (
            <p
              role="status"
              className="rounded-lg bg-mist p-3 text-brand-green text-sm"
            >
              {success}
            </p>
          )}
          <section
            aria-label="Request history"
            className="border-line border-t pt-4"
          >
            <h3 className="font-semibold">Request history</h3>
            {selected.requests.length === 0 ? (
              <p className="mt-2 text-ink/60 text-sm">
                No requests sent for this signup.
              </p>
            ) : (
              <ul className="mt-3 space-y-3">
                {[...selected.requests]
                  .reverse()
                  .map(({ request, observerName }) => (
                    <li
                      key={request.requestId}
                      className="rounded-lg border border-line p-3"
                    >
                      <div className="flex items-center justify-between gap-3">
                        <span className="font-semibold text-sm">
                          {observerName}
                        </span>
                        <span
                          className={`rounded-full px-3 py-1 font-semibold text-xs ${statusClasses[request.status]}`}
                        >
                          {statusLabels[request.status]}
                        </span>
                      </div>
                      <dl className="mt-2 grid grid-cols-[auto_1fr] gap-x-3 gap-y-1 text-ink/60 text-xs">
                        <dt>Requested</dt>
                        <dd>{dateTime(request.requestDate)}</dd>
                        <dt>Expires</dt>
                        <dd>{dateTime(request.expirationDate)}</dd>
                        {request.responseDate && (
                          <>
                            <dt>Responded</dt>
                            <dd>{dateTime(request.responseDate)}</dd>
                          </>
                        )}
                      </dl>
                    </li>
                  ))}
              </ul>
            )}
            <p className="mt-3 text-ink/50 text-xs">
              Times are in America/Chicago. Requests are saved in the app; email
              delivery is not connected.
            </p>
          </section>
        </>
      )}
    </div>
  );
}

export function FacultyObservers() {
  const { user } = useAuth();
  return (
    <Modal
      title="Choose Observers"
      subtitle="Send evaluation requests and track responses"
    >
      {user?.professorId ? (
        <RequestForm key={user.professorId} professorId={user.professorId} />
      ) : (
        <p role="alert">
          Your session has no faculty profile. Sign out and sign in again.
        </p>
      )}
    </Modal>
  );
}
