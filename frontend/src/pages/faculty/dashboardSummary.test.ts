import { describe, expect, test } from "bun:test";
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

test("the cards come in the order the dashboard shows them", () => {
  expect(summarize().map((c) => c.label)).toEqual([
    "Evaluation Status",
    "Cycle",
    "Next Due",
    "Observations This Term",
  ]);
});

test("every card says it is loading until its data arrives", () => {
  const cards = summarize({
    deadline: null,
    records: null,
    cycle: undefined,
    observations: null,
  });
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
});

test("the cycle card reads the rank's interval from the policy", () => {
  expect(card(summarize(), "Cycle")).toMatchObject({
    value: "Every 2 Years",
    note: "Associate Professor schedule",
  });
  expect(
    card(
      summarize({
        deadline: {
          ...deadline,
          professorRank: "ASSISTANT",
          evaluationIntervalYears: 1,
        },
      }),
      "Cycle",
    ),
  ).toMatchObject({
    value: "Every Year",
    note: "Assistant Professor schedule",
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
      summarize({ deadline: { ...deadline, lastEvaluationDate: null } }),
      "Next Due",
    ).note,
  ).toBe("No completed evaluation yet");
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
  expect(
    card(
      summarize({ deadline: { ...deadline, nextDueDate: null } }),
      "Next Due",
    ).value,
  ).toBe("Not set");
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
    note: "1 submitted • 2 not yet submitted",
  });
  expect(card(summarize(), "Observations This Term")).toMatchObject({
    value: "0",
    note: "None of your classes observed yet",
  });
});

test("a professor whose evaluation is due after this cycle is not due yet", () => {
  // The seeded cycle's last deadline is Nov 15, 2026.
  expect(card(summarize({ records: [] }), "Evaluation Status")).toEqual({
    label: "Evaluation Status",
    value: "Not Due",
    note: "Next evaluation due Mar 2028",
  });
  // Due on the last deadline itself still counts as this cycle.
  expect(
    card(
      summarize({
        records: [],
        deadline: { ...deadline, nextDueDate: "2026-11-15" },
      }),
      "Evaluation Status",
    ).value,
  ).toBe("Not Started");
});

// What the seeded demo logins should see, worked out by hand from data.sql and the due-date rules in
// EvaluationDueRules. Today is in Fall 2026, the seeded current cycle (observations due Nov 15, 2026).
describe("seeded demo accounts", () => {
  const seededCycle = {
    ...cycle,
    cycleId: 1,
    label: "Fall 2026",
    observationDeadline: "2026-11-15",
    feedbackDeadline: "2026-12-01",
  };
  const run = (
    deadlineChanges: Partial<EvaluationDeadline>,
    records: EvaluationRecord[],
  ) =>
    evaluationSummary({
      deadline: { ...deadline, ...deadlineChanges },
      deadlineError: "",
      records,
      cycle: seededCycle,
      observations: [],
    });
  const inFall = record({
    cycleId: 1,
    term: "Fall",
    academicYear: 2026,
    status: "ACTIVE",
  });

  test("Dario: assistant professor hired Sep 2025, not signed up yet, due this semester", () => {
    const cards = run(
      {
        professorRank: "ASSISTANT",
        evaluationIntervalYears: 1,
        lastEvaluationDate: null,
        nextDueDate: "2026-09-15",
      },
      [],
    );
    expect(cards[0]).toMatchObject({ value: "Not Started" });
    expect(cards[1]).toMatchObject({ value: "Every Year" });
    expect(cards[2]).toEqual({
      label: "Next Due",
      value: "Sep 2026",
      note: "No completed evaluation yet",
    });
  });

  test("Leander: hired Jan 2025 and never evaluated, so overdue, but already in this cycle", () => {
    const cards = run(
      {
        professorRank: "ASSISTANT",
        evaluationIntervalYears: 1,
        lastEvaluationDate: null,
        nextDueDate: "2026-01-27",
        overdue: true,
      },
      [inFall],
    );
    expect(cards[0]).toMatchObject({ value: "In Progress", tone: "highlight" });
    expect(cards[2]).toMatchObject({
      value: "Jan 2026",
      note: "Was due Jan 27, 2026",
      tone: "alert",
    });
  });

  test("Keira: evaluated Oct 2025, due Oct 2026, in this cycle", () => {
    const cards = run(
      {
        professorRank: "ASSISTANT",
        evaluationIntervalYears: 1,
        lastEvaluationDate: "2025-10-28",
        nextDueDate: "2026-10-28",
      },
      [inFall, record({ cycleId: 0, term: "Fall", academicYear: 2025 })],
    );
    expect(cards[0]).toMatchObject({ value: "In Progress" });
    expect(cards[2]).toEqual({
      label: "Next Due",
      value: "Oct 2026",
      note: "Last evaluated Oct 28, 2025",
    });
  });
});
