import { useState } from "react";
import { apiFetch } from "../../api/client";
import {
  type FacultyState,
  type ObserverRequest,
  observerBase,
  observerButton,
  observerChanged,
  postObserver,
  schedule,
  useObserverData,
} from "../../api/observerEligibility";
import { useAuth } from "../../auth/AuthContext";
import { Modal } from "../../components/Modal";

export function FacultyObserverRequests() {
  const { user } = useAuth();
  const id = user?.professorId;
  const { data, error } = useObserverData<FacultyState>(
    id ? `/faculty/${id}` : null,
  );
  const [busy, setBusy] = useState<number | null>(null);
  const [failure, setFailure] = useState("");
  async function respond(
    requestId: number,
    accept: boolean,
    observationDate?: string,
  ) {
    if (!id) return;
    setBusy(requestId);
    setFailure("");
    try {
      await postObserver(`/faculty/${id}/requests/${requestId}/respond`, {
        accept,
        observationDate,
      });
      observerChanged();
    } catch (cause) {
      setFailure(cause instanceof Error ? cause.message : "Unable to respond.");
    } finally {
      setBusy(null);
    }
  }
  return (
    <Modal
      title="Observer Requests"
      subtitle="Review requests and the observations you accepted."
    >
      {(error || failure || !id) && (
        <p role="alert" className="mb-4 text-brand-red">
          {failure ||
            error ||
            "Your session has no faculty profile. Sign out and sign in again."}
        </p>
      )}
      {!id ? null : !data?.cycle ? (
        <p>Loading requests…</p>
      ) : (
        <div className="max-h-[65vh] space-y-6 overflow-y-auto">
          {["PENDING", "CONFIRMED", "DECLINED", "EXPIRED"].map((status) => {
            const requests =
              data.incoming?.filter((r) => r.status === status) ?? [];
            return (
              <section key={status}>
                <h3 className="mb-3 font-semibold text-brand-green">
                  {status === "CONFIRMED"
                    ? "Accepted / Assigned Requests"
                    : status === "PENDING"
                      ? "Incoming Requests"
                      : status === "DECLINED"
                        ? "Declined Requests"
                        : "Expired Requests"}{" "}
                  ({requests.length})
                </h3>
                {!requests.length ? (
                  <p className="text-sm text-muted">No requests.</p>
                ) : (
                  requests.map((r) => (
                    <div
                      key={r.requestId}
                      className="mb-3 rounded-lg border border-line p-4 text-sm"
                    >
                      <p className="font-semibold">
                        {r.observeeName} — {r.section.label}
                      </p>
                      <p className="mt-1">{schedule(r.section)}</p>
                      <p className="mt-1 text-muted">
                        Observation window: {r.windowStart} – {r.windowEnd}
                      </p>
                      {r.assignedByAc && (
                        <p className="mt-1 font-medium">Assigned by AC</p>
                      )}
                      {r.observationDate && (
                        <p className="mt-1 font-medium">
                          Observation scheduled: {r.observationDate} at{" "}
                          {r.section.startTime.slice(0, 5)}
                        </p>
                      )}
                      {(status === "PENDING" ||
                        (status === "CONFIRMED" && !r.observationDate)) && (
                        <div className="mt-4">
                          <ObservationDatePicker
                            professorId={id}
                            request={r}
                            disabled={busy !== null || !data.cycle.requestsOpen}
                            accept={(date) => respond(r.requestId, true, date)}
                            decline={() => void respond(r.requestId, false)}
                          />
                        </div>
                      )}
                    </div>
                  ))
                )}
              </section>
            );
          })}
        </div>
      )}
    </Modal>
  );
}

function ObservationDatePicker({
  professorId,
  request,
  disabled,
  accept,
  decline,
}: {
  professorId: number;
  request: ObserverRequest;
  disabled: boolean;
  accept: (date: string) => Promise<void>;
  decline: () => void;
}) {
  const [dates, setDates] = useState<string[] | null>(null);
  const [selected, setSelected] = useState("");
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState("");
  const pending = request.status === "PENDING";
  const path = `/faculty/${professorId}/requests/${request.requestId}`;
  async function open() {
    setLoading(true);
    setError("");
    try {
      setDates(await apiFetch<string[]>(`${observerBase}${path}/dates`));
      setSelected("");
    } catch (cause) {
      setError(
        cause instanceof Error ? cause.message : "Unable to load dates.",
      );
    } finally {
      setLoading(false);
    }
  }
  async function save() {
    setLoading(true);
    setError("");
    try {
      if (pending) await accept(selected);
      else {
        await postObserver(`${path}/schedule`, { observationDate: selected });
        observerChanged();
      }
    } catch (cause) {
      setError(
        cause instanceof Error
          ? cause.message
          : "Unable to schedule observation.",
      );
    } finally {
      setLoading(false);
    }
  }
  const declineButton = pending ? (
    <button
      type="button"
      className="cursor-pointer rounded-lg border border-line px-5 py-3 disabled:opacity-50"
      disabled={disabled || loading}
      onClick={decline}
    >
      Decline
    </button>
  ) : null;
  return (
    <div>
      {dates === null ? (
        <div className="flex flex-wrap items-center gap-3">
          <button
            type="button"
            className={observerButton}
            disabled={disabled || loading}
            onClick={() => void open()}
          >
            {loading
              ? "Loading dates..."
              : pending
                ? "Accept"
                : "Choose Observation Date"}
          </button>
          {declineButton}
        </div>
      ) : (
        <div className="space-y-3">
          <label
            className="block font-medium"
            htmlFor={`date-${request.requestId}`}
          >
            Observation date
          </label>
          <p className="text-muted">
            Choose a class date within the observation window. Class time:{" "}
            {request.section.startTime.slice(0, 5)} to{" "}
            {request.section.endTime.slice(0, 5)}.
          </p>
          {dates.length === 0 ? (
            <p>
              No available class dates remain. Contact the Assessment Committee.
            </p>
          ) : (
            <select
              id={`date-${request.requestId}`}
              className="rounded border border-line p-2"
              value={selected}
              onChange={(event) => setSelected(event.target.value)}
            >
              <option value="">Choose a date</option>
              {dates.map((date) => (
                <option key={date} value={date}>
                  {date}
                </option>
              ))}
            </select>
          )}
          <div className="flex flex-wrap items-center gap-3">
            <button
              type="button"
              className={observerButton}
              disabled={disabled || loading || !selected}
              onClick={() => void save()}
            >
              {pending ? "Accept & Schedule" : "Save Observation Date"}
            </button>
            <button
              type="button"
              className="cursor-pointer rounded border border-line px-4 py-2"
              disabled={loading}
              onClick={() => setDates(null)}
            >
              Cancel
            </button>
            {declineButton}
          </div>
        </div>
      )}
      {error && (
        <p role="alert" className="mt-2 text-brand-red">
          {error}
        </p>
      )}
    </div>
  );
}
