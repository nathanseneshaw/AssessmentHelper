import { type FormEvent, useState } from "react";
import { Link } from "react-router-dom";
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

export function FacultyEvaluationSignup() {
  const { user } = useAuth();
  const id = user?.professorId ?? 1;
  const { data, error } = useObserverData<FacultyState>(`/faculty/${id}`);
  const [selected, setSelected] = useState("");
  const start = data?.cycle?.signupDeadline
    ? new Date(Date.parse(`${data.cycle.signupDeadline}T00:00:00Z`) + 86400000)
        .toISOString()
        .slice(0, 10)
    : "";
  const [end, setEnd] = useState("");
  const [busy, setBusy] = useState(false);
  const [failure, setFailure] = useState("");
  const [saved, setSaved] = useState<string | null>(null);
  const section = data?.sections?.find((s) => s.sectionId === Number(selected));
  async function submit(e: FormEvent) {
    e.preventDefault();
    if (!section) return;
    setBusy(true);
    setFailure("");
    try {
      await postObserver(`/faculty/${id}/signups`, {
        sectionId: section.sectionId,
        windowStart: start,
        windowEnd: end,
      });
      setSaved(`${section.label} • ${schedule(section)}`);
      observerChanged();
    } catch (cause) {
      setFailure(cause instanceof Error ? cause.message : "Unable to sign up.");
    } finally {
      setBusy(false);
    }
  }
  if (saved)
    return (
      <Modal title="Evaluation Signup Complete">
        <p>Your course selection has been saved and signup has been sent.</p>
        <p className="my-6 rounded-lg bg-mist p-5 font-semibold">{saved}</p>
        <Link className={observerButton} to="/faculty">
          Done
        </Link>
      </Modal>
    );
  return (
    <Modal
      title="Sign Up for Evaluation"
      subtitle="Select the course and section you want observed."
    >
      {(error || failure) && (
        <p role="alert" className="mb-4 text-brand-red">
          {failure || error}
        </p>
      )}
      {!data?.cycle ? (
        <p>Loading your courses…</p>
      ) : data.signup ? (
        <p>You have already signed up for {data.signup.section.label}.</p>
      ) : (
        <form onSubmit={submit} className="space-y-5">
          <label className="block text-sm">
            Course / Section
            <select
              required
              value={selected}
              onChange={(e) => setSelected(e.target.value)}
              className="mt-2 w-full rounded-lg border border-line bg-white p-3"
            >
              <option value="">Choose a class</option>
              {data.sections.map((s) => (
                <option key={s.sectionId} value={s.sectionId}>
                  {s.label} — {s.courseName}
                </option>
              ))}
            </select>
          </label>
          {!data.sections.length && (
            <p>No teaching assignments are available for this semester.</p>
          )}
          <div className="grid grid-cols-2 gap-4">
            <label className="text-sm">
              Meeting Days
              <input
                readOnly
                value={section?.meetingDays ?? ""}
                className="mt-2 w-full rounded-lg border border-line p-3"
              />
            </label>
            <label className="text-sm">
              Meeting Time
              <input
                readOnly
                value={
                  section
                    ? `${section.startTime?.slice(0, 5) ?? "?"}–${section.endTime?.slice(0, 5) ?? "?"}`
                    : ""
                }
                className="mt-2 w-full rounded-lg border border-line p-3"
              />
            </label>
          </div>
          <fieldset>
            <legend className="mb-2 text-sm">
              Preferred Observation Window
            </legend>
            <p className="mb-3 text-xs text-muted">
              Observation begins the day after the signup deadline.
            </p>
            <div className="grid grid-cols-2 gap-4">
              <label className="text-sm">
                From
                <input
                  type="date"
                  required
                  value={start}
                  readOnly
                  min={start}
                  max={data.cycle.observationDeadline}
                  className="mt-2 w-full rounded-lg border border-line p-3"
                />
              </label>
              <label className="text-sm">
                Through
                <input
                  type="date"
                  required
                  value={end}
                  min={start}
                  max={data.cycle.observationDeadline}
                  onChange={(e) => setEnd(e.target.value)}
                  className="mt-2 w-full rounded-lg border border-line p-3"
                />
              </label>
            </div>
          </fieldset>
          <div className="rounded-lg bg-mist p-4 text-sm">
            <p className="mb-2 font-semibold text-brand-green">
              What happens next?
            </p>
            The system generates eligible observers using your department,
            course level, and meeting schedule. AC reviews and sends your list.
          </div>
          {!data.cycle.signupOpen && (
            <p role="status">
              Signup is closed. Deadline: {data.cycle.signupDeadline}.
            </p>
          )}
          <div className="flex justify-end">
            <button
              type="submit"
              className={observerButton}
              disabled={busy || !section || !data.cycle.signupOpen}
            >
              {busy ? "Saving…" : "Sign-Up"}
            </button>
          </div>
        </form>
      )}
    </Modal>
  );
}
