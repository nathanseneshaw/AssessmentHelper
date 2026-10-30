import {
  type EvaluationDeadline,
  type EvaluationRecord,
  rankLabel,
} from "../../api/evaluations";
import {
  formatDate,
  formatMonthYear,
  type ObservationSummary,
} from "../../api/observations";
import type { Cycle } from "../../api/observerEligibility";

/** `highlight` is for work in progress, `alert` for something missed. */
export type SummaryTone = "highlight" | "alert";

export interface SummaryCard {
  label: string;
  value: string;
  note: string;
  tone?: SummaryTone;
}

export interface SummaryInputs {
  /** Null while loading or when the request failed. */
  deadline: EvaluationDeadline | null;
  /** The reason the deadline could not load, such as a professor with no rank. Empty when there is none. */
  deadlineError: string;
  /** Every cycle the professor was an observee in. Null while loading or when the request failed. */
  records: EvaluationRecord[] | null;
  /** The current cycle, once the status has loaded. */
  cycle: Cycle | undefined;
  /** The professor's observations, as observer and observee. Null while loading or when the request failed. */
  observations: ObservationSummary[] | null;
}

const LOADING = "—";

const isCompleted = (status: string | null) =>
  status?.toUpperCase() === "COMPLETED";

/** Where the professor stands in the current cycle. */
function statusCard({ deadline, records, cycle }: SummaryInputs): SummaryCard {
  const label = "Evaluation Status";
  if (!records || !cycle)
    return { label, value: LOADING, note: "Loading evaluation status..." };
  const current = records.find((r) => r.cycleId === cycle.cycleId);
  if (current && isCompleted(current.status))
    return { label, value: "Completed", note: "Current evaluation cycle" };
  if (current)
    return {
      label,
      value: "In Progress",
      note: "Current evaluation cycle",
      tone: "highlight",
    };
  if (deadline?.overdue)
    return {
      label,
      value: "Overdue",
      note: "Your evaluation is past due",
      tone: "alert",
    };
  // Due after this cycle's last deadline, so nothing is expected of the professor yet.
  const lastDeadline = cycle.feedbackDeadline ?? cycle.observationDeadline;
  if (deadline?.nextDueDate && deadline.nextDueDate > lastDeadline)
    return {
      label,
      value: "Not Due",
      note: `Next evaluation due ${formatMonthYear(deadline.nextDueDate)}`,
    };
  return { label, value: "Not Started", note: "Not signed up this cycle" };
}

/** How often the professor's rank is evaluated. */
function cycleCard({ deadline, deadlineError }: SummaryInputs): SummaryCard {
  const label = "Cycle";
  if (!deadline)
    return deadlineError
      ? { label, value: "Unavailable", note: deadlineError }
      : { label, value: LOADING, note: "Loading schedule..." };
  const years = deadline.evaluationIntervalYears;
  return {
    label,
    value: `Every ${years === 1 ? "Year" : `${years} Years`}`,
    note: `${rankLabel(deadline.professorRank) ?? "Faculty"} schedule`,
  };
}

/** When the professor is next due, or how long overdue. */
function nextDueCard({ deadline, deadlineError }: SummaryInputs): SummaryCard {
  const label = "Next Due";
  if (!deadline)
    return deadlineError
      ? { label, value: "Unavailable", note: deadlineError }
      : { label, value: LOADING, note: "Loading due date..." };
  if (!deadline.nextDueDate)
    return {
      label,
      value: "Not set",
      note: "No hire date or completed evaluation on record",
    };
  if (deadline.overdue)
    return {
      label,
      value: formatMonthYear(deadline.nextDueDate),
      note: `Was due ${formatDate(deadline.nextDueDate)}`,
      tone: "alert",
    };
  return {
    label,
    value: formatMonthYear(deadline.nextDueDate),
    note: deadline.lastEvaluationDate
      ? `Last evaluated ${formatDate(deadline.lastEvaluationDate)}`
      : "No completed evaluation yet",
  };
}

/** The observations of the professor's own classes in the current cycle, and how many the observers submitted. */
function observationsCard({ observations, cycle }: SummaryInputs): SummaryCard {
  const label = "Observations This Term";
  if (!observations || !cycle)
    return { label, value: LOADING, note: "Loading observations..." };
  const mine = observations.filter(
    (o) => o.role === "OBSERVEE" && o.cycleId === cycle.cycleId,
  );
  if (!mine.length)
    return { label, value: "0", note: "None of your classes observed yet" };
  const submitted = mine.filter(
    (o) => o.status === "SUBMITTED" || o.status === "COMPLETED",
  ).length;
  return {
    label,
    value: String(mine.length),
    note: `${submitted} submitted • ${mine.length - submitted} not yet submitted`,
  };
}

/** The four cards of the evaluation summary, in the order they are shown. */
export function evaluationSummary(inputs: SummaryInputs): SummaryCard[] {
  return [
    statusCard(inputs),
    cycleCard(inputs),
    nextDueCard(inputs),
    observationsCard(inputs),
  ];
}
