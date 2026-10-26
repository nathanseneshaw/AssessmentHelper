import { type FormEvent, useEffect, useState } from "react";
import {
  type Course,
  type CourseSection,
  type EvaluationSignup,
  getCourses,
  getObservationWindow,
  getSignup,
  getSignupSemesters,
  getTeachingSections,
  type ObservationWindow,
  type Semester,
  saveSelection,
} from "../../api/courseSelection";
import { observerChanged } from "../../api/observerEligibility";
import { useAuth } from "../../auth/AuthContext";
import { Modal } from "../../components/Modal";

const selectClass =
  "mt-1 w-full rounded-lg border border-line bg-white px-3 py-2 text-sm disabled:bg-mist disabled:text-ink/50";
const buttonClass =
  "rounded-lg bg-brand-green px-4 py-2 font-semibold text-sm text-white disabled:cursor-not-allowed disabled:opacity-50";

function SelectionForm({ professorId }: { professorId: number }) {
  const [semesters, setSemesters] = useState<Semester[]>([]);
  const [courses, setCourses] = useState<Course[]>([]);
  const [sections, setSections] = useState<CourseSection[]>([]);
  const [semesterId, setSemesterId] = useState("");
  const [courseId, setCourseId] = useState("");
  const [sectionId, setSectionId] = useState("");
  const [saved, setSaved] = useState<EvaluationSignup>();
  const [observationWindow, setObservationWindow] =
    useState<ObservationWindow>();
  const [windowEnd, setWindowEnd] = useState("");
  const [loadingCatalog, setLoadingCatalog] = useState(true);
  const [loadingSections, setLoadingSections] = useState(false);
  const [saving, setSaving] = useState(false);
  const [catalogError, setCatalogError] = useState("");
  const [sectionError, setSectionError] = useState("");
  const [saveError, setSaveError] = useState("");
  const [message, setMessage] = useState("");
  const [retry, setRetry] = useState(0);

  // biome-ignore lint/correctness/useExhaustiveDependencies: Retry intentionally reloads the catalog after a failed request.
  useEffect(() => {
    let active = true;
    setLoadingCatalog(true);
    setCatalogError("");
    Promise.all([getSignupSemesters(), getCourses()])
      .then(([nextSemesters, nextCourses]) => {
        if (active) {
          setSemesters(nextSemesters);
          setCourses(nextCourses);
        }
      })
      .catch(() => {
        if (active)
          setCatalogError(
            "Could not load available semesters and courses. Please try again.",
          );
      })
      .finally(() => {
        if (active) setLoadingCatalog(false);
      });
    return () => {
      active = false;
    };
  }, [retry]);

  // biome-ignore lint/correctness/useExhaustiveDependencies: Retry intentionally reloads this semester after a failed request.
  useEffect(() => {
    if (!semesterId) return;
    let active = true;
    setLoadingSections(true);
    setSectionError("");
    Promise.all([
      getTeachingSections(professorId, Number(semesterId)),
      getSignup(professorId, Number(semesterId)),
      getObservationWindow(professorId, Number(semesterId)),
    ])
      .then(([nextSections, signup, nextWindow]) => {
        if (!active) return;
        setSections(nextSections);
        setSaved(signup);
        setObservationWindow(nextWindow);
        setWindowEnd(nextWindow.end ?? nextWindow.latestEnd ?? "");
        const selected = nextSections.find(
          (section) => section.sectionId === signup?.sectionId,
        );
        setCourseId(selected ? String(selected.courseId) : "");
        setSectionId(selected ? String(selected.sectionId) : "");
        if (signup) setMessage("Your saved course selection has been loaded.");
      })
      .catch((error) => {
        if (active)
          setSectionError(
            error instanceof Error
              ? error.message
              : "Could not load your sections.",
          );
      })
      .finally(() => {
        if (active) setLoadingSections(false);
      });
    return () => {
      active = false;
    };
  }, [professorId, semesterId, retry]);

  const availableCourses = courses.filter((course) =>
    sections.some((section) => section.courseId === course.courseId),
  );
  const availableSections = sections.filter(
    (section) => String(section.courseId) === courseId,
  );
  const selected = availableSections.find(
    (section) => String(section.sectionId) === sectionId,
  );
  const locked = saved !== undefined && saved.status !== "PENDING";
  const busy = loadingCatalog || loadingSections || saving;
  const closed =
    observationWindow !== undefined && !observationWindow.signupOpen;
  const unchanged =
    selected?.sectionId === saved?.sectionId &&
    windowEnd === observationWindow?.end;

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!selected || !windowEnd || busy || locked || closed || sectionError)
      return;
    setSaving(true);
    setSaveError("");
    setMessage("");
    try {
      const signup = await saveSelection(
        professorId,
        Number(semesterId),
        selected.sectionId,
        windowEnd,
      );
      setSaved(signup);
      setObservationWindow((current) =>
        current ? { ...current, end: windowEnd } : current,
      );
      observerChanged();
      setMessage("Your course and section have been saved for evaluation.");
    } catch (error) {
      setSaveError(
        error instanceof Error
          ? error.message
          : "Could not save your selection. Please try again.",
      );
    } finally {
      setSaving(false);
    }
  }

  if (loadingCatalog)
    return <p role="status">Loading semesters and courses…</p>;
  if (catalogError)
    return (
      <div className="space-y-3">
        <p role="alert">{catalogError}</p>
        <button
          type="button"
          className={buttonClass}
          onClick={() => setRetry((value) => value + 1)}
        >
          Retry
        </button>
      </div>
    );
  if (!semesters.length)
    return <p>No evaluation cycles are open for sign-up.</p>;

  return (
    <form onSubmit={submit} className="space-y-4">
      <p className="text-ink/60 text-sm">
        Choose a semester, then one of the courses and sections you teach.
      </p>
      <label className="block font-medium text-sm" htmlFor="signup-semester">
        Semester
        <select
          id="signup-semester"
          className={selectClass}
          value={semesterId}
          disabled={saving}
          onChange={(event) => {
            setSemesterId(event.target.value);
            setCourseId("");
            setSectionId("");
            setSections([]);
            setSaved(undefined);
            setObservationWindow(undefined);
            setWindowEnd("");
            setSectionError("");
            setSaveError("");
            setMessage("");
            setLoadingSections(Boolean(event.target.value));
          }}
        >
          <option value="">Select a semester</option>
          {semesters.map((semester) => (
            <option key={semester.semesterId} value={semester.semesterId}>
              {semester.term} {semester.academicYear}
            </option>
          ))}
        </select>
      </label>
      {loadingSections && (
        <p role="status" className="text-sm">
          Loading your sections…
        </p>
      )}
      {sectionError && (
        <div className="space-y-2">
          <p role="alert" className="text-sm text-red-700">
            {sectionError}
          </p>
          <button
            type="button"
            className={buttonClass}
            onClick={() => setRetry((value) => value + 1)}
          >
            Retry
          </button>
        </div>
      )}
      {semesterId &&
        !loadingSections &&
        !sectionError &&
        sections.length === 0 && (
          <p className="text-sm">
            You have no teaching sections in this semester.
          </p>
        )}
      {locked && (
        <p className="rounded-lg bg-mist p-3 text-sm">
          This signup is {saved.status.toLowerCase()}. Contact the committee to
          change its course or section.
        </p>
      )}
      <label className="block font-medium text-sm" htmlFor="signup-course">
        Course
        <select
          id="signup-course"
          className={selectClass}
          value={courseId}
          disabled={
            busy || locked || !availableCourses.length || Boolean(sectionError)
          }
          onChange={(event) => {
            setCourseId(event.target.value);
            setSectionId("");
            setSaveError("");
            setMessage("");
          }}
        >
          <option value="">Select a course</option>
          {availableCourses.map((course) => (
            <option key={course.courseId} value={course.courseId}>
              {course.courseCode} {course.courseNumber} — {course.courseName}
            </option>
          ))}
        </select>
      </label>
      <label className="block font-medium text-sm" htmlFor="signup-section">
        Section
        <select
          id="signup-section"
          className={selectClass}
          value={sectionId}
          disabled={busy || locked || !courseId || Boolean(sectionError)}
          onChange={(event) => {
            setSectionId(event.target.value);
            setSaveError("");
            setMessage("");
          }}
        >
          <option value="">Select a section</option>
          {availableSections.map((section) => (
            <option key={section.sectionId} value={section.sectionId}>
              Section {section.sectionNumber}
            </option>
          ))}
        </select>
      </label>
      {selected && (
        <dl className="grid grid-cols-[auto_1fr] gap-x-4 gap-y-1 rounded-lg bg-mist p-3 text-sm">
          <dt className="font-medium">Meeting days</dt>
          <dd>{selected.meetingDays || "To be announced"}</dd>
          <dt className="font-medium">Time</dt>
          <dd>
            {selected.startTime && selected.endTime
              ? `${selected.startTime.slice(0, 5)} – ${selected.endTime.slice(0, 5)}`
              : "To be announced"}
          </dd>
          <dt className="font-medium">Location</dt>
          <dd>{selected.location || "To be announced"}</dd>
        </dl>
      )}
      {selected && observationWindow && (
        <fieldset className="space-y-2">
          <legend className="font-medium text-sm">
            Preferred observation window
          </legend>
          <p className="text-ink/60 text-xs">
            Observations begin the day after the signup deadline. The committee
            reviews the observers generated for your class before you can
            request one.
          </p>
          <div className="grid grid-cols-2 gap-4">
            <label className="block text-sm" htmlFor="signup-window-start">
              From
              <input
                id="signup-window-start"
                type="date"
                readOnly
                value={observationWindow.start ?? ""}
                className={selectClass}
              />
            </label>
            <label className="block text-sm" htmlFor="signup-window-end">
              Through
              <input
                id="signup-window-end"
                type="date"
                required
                value={windowEnd}
                min={observationWindow.start ?? undefined}
                max={observationWindow.latestEnd ?? undefined}
                disabled={busy || locked || closed}
                onChange={(event) => {
                  setWindowEnd(event.target.value);
                  setSaveError("");
                  setMessage("");
                }}
                className={selectClass}
              />
            </label>
          </div>
        </fieldset>
      )}
      {closed && (
        <p className="rounded-lg bg-mist p-3 text-sm">
          Signup closed on {observationWindow?.signupDeadline}.
        </p>
      )}
      {saveError && (
        <p role="alert" className="text-sm text-red-700">
          {saveError}
        </p>
      )}
      {message && (
        <p role="status" className="text-brand-green text-sm">
          {message}
        </p>
      )}
      <div className="flex justify-end border-line border-t pt-4">
        <button
          type="submit"
          className={buttonClass}
          disabled={
            !selected ||
            !windowEnd ||
            busy ||
            locked ||
            closed ||
            Boolean(sectionError) ||
            unchanged
          }
        >
          {saving ? "Saving…" : "Save selection"}
        </button>
      </div>
    </form>
  );
}

export function FacultyEvaluationSignup() {
  const { user, logout } = useAuth();
  return (
    <Modal
      title="Sign Up for Evaluation"
      subtitle="Select the course and section you want to be observed in"
    >
      {user?.professorId ? (
        <SelectionForm professorId={user.professorId} />
      ) : (
        <div className="space-y-3">
          <p>
            Your session does not include a faculty profile. Sign in again to
            select your courses.
          </p>
          <button type="button" className={buttonClass} onClick={logout}>
            Sign in again
          </button>
        </div>
      )}
    </Modal>
  );
}
