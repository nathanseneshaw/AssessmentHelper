import { useState } from "react";
import {
  type FacultyState,
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
  async function respond(requestId: number, accept: boolean) {
    if (!id) return;
    setBusy(requestId);
    setFailure("");
    try {
      await postObserver(`/faculty/${id}/requests/${requestId}/respond`, {
        accept,
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
                    ? "Accepted Requests"
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
                      {status === "PENDING" && (
                        <div className="mt-4 flex gap-3">
                          <button
                            type="button"
                            className={observerButton}
                            disabled={busy !== null || !data.cycle.requestsOpen}
                            onClick={() => void respond(r.requestId, true)}
                          >
                            Accept
                          </button>
                          <button
                            type="button"
                            className="rounded-lg border border-line px-5 py-3 disabled:opacity-50"
                            disabled={busy !== null || !data.cycle.requestsOpen}
                            onClick={() => void respond(r.requestId, false)}
                          >
                            Decline
                          </button>
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
