import type {
  ObservationDetail,
  ObservationTemplate,
} from "../api/observations";

export const template: ObservationTemplate = {
  templateId: 1,
  templateName: "Standard Classroom Observation",
  version: 1,
  criteria: [
    {
      criterionId: 1,
      criterionText: "Student Engagement",
      questionType: "RATING",
      required: true,
      displayOrder: 1,
      ratingMin: 1,
      ratingMax: 5,
    },
    {
      criterionId: 2,
      criterionText: "Preparedness & Organization",
      questionType: "RATING",
      required: true,
      displayOrder: 2,
      ratingMin: 1,
      ratingMax: 5,
    },
    {
      criterionId: 6,
      criterionText: "Strengths observed",
      questionType: "TEXT",
      required: true,
      displayOrder: 6,
      ratingMin: null,
      ratingMax: null,
    },
    {
      criterionId: 7,
      criterionText: "Suggestions for improvement",
      questionType: "TEXT",
      required: false,
      displayOrder: 7,
      ratingMin: null,
      ratingMax: null,
    },
  ],
};

/**
 * Oscar's completed Fall 2026 observation of Olivia's class, as Oscar sees it. The optional last question is
 * unanswered.
 */
export const completedObservation: ObservationDetail = {
  observationId: 11,
  status: "COMPLETED",
  viewerRole: "OBSERVER",
  observee: {
    professorId: 21,
    name: "Olivia Observee",
    department: "Computer Science",
  },
  observer: {
    professorId: 22,
    name: "Oscar Observer",
    department: "Computer Science",
  },
  course: {
    sectionId: 31,
    label: "CS 3345.001",
    courseName: "Data Structures",
    meetingDays: "MW",
    startTime: "08:30:00",
    endTime: "09:45:00",
    location: "ECSS 2.410",
  },
  semesterLabel: "Fall 2026",
  cycleId: 2,
  observationDate: "2026-10-20",
  startTime: "08:30:00",
  template,
  responses: [
    { criterionId: 1, rating: 4, comments: "Students asked questions." },
    { criterionId: 2, rating: 5, comments: null },
    {
      criterionId: 6,
      rating: null,
      comments: "Clear examples.\nGood pacing.",
    },
  ],
  submittedAt: "2026-10-20T09:15:00",
  canStart: false,
  canEdit: false,
};
