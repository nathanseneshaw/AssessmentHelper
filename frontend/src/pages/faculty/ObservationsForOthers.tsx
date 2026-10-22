import {
  type FacultyState,
  schedule,
  useObserverData,
} from "../../api/observerEligibility";
import { useAuth } from "../../auth/AuthContext";
import { Modal } from "../../components/Modal";
export function FacultyObservationsForOthers() {
  const { user } = useAuth();
  const { data, error } = useObserverData<FacultyState>(
    `/faculty/${user?.professorId ?? 1}`,
  );
  const accepted =
    data?.incoming?.filter((r) => r.status === "CONFIRMED") ?? [];
  return (
    <Modal
      title="Observation Records for Others"
      subtitle="Accepted observations for the current cycle."
    >
      {error && (
        <p role="alert" className="text-brand-red">
          {error}
        </p>
      )}
      {!data ? (
        <p>Loading observations...</p>
      ) : !accepted.length ? (
        <p>No accepted observations yet.</p>
      ) : (
        <ul className="space-y-4">
          {accepted.map((r) => (
            <li key={r.requestId} className="rounded-lg border border-line p-4">
              <p className="font-semibold">
                {r.section.label} - {r.observeeName}
              </p>
              <p className="mt-2 text-sm">{schedule(r.section)}</p>
              <p className="text-sm text-muted">
                {r.windowStart} - {r.windowEnd} | Accepted
              </p>
            </li>
          ))}
        </ul>
      )}
    </Modal>
  );
}
