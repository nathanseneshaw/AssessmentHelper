import type {
  DashboardMetrics,
  ObservationTracking,
  TrackedObservation,
} from "../api/acDashboard";

export const metrics: DashboardMetrics = {
  cycleId: 1,
  cycleLabel: "Fall 2026",
  facultyDue: 13,
  listSize: 5,
  participation: { count: 2, total: 13 },
  observerUtilization: { count: 1, total: 6 },
  listSufficiency: { count: 2, total: 6 },
  missingObservations: 3,
};

const tracked = (
  signupId: number,
  observee: string,
  changes: Partial<TrackedObservation>,
): TrackedObservation => ({
  signupId,
  observeeId: signupId + 10,
  observee,
  observerId: null,
  observer: null,
  course: `CS 3345.00${signupId}`,
  listReleased: true,
  date: null,
  startTime: null,
  status: "AWAITING_OBSERVER",
  observeeSigned: false,
  observerSigned: false,
  completedAt: null,
  ...changes,
});

// One observation in each status.
export const observationTracking: ObservationTracking = {
  cycleId: 1,
  cycleLabel: "Fall 2026",
  observationDeadline: "2026-11-15",
  observations: [
    tracked(1, "Marlow Fenwick", {
      observerId: 21,
      observer: "Tessa Quillfeather",
      date: "2026-11-04",
      startTime: "08:30:00",
      status: "SCHEDULED",
    }),
    tracked(2, "Orrin Bramblecote", {
      observerId: 22,
      observer: "Juniper Ashgrove",
      date: "2026-10-20",
      startTime: "13:00:00",
      status: "COMPLETED",
      observeeSigned: true,
      observerSigned: true,
      completedAt: "2026-10-20T14:15:00",
    }),
    tracked(3, "Cassius Wrenfield", {
      observerId: 23,
      observer: "Delphine Marrowby",
      date: "2026-10-22",
      startTime: "10:00:00",
      status: "MISSING",
    }),
    tracked(4, "Ewan Thistlewood", {}),
  ],
};
