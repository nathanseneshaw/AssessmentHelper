export interface OverdueFaculty {
  professorId: number;
  name: string;
  rank: string;
  email: string;
  lastEvaluationCycle: string;
  nextDueDate: string;
  intervalYears: number;
  lastNotifiedAt: string | null;
}

export interface OverdueQueue {
  cycleId: number;
  cycleLabel: string;
  count: number;
  faculty: OverdueFaculty[];
}

export interface NotificationEmail {
  recipient: string;
  subject: string;
  body: string;
}

async function readResponse<T>(response: Response): Promise<T> {
  if (!response.ok) {
    const error = await response.json().catch(() => null);
    throw new Error(
      error?.detail ||
        error?.message ||
        "Unable to reach the evaluation service. Please try again.",
    );
  }
  return response.json();
}

export async function getOverdueQueue(
  signal?: AbortSignal,
): Promise<OverdueQueue> {
  return readResponse(await fetch("/api/overdue-evaluations", { signal }));
}

export async function sendNotification(
  cycleId: number,
  professorId: number,
  email: NotificationEmail,
) {
  return readResponse<{ sentAt: string }>(
    await fetch(
      `/api/overdue-evaluations/${cycleId}/professors/${professorId}/notifications`,
      {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify(email),
      },
    ),
  );
}

export function notificationTemplate(
  faculty: OverdueFaculty,
  cycleLabel: string,
): NotificationEmail {
  const followUp = Boolean(faculty.lastNotifiedAt);
  const history =
    faculty.lastEvaluationCycle === "Never evaluated"
      ? "Our records do not show a completed evaluation for you."
      : `Your last evaluation was completed in ${faculty.lastEvaluationCycle}.`;
  return {
    recipient: faculty.email,
    subject: `${followUp ? "Follow Up: " : ""}Faculty Evaluation Overdue - ${cycleLabel} Evaluation Required`,
    body: `Dear Professor ${faculty.name},\n\n${followUp ? "This is a follow-up reminder about your overdue faculty evaluation. " : "You are overdue for a faculty evaluation. "}${history} As ${faculty.rank === "FULL" ? "a" : "an"} ${rankLabel(faculty.rank)}, you are required to participate in an evaluation once every ${faculty.intervalYears === 1 ? "year" : `${faculty.intervalYears} years`}. Please complete the ${cycleLabel} evaluation cycle for your outstanding evaluation. Please sign in to the Assessment Helper and complete your evaluation sign-up by the stated deadline.\n\nIf you have already completed the required sign-up, no further action is needed at this time.\n\nThank you,\nAssessment Committee`,
  };
}

export function rankLabel(rank: string) {
  return `${rank.charAt(0)}${rank.slice(1).toLowerCase()} Professor`;
}
