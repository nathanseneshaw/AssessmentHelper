import { apiFetch } from "./client";

export interface FeedbackSurvey {
  cycleId: number;
  label: string;
  observationDeadline: string | null;
  feedbackDeadline: string | null;
  status: "AVAILABLE" | "NOT_OPEN" | "CLOSED" | "SUBMITTED" | "UNCONFIGURED";
  ratings?: (number | null)[] | null;
  comments?: string | null;
  submittedDate?: string | null;
}

export const feedbackPath = "/api/feedback-surveys";
export const feedbackQuestions = [
  "The observation process was clearly explained.",
  "Scheduling the observation was straightforward.",
  "Communication throughout the process was clear and timely.",
  "The observation process was organized.",
  "The feedback I received was useful.",
  "Overall, the evaluation process was effective.",
] as const;

export const submitFeedback = (
  cycleId: number,
  ratings: number[],
  comments: string,
) =>
  apiFetch<FeedbackSurvey>(`${feedbackPath}/${cycleId}`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ ratings, comments }),
  });
