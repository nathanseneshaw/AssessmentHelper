import { useEffect, useId, useRef, useState } from "react";
import {
  type NotificationEmail,
  notificationTemplate,
  type OverdueFaculty,
  type OverdueQueue,
  rankLabel,
  sendNotification,
} from "./overdueApi";

const secondary =
  "rounded-md border border-line bg-[#f7f6f2] px-6 py-2 text-sm hover:bg-mist disabled:opacity-50";
const primary =
  "rounded-lg bg-brand-orange px-6 py-2 text-sm text-white hover:opacity-90 disabled:opacity-50";

export function OverdueEvaluations({
  queue,
  onClose,
  onNotified,
}: {
  queue: OverdueQueue;
  onClose: () => void;
  onNotified: (professorId: number, sentAt: string) => void;
}) {
  const dialog = useRef<HTMLDialogElement>(null);
  const titleId = useId();
  const [faculty, setFaculty] = useState<OverdueFaculty | null>(null);
  const [email, setEmail] = useState<NotificationEmail | null>(null);
  const [sent, setSent] = useState(false);
  const [sending, setSending] = useState(false);
  const [error, setError] = useState("");
  const returnFocus = useRef<number | null>(null);

  useEffect(() => {
    const previous = document.activeElement as HTMLElement | null;
    dialog.current?.showModal();
    const previousOverflow = document.body.style.overflow;
    document.body.style.overflow = "hidden";
    return () => {
      document.body.style.overflow = previousOverflow;
      previous?.focus();
    };
  }, []);

  useEffect(() => {
    if (sent)
      dialog.current?.querySelector<HTMLButtonElement>("[data-done]")?.focus();
    else if (faculty)
      dialog.current
        ?.querySelector<HTMLInputElement>("input[name=subject]")
        ?.focus();
  }, [sent, faculty]);

  function back() {
    setFaculty(null);
    setEmail(null);
    setError("");
    setSent(false);
    requestAnimationFrame(() =>
      dialog.current
        ?.querySelector<HTMLButtonElement>(
          `[data-professor-id="${returnFocus.current}"]`,
        )
        ?.focus(),
    );
  }

  async function send() {
    if (!faculty || !email || sending) return;
    setSending(true);
    setError("");
    try {
      const result = await sendNotification(
        queue.cycleId,
        faculty.professorId,
        email,
      );
      onNotified(faculty.professorId, result.sentAt);
      setSent(true);
    } catch (cause) {
      setError(
        cause instanceof Error
          ? cause.message
          : "Could not send the notification. Please try again.",
      );
    } finally {
      setSending(false);
    }
  }

  return (
    <dialog
      ref={dialog}
      aria-labelledby={titleId}
      onCancel={(event) => {
        event.preventDefault();
        if (!sending) {
          if (faculty) back();
          else onClose();
        }
      }}
      className={`fixed inset-0 m-auto max-h-[90vh] w-[calc(100%-2rem)] overflow-y-auto rounded-xl border border-line bg-white p-6 text-ink shadow-xl backdrop:bg-ink/40 ${sent ? "max-w-md" : faculty ? "max-w-xl" : "max-w-3xl"}`}
    >
      {sent ? (
        <>
          <h2 id={titleId} className="font-semibold text-xl text-brand-green">
            Email Sent
          </h2>
          <p role="status" className="mt-3 text-sm">
            The evaluation notification has been sent.
          </p>
          <p className="mt-2 text-xs text-muted">
            Simulated delivery to {faculty?.email}. No email was actually sent.
          </p>
          <div className="mt-10 flex justify-end">
            <button
              data-done
              type="button"
              onClick={back}
              className="rounded-lg bg-brand-green px-8 py-2 text-white"
            >
              Done
            </button>
          </div>
        </>
      ) : faculty && email ? (
        <>
          <div className="flex items-start justify-between gap-4">
            <div>
              <h2 id={titleId} className="font-bold text-xl">
                {faculty.lastNotifiedAt
                  ? "Follow Up with Faculty Member"
                  : "Notify Faculty Member"}
              </h2>
              <p className="mt-1 text-sm text-muted">
                Review and edit the email before sending.
              </p>
            </div>
            <button
              type="button"
              disabled={sending}
              onClick={back}
              className={secondary}
            >
              Back
            </button>
          </div>
          <form
            className="mt-7 flex flex-col gap-5"
            onSubmit={(event) => {
              event.preventDefault();
              void send();
            }}
          >
            <label className="flex flex-col gap-2 text-sm text-muted">
              To
              <input
                readOnly
                type="email"
                value={email.recipient}
                className="rounded-lg border border-line bg-[#f7f6f2] p-3 text-ink"
              />
            </label>
            <label className="flex flex-col gap-2 text-sm text-muted">
              Subject
              <input
                name="subject"
                required
                maxLength={255}
                disabled={sending}
                value={email.subject}
                onChange={(event) =>
                  setEmail({ ...email, subject: event.target.value })
                }
                className="rounded-lg border border-line p-3 text-ink"
              />
            </label>
            <label className="flex flex-col gap-2 text-sm text-muted">
              Message
              <textarea
                required
                maxLength={16000}
                disabled={sending}
                rows={11}
                value={email.body}
                onChange={(event) =>
                  setEmail({ ...email, body: event.target.value })
                }
                className="resize-y rounded-lg border border-line p-3 text-ink"
              />
            </label>
            <p className="text-xs text-muted">
              Email delivery is simulated. Notification history will be saved.
            </p>
            {error && (
              <p role="alert" className="text-sm text-brand-red">
                {error}
              </p>
            )}
            <div className="flex justify-end">
              <button
                type="submit"
                disabled={
                  sending || !email.subject.trim() || !email.body.trim()
                }
                className={primary}
              >
                {sending ? "Sending…" : "Send Email"}
              </button>
            </div>
          </form>
        </>
      ) : (
        <>
          <div className="flex items-start justify-between gap-4">
            <div>
              <h2 id={titleId} className="font-bold text-xl">
                Faculty Overdue for Evaluation
              </h2>
              <p className="mt-1 text-sm text-muted">
                {queue.cycleLabel} evaluation cycle • {queue.count} faculty
                overdue
              </p>
            </div>
            <button type="button" onClick={onClose} className={secondary}>
              Close
            </button>
          </div>
          <div className="mt-6 overflow-x-auto border-t border-line pt-4">
            <table className="w-full whitespace-nowrap text-left text-sm">
              <thead className="text-xs text-muted">
                <tr>
                  {[
                    "Faculty",
                    "Rank",
                    "Last Evaluation Cycle",
                    "Status",
                    "Notify",
                  ].map((heading) => (
                    <th
                      key={heading}
                      scope="col"
                      className="px-2 pb-3 font-normal"
                    >
                      {heading}
                    </th>
                  ))}
                </tr>
              </thead>
              <tbody>
                {queue.faculty.map((person) => (
                  <tr key={person.professorId} className="even:bg-[#f7f6f2]">
                    <td className="px-2 py-3 font-semibold">{person.name}</td>
                    <td className="px-2 py-3">{rankLabel(person.rank)}</td>
                    <td className="px-2 py-3">{person.lastEvaluationCycle}</td>
                    <td
                      className={`px-2 py-3 ${person.lastNotifiedAt ? "text-green-600" : "text-brand-orange"}`}
                      title={
                        person.lastNotifiedAt
                          ? `Last notified: ${person.lastNotifiedAt.replace("T", " ")}`
                          : undefined
                      }
                    >
                      {person.lastNotifiedAt ? "Notified" : "Overdue"}
                    </td>
                    <td className="px-2 py-3">
                      <button
                        type="button"
                        data-professor-id={person.professorId}
                        className={primary}
                        aria-label={`${person.lastNotifiedAt ? "Follow up with" : "Notify"} ${person.name}`}
                        onClick={() => {
                          returnFocus.current = person.professorId;
                          setFaculty(person);
                          setEmail(
                            notificationTemplate(person, queue.cycleLabel),
                          );
                          setError("");
                        }}
                      >
                        {person.lastNotifiedAt ? "Follow Up" : "Notify"}
                      </button>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
            {queue.faculty.length === 0 && (
              <p className="py-10 text-center text-muted">
                No faculty are overdue for evaluation.
              </p>
            )}
          </div>
        </>
      )}
    </dialog>
  );
}
