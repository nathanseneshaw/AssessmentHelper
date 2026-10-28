import type {
  ObservationDetail,
  ObservationSummary,
} from "../api/observations";
import type { FacultyState } from "../api/observerEligibility";
import { completedObservation } from "./observationFixtures";
import { faculty } from "./observerFixtures";

/** Olivia Observee, the observee of the shared observation fixtures, who also observes other professors. */
export const OLIVIA = {
  professorId: 21,
  name: "Olivia Observee",
  email: "olivia.observee@test.edu",
};

/** The observer-eligibility status the faculty dashboard loads for this professor. */
export function facultyStateFor(person: typeof OLIVIA): FacultyState {
  return {
    ...structuredClone(faculty),
    professor: {
      professorId: person.professorId,
      name: person.name,
      department: "Computer Science",
    },
  };
}

/** Olivia's CS 3345.001 observation as she sees it after Oscar submitted it. */
export function submittedObservation(): ObservationDetail {
  return {
    ...structuredClone(completedObservation),
    status: "SUBMITTED",
    viewerRole: "OBSERVEE",
  };
}

function summary(
  changes: Partial<ObservationSummary> &
    Pick<ObservationSummary, "observationId" | "role" | "status">,
): ObservationSummary {
  return {
    observeeName: OLIVIA.name,
    observerName: OLIVIA.name,
    courseLabel: "CS 3345.001",
    semesterLabel: "Fall 2026",
    cycleId: 2,
    observationDate: null,
    startTime: null,
    submittedAt: null,
    ...changes,
  };
}

/**
 * Everything on Olivia's list, in the API's order: undated first, then newest first. She is the observee of five
 * observations (one of each status) and the observer of four.
 */
export function oliviaObservations(): ObservationSummary[] {
  return [
    summary({
      observationId: 16,
      role: "OBSERVER",
      status: "ACCEPTED",
      observeeName: "Nina Notstarted",
      courseLabel: "CS 1337.002",
    }),
    summary({
      observationId: 12,
      role: "OBSERVEE",
      status: "ACCEPTED",
      observerName: "Omar Observer",
      courseLabel: "CS 4348.501",
    }),
    summary({
      observationId: 14,
      role: "OBSERVEE",
      status: "SCHEDULED",
      observerName: "Omar Observer",
      courseLabel: "CS 2305.001",
      observationDate: "2026-11-05",
      startTime: "10:00:00",
    }),
    summary({
      observationId: 17,
      role: "OBSERVER",
      status: "IN_PROGRESS",
      observeeName: "Ian Inprogress",
      courseLabel: "CS 2336.001",
      observationDate: "2026-10-27",
      startTime: "09:00:00",
    }),
    summary({
      observationId: 15,
      role: "OBSERVEE",
      status: "IN_PROGRESS",
      observerName: "Opal Observer",
      courseLabel: "CS 3354.001",
      observationDate: "2026-10-26",
      startTime: "14:00:00",
    }),
    summary({
      observationId: 18,
      role: "OBSERVER",
      status: "SUBMITTED",
      observeeName: "Sue Submitted",
      courseLabel: "CS 3341.001",
      observationDate: "2026-10-21",
      startTime: "11:00:00",
      submittedAt: "2026-10-21T12:00:00",
    }),
    summary({
      observationId: 11,
      role: "OBSERVEE",
      status: "SUBMITTED",
      observerName: "Oscar Observer",
      observationDate: "2026-10-20",
      startTime: "08:30:00",
      submittedAt: "2026-10-20T09:15:00",
    }),
    summary({
      observationId: 19,
      role: "OBSERVER",
      status: "COMPLETED",
      observeeName: "Cora Completed",
      courseLabel: "CS 1336.001",
      semesterLabel: "Spring 2026",
      cycleId: 1,
      observationDate: "2026-03-10",
      startTime: "10:00:00",
      submittedAt: "2026-03-10T11:00:00",
    }),
    summary({
      observationId: 9,
      role: "OBSERVEE",
      status: "COMPLETED",
      observerName: "Otto Observer",
      courseLabel: "CS 3345.002",
      semesterLabel: "Spring 2026",
      cycleId: 1,
      observationDate: "2026-03-09",
      startTime: "09:00:00",
      submittedAt: "2026-03-09T10:00:00",
    }),
  ];
}
