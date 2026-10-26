import { apiFetch } from "./client";

export interface Semester {
  semesterId: number;
  term: string;
  academicYear: number;
}

export interface Course {
  courseId: number;
  courseCode: string;
  courseNumber: string;
  courseName: string;
}

export interface CourseSection {
  sectionId: number;
  courseId: number;
  semesterId: number;
  sectionNumber: string;
  meetingDays: string | null;
  startTime: string | null;
  endTime: string | null;
  location: string | null;
}

export interface EvaluationSignup {
  signupId: number;
  observeeId: number;
  sectionId: number;
  signupDate: string;
  status: string;
}

// Observations run from `start` (the day after the signup deadline) through any date up to `latestEnd`.
export interface ObservationWindow {
  start: string | null;
  latestEnd: string | null;
  end: string | null;
  signupDeadline: string | null;
  signupOpen: boolean;
}

export const getSignupSemesters = () =>
  apiFetch<Semester[]>("/api/semesters?signupOpen=true");
export const getCourses = () => apiFetch<Course[]>("/api/courses");
export const getTeachingSections = (professorId: number, semesterId: number) =>
  apiFetch<CourseSection[]>(
    `/api/sections?professorId=${professorId}&semesterId=${semesterId}`,
  );
export const getSignup = (professorId: number, semesterId: number) =>
  apiFetch<EvaluationSignup | undefined>(
    `/api/professors/${professorId}/evaluation-signups/${semesterId}`,
  );
export const getObservationWindow = (professorId: number, semesterId: number) =>
  apiFetch<ObservationWindow>(
    `/api/professors/${professorId}/evaluation-signups/${semesterId}/window`,
  );
export const saveSelection = (
  professorId: number,
  semesterId: number,
  sectionId: number,
  windowEnd?: string,
) =>
  apiFetch<EvaluationSignup>(
    `/api/professors/${professorId}/evaluation-signups/${semesterId}`,
    {
      method: "PUT",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ sectionId, windowEnd }),
    },
  );
