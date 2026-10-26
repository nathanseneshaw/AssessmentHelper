import type {
  AcState,
  Cycle,
  FacultyState,
  Signup,
} from "../api/observerEligibility";
export const cycle: Cycle = {
  cycleId: 1,
  semesterId: 1,
  label: "Fall 2026",
  startDate: "2026-08-24",
  signupDeadline: "2026-10-31",
  observationDeadline: "2026-11-15",
  signupOpen: true,
  requestsOpen: true,
};
export const section = {
  sectionId: 1,
  label: "CS 3345.002",
  courseName: "Algorithms",
  meetingDays: "MW",
  startTime: "08:30:00",
  endTime: "09:45:00",
};
export const signup: Signup = {
  signupId: 1,
  professorId: 1,
  name: "Professor 1",
  department: "Computer Science",
  section,
  windowStart: "2026-11-01",
  windowEnd: "2026-11-10",
  released: false,
  candidates: Array.from({ length: 5 }, (_, i) => ({
    observerId: i + 2,
    professorId: i + 2,
    name: `Professor ${i + 2}`,
    department: "Computer Science",
    level: "3000-level",
    qualifyingCourses: [`CS 3346.00${i + 2} - Data Structures`],
  })),
};
export const ac: AcState = {
  cycle,
  signups: [
    signup,
    {
      ...signup,
      signupId: 2,
      professorId: 8,
      name: "Professor 8",
      candidates: [],
    },
  ],
};
export const faculty: FacultyState = {
  cycle,
  professor: {
    professorId: 1,
    name: "Professor 1",
    department: "Computer Science",
  },
  signup: null,
  incoming: [],
  outgoing: [],
  notifications: [],
};
