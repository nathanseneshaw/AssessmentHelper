import { type ReactNode, useEffect, useRef, useState } from "react";
import { useParams } from "react-router-dom";
import {
  formatDate,
  formatTime,
  formatTimestamp,
  isConflict,
  type ObservationCourse,
  type ObservationDetail,
  observationPath,
  startObservation,
  useApiResource,
} from "../../api/observations";
import { observerChanged } from "../../api/observerEligibility";
import { Modal } from "../../components/Modal";
import { ObservationRecord } from "../../components/ObservationRecord";
import { ObservationForm } from "./ObservationForm";

const buttonClass =
  "rounded-lg bg-brand-green px-4 py-2 font-semibold text-sm text-white hover:opacity-90 disabled:cursor-not-allowed disabled:opacity-50";

const LEAVE_WARNING =
  "Your unsaved answers will be lost. Save a draft first to keep them.";

function meetingTime(course: ObservationCourse) {
  const time =
    course.startTime && course.endTime
      ? `${formatTime(course.startTime)} – ${formatTime(course.endTime)}`
      : null;
  return [course.meetingDays, time].filter(Boolean).join(" ") || null;
}

function Item({
  label,
  value,
  detail,
}: {
  label: string;
  value: ReactNode;
  detail?: string | null;
}) {
  return (
    <div>
      <dt className="text-muted text-sm">{label}</dt>
      <dd className="mt-1 font-semibold">{value}</dd>
      {detail && <dd className="text-muted text-sm">{detail}</dd>}
    </div>
  );
}

function NotListed({ children = "Not listed" }: { children?: string }) {
  return <span className="font-normal text-muted">{children}</span>;
}

/** What the observer sees before the form opens. */
function StartPanel({
  observation,
  onStarted,
  onStale,
}: {
  observation: ObservationDetail;
  onStarted: (observation: ObservationDetail) => void;
  /** Loads the observation again after the server said it changed elsewhere. */
  onStale?: () => void;
}) {
  const [starting, setStarting] = useState(false);
  const [error, setError] = useState("");
  const { course, observee } = observation;
  const meets = meetingTime(course);

  async function start() {
    setStarting(true);
    setError("");
    try {
      onStarted(await startObservation(observation.observationId));
      // The dashboard behind the dialog refreshes its observations.
      observerChanged();
    } catch (cause) {
      setError(
        cause instanceof Error
          ? cause.message
          : "Could not start the observation. Please try again.",
      );
      setStarting(false);
      if (isConflict(cause)) onStale?.();
    }
  }

  return (
    <div className="@container space-y-4">
      <dl className="grid gap-x-8 gap-y-4 rounded-lg bg-mist px-5 py-4 @md:grid-cols-2">
        <Item
          label="Observee"
          value={observee.name}
          detail={observee.department}
        />
        <Item
          label="Course"
          value={course.label}
          detail={[course.courseName, meets].filter(Boolean).join(" · ")}
        />
        <Item label="Location" value={course.location ?? <NotListed />} />
        <Item
          label="Planned for"
          value={
            observation.observationDate ? (
              formatDate(observation.observationDate)
            ) : (
              <NotListed>Not scheduled</NotListed>
            )
          }
          detail={observation.startTime && formatTime(observation.startTime)}
        />
      </dl>
      <p className="text-muted text-sm">
        Start the observation when you are ready to fill in the form. Starting
        before the planned day, or without one, records today's date and the
        current time. You can save a draft and finish later.
      </p>
      {error && (
        <p role="alert" className="text-brand-red text-sm">
          {error}
        </p>
      )}
      <div className="flex justify-end border-line border-t pt-4">
        <button
          type="button"
          className={buttonClass}
          disabled={starting}
          onClick={start}
        >
          {starting ? "Starting…" : "Start observation"}
        </button>
      </div>
    </div>
  );
}

function Observation({ observationId }: { observationId: number }) {
  const { data, error, reload, setData } = useApiResource<ObservationDetail>(
    observationPath(observationId),
  );
  const [justSubmitted, setJustSubmitted] = useState(false);
  const notice = useRef<HTMLParagraphElement>(null);
  // Answers the observer has typed but not saved. Closing the dialog or the tab asks first.
  const [dirty, setDirty] = useState(false);
  const unsaved = dirty && data?.canEdit === true;

  // The form's buttons are gone once it is submitted, so focus moves to the confirmation.
  useEffect(() => {
    if (justSubmitted) notice.current?.focus();
  }, [justSubmitted]);

  // Reloading or closing the tab shows the browser's own leave-page warning.
  useEffect(() => {
    if (!unsaved) return;
    const warn = (event: BeforeUnloadEvent) => event.preventDefault();
    window.addEventListener("beforeunload", warn);
    return () => window.removeEventListener("beforeunload", warn);
  }, [unsaved]);

  function formChanged(observation: ObservationDetail) {
    if (observation.status === "SUBMITTED") setJustSubmitted(true);
    setData(observation);
  }

  return (
    <Modal
      wide
      title="Classroom Observation"
      subtitle={
        data ? `${data.course.label} · ${data.semesterLabel}` : undefined
      }
      leaveWarning={unsaved ? LEAVE_WARNING : undefined}
    >
      {error ? (
        <p role="alert" className="text-brand-red">
          {error}{" "}
          <button type="button" className="underline" onClick={reload}>
            Retry
          </button>
        </p>
      ) : !data ? (
        <p role="status">Loading observation...</p>
      ) : data.canStart ? (
        <StartPanel observation={data} onStarted={setData} onStale={reload} />
      ) : data.canEdit ? (
        <ObservationForm
          observation={data}
          onChange={formChanged}
          onDirtyChange={setDirty}
          onStale={reload}
        />
      ) : (
        <>
          {justSubmitted ? (
            <p
              ref={notice}
              role="status"
              tabIndex={-1}
              className="mb-5 rounded-lg bg-mist px-4 py-3 font-semibold text-brand-green text-sm outline-none"
            >
              <span aria-hidden="true">✓ </span>
              You submitted this observation.
            </p>
          ) : (
            <p className="mb-5 rounded-lg bg-mist px-4 py-3 text-sm">
              {data.viewerRole === "OBSERVEE"
                ? `Submitted by ${data.observer.name}`
                : "You submitted this observation"}
              {data.submittedAt && ` on ${formatTimestamp(data.submittedAt)}`}.
            </p>
          )}
          <ObservationRecord observation={data} />
        </>
      )}
    </Modal>
  );
}

export function FacultyObservationDetails() {
  const { id = "" } = useParams();
  if (!/^\d+$/.test(id)) {
    return (
      <Modal title="Classroom Observation">
        <p>This observation link is not valid.</p>
      </Modal>
    );
  }
  // A new id starts over, so nothing from the last observation carries across.
  return <Observation key={id} observationId={Number(id)} />;
}
