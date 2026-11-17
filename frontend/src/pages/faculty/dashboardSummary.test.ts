import { expect, test } from "bun:test";
import type {
  EvaluationDeadline,
  EvaluationRecord,
} from "../../api/evaluations";
import type { ObservationSummary } from "../../api/observations";
import { cycle } from "../../test/observerFixtures";
import { evaluationSummary, type SummaryInputs } from "./dashboardSummary";

const deadline: EvaluationDeadline = {
  professorId: 21,
  professorRank: "ASSOCIATE",
  evaluationIntervalYears: 2,
  lastEvaluationDate: "2026-03-10",
  nextDueDate: "2028-03-10",
  overdue: false,
};

const record = (changes: Partial<EvaluationRecord>): EvaluationRecord => ({
  cycleId: 1,
  term: "Spring",
  academicYear: 2026,
  status: "COMPLETED",
  observationCount: 2,
  lastObservationCompletedAt: "2026-03-10T11:00:00",
  ...changes,
});

const observation = (
  changes: Partial<ObservationSummary>,
): ObservationSummary => ({
  observationId: 1,
  role: "OBSERVEE",
  status: "ACCEPTED",
  observeeName: "Olivia Observee",
  observerName: "Omar Observer",
  courseLabel: "CS 3345.001",
  semesterLabel: "Fall 2026",
  cycleId: cycle.cycleId,
  observationDate: null,
  startTime: null,
  submittedAt: null,
  ...changes,
});

function summarize(changes: Partial<SummaryInputs> = {}) {
  return evaluationSummary({
    deadline,
    deadlineError: "",
    records: [],
    cycle,
    observations: [],
    ...changes,
  });
}

const card = (cards: ReturnType<typeof summarize>, label: string) => {
  const found = cards.find((c) => c.label === label);
  if (!found) throw new Error(`Missing card ${label}`);
  return found;
};

test("the cards come in the dashboard's order, and say they are loading until their data arrives", () => {
  const cards = summarize({
    deadline: null,
    records: null,
    cycle: undefined,
    observations: null,
  });
  expect(cards.map((c) => c.label)).toEqual([
    "Evaluation Status",
    "Cycle",
    "Next Due",
    "Observations This Term",
  ]);
  expect(cards.map((c) => c.value)).toEqual(["—", "—", "—", "—"]);
});

test("status follows the professor's record for the current cycle", () => {
  // Due this semester, so a professor who has not signed up has not started.
  const status = (records: EvaluationRecord[], overdue = false) =>
    card(
      summarize({
        records,
        deadline: { ...deadline, overdue, nextDueDate: "2026-09-15" },
      }),
      "Evaluation Status",
    );

  expect(
    status([record({ cycleId: cycle.cycleId, status: "ACTIVE" })]),
  ).toEqual({
    label: "Evaluation Status",
    value: "In Progress",
    note: "Current evaluation cycle",
    tone: "highlight",
  });
  expect(
    status([record({ cycleId: cycle.cycleId, status: "COMPLETED" })]).value,
  ).toBe("Completed");
  // Older cycles do not count as the current one.
  expect(status([record({ cycleId: 99 })]).value).toBe("Not Started");
  expect(status([], true)).toMatchObject({ value: "Overdue", tone: "alert" });
  // Being in the current cycle outranks an old overdue flag.
  expect(
    status([record({ cycleId: cycle.cycleId, status: "ACTIVE" })], true).value,
  ).toBe("In Progress");
  // Due after this cycle's last deadline, so nothing is expected yet.
  expect(card(summarize(), "Evaluation Status")).toEqual({
    label: "Evaluation Status",
    value: "Not Due",
    note: "Next evaluation due Mar 2028",
  });
});

test("the cycle card reads the rank's interval from the policy", () => {
  expect(card(summarize(), "Cycle")).toMatchObject({
    value: "Every 2 Years",
    note: "Associate Professor schedule",
  });
});

test("next due shows the month, and how the date was reached", () => {
  expect(card(summarize(), "Next Due")).toEqual({
    label: "Next Due",
    value: "Mar 2028",
    note: "Last evaluated Mar 10, 2026",
  });
  expect(
    card(
      summarize({
        deadline: { ...deadline, overdue: true, nextDueDate: "2025-08-01" },
      }),
      "Next Due",
    ),
  ).toMatchObject({
    value: "Aug 2025",
    note: "Was due Aug 1, 2025",
    tone: "alert",
  });
});

test("a deadline that cannot load explains why on the cards that need it", () => {
  const cards = summarize({
    deadline: null,
    deadlineError: "Professor 21 has no rank",
  });
  for (const label of ["Cycle", "Next Due"])
    expect(card(cards, label)).toEqual({
      label,
      value: "Unavailable",
      note: "Professor 21 has no rank",
    });
  // The rest of the summary does not depend on it.
  expect(card(cards, "Evaluation Status").value).toBe("Not Started");
});

test("observations this term counts only the professor's own classes in the current cycle", () => {
  const cards = summarize({
    observations: [
      observation({ observationId: 1, status: "ACCEPTED" }),
      observation({ observationId: 2, status: "IN_PROGRESS" }),
      observation({ observationId: 3, status: "SUBMITTED" }),
      // Not counted: observed by the professor, another cycle.
      observation({ observationId: 4, role: "OBSERVER" }),
      observation({ observationId: 5, cycleId: 99, status: "COMPLETED" }),
    ],
  });
  expect(card(cards, "Observations This Term")).toEqual({
    label: "Observations This Term",
    value: "3",
    note: "0 completed • 1 pending sign-off • 2 not yet submitted",
  });
});
