import { afterEach, beforeEach, expect, test } from "bun:test";
import {
  act,
  cleanup,
  fireEvent,
  render,
  screen,
  waitFor,
  within,
} from "@testing-library/react";
import { App } from "../../App";
import type { MissingSignups } from "../../api/acDashboard";
import type { OverdueQueue } from "../../api/evaluationEligibility";
import type { AcState } from "../../api/observerEligibility";
import { metrics } from "../../test/acDashboardFixtures";
import { fakeBackend, resetBackend, signInAs } from "../../test/fakeBackend";
import { ac } from "../../test/observerFixtures";

const METRIC_LABELS = [
  "Faculty Due This Cycle",
  "Participation Rate",
  "Observer Utilization",
  "List Sufficiency Rate",
  "Survey Completion Rate",
  "Missing Observations",
];

const queue: OverdueQueue = {
  cycleId: 1,
  cycleLabel: "Fall 2026",
  count: 1,
  faculty: [
    {
      professorId: 3,
      name: "Ollie Overdue",
      lastName: "Overdue",
      rank: "ASSOCIATE",
      email: "ollie.overdue@test.edu",
      lastEvaluationCycle: "Spring 2023",
      nextDueDate: "2025-03-01",
      intervalYears: 2,
      lastNotifiedAt: null,
    },
  ],
};

let answerMetrics: () => Response | Promise<Response>;
let matchingState: AcState;
let dueQueue: OverdueQueue;
let overdueQueue: OverdueQueue;
let missingSignups: MissingSignups;

beforeEach(() => {
  answerMetrics = () => Response.json(metrics);
  matchingState = structuredClone(ac);
  dueQueue = queue;
  overdueQueue = queue;
  missingSignups = {
    cycleId: 1,
    cycleLabel: "Fall 2026",
    observationDeadline: "2026-11-15",
    signups: [],
  };
  fakeBackend({
    "/api/missing-observations": () => Response.json(missingSignups),
    "/api/due-evaluations": () => Response.json(dueQueue),
    "/api/overdue-evaluations": () => Response.json(overdueQueue),
    "/api/observer-eligibility/ac": () => Response.json(matchingState),
    "/api/observer-eligibility/cycles/1/deadlines": () =>
      Response.json(matchingState.cycle),
    "/api/ac-dashboard/metrics": () => answerMetrics(),
  });
  signInAs("ac");
});

afterEach(() => {
  cleanup();
  resetBackend();
});

async function openOverview() {
  window.history.pushState({}, "", "/ac");
  render(<App />);
  await screen.findByRole("heading", {
    level: 1,
    name: "Assessment Committee Dashboard",
  });
}

// Reads the value and note shown on the KPI card with this label.
function kpi(label: string) {
  const card = screen.getByText(label, { selector: "p" }).parentElement;
  if (!card) throw new Error(`Missing ${label} card`);
  const [, value, note] = card.querySelectorAll("p");
  return { value: value?.textContent, note: note?.textContent };
}

test("unmatched faculty includes failed invitations and links to their matching case", async () => {
  const first = matchingState.matchingCases[0];
  if (!first) throw new Error("Missing matching case");
  first.status = "UNMATCHED";
  first.reason = "INVITATIONS_UNSUCCESSFUL";
  await openOverview();
  await waitFor(() => expect(kpi("Unmatched Faculty").value).toBe("2"));
  expect(
    screen
      .getByRole("link", { name: "Resolve Professor 1" })
      .getAttribute("href"),
  ).toBe("/ac/matching?signup=1");
});

test("Resolve Matches lists every unmatched faculty member and opens the selected case directly", async () => {
  const signup = matchingState.signups[1];
  const matching = matchingState.matchingCases[1];
  if (!signup || !matching) throw new Error("Missing fixture");
  matchingState.signups = Array.from({ length: 6 }, (_, i) => ({
    ...signup,
    signupId: i + 1,
    professorId: i + 1,
    name: `Observee ${i + 1}`,
  }));
  matchingState.matchingCases = matchingState.signups.map((s) => ({
    ...matching,
    signupId: s.signupId,
  }));
  await openOverview();
  const button = screen.getByRole("button", { name: "Resolve Matches" });
  await waitFor(() => expect(kpi("Unmatched Faculty").value).toBe("6"));
  expect(
    screen.getAllByRole("link", { name: /^Resolve Observee/ }),
  ).toHaveLength(4);
  fireEvent.click(button);
  const dialog = await screen.findByRole("dialog", {
    name: "Unmatched Faculty",
  });
  expect(
    within(dialog).getAllByRole("link", { name: /^Resolve Observee/ }),
  ).toHaveLength(6);
  fireEvent.click(
    within(dialog).getByRole("link", { name: "Resolve Observee 6" }),
  );
  await screen.findByRole("heading", { name: "Observee: Observee 6" });
  expect(window.location.pathname + window.location.search).toBe(
    "/ac/matching?signup=6",
  );
  expect(screen.queryByRole("dialog")).toBeNull();
});

test("the unmatched overlay closes without navigating away from the overview", async () => {
  await openOverview();
  const button = screen.getByRole("button", { name: "Resolve Matches" });
  button.focus();
  fireEvent.click(button);
  const dialog = await screen.findByRole("dialog", {
    name: "Unmatched Faculty",
  });
  fireEvent.click(within(dialog).getByRole("button", { name: "Close" }));
  expect(screen.queryByRole("dialog")).toBeNull();
  expect(window.location.pathname).toBe("/ac");
  expect(document.activeElement).toBe(button);
});

test("confirmed and pending cases are excluded even if their stored candidate lists are empty", async () => {
  matchingState.signups.forEach((s) => {
    s.candidates = [];
  });
  matchingState.matchingCases.forEach((c, i) => {
    c.status = i === 0 ? "MATCHED" : "AWAITING_RESPONSE";
    c.reason = null;
  });
  await openOverview();
  await waitFor(() => expect(kpi("Unmatched Faculty").value).toBe("0"));
  expect(screen.getByText("No unmatched faculty.")).toBeTruthy();
});

test("metric cards show live values, with rates rounded to whole percents", async () => {
  await openOverview();
  await waitFor(() => expect(kpi("Faculty Due This Cycle").value).toBe("13"));
  expect(kpi("Faculty Due This Cycle").note).toBe(
    "Active faculty due for evaluation",
  );
  expect(kpi("Participation Rate")).toEqual({
    value: "15%",
    note: "2 of 13 due faculty signed up",
  });
  expect(kpi("Observer Utilization")).toEqual({
    value: "17%",
    note: "1 of 6 signups have an observer",
  });
  expect(kpi("List Sufficiency Rate")).toEqual({
    value: "33%",
    note: "2 of 6 signups have a full list of 5",
  });
  expect(kpi("Survey Completion Rate")).toEqual({
    value: "44%",
    note: "4 of 9 observees submitted feedback",
  });
  expect(kpi("Missing Observations")).toEqual({
    value: "3",
    note: "Past the observation deadline without final sign-off",
  });
});

test("survey completion explains an empty rate before the survey opens", async () => {
  answerMetrics = () =>
    Response.json({
      ...metrics,
      surveyCompletion: { count: 0, total: 8 },
      surveyStatus: "NOT_OPEN",
    });
  await openOverview();
  await waitFor(() =>
    expect(kpi("Survey Completion Rate")).toEqual({
      value: "—",
      note: "Survey opens after the observation deadline",
    }),
  );
});

test("saving new deadlines refreshes the metrics and overdue evaluations", async () => {
  matchingState.cycle.feedbackDeadline = "2026-12-01";
  await openOverview();
  await waitFor(() => expect(kpi("Participation Rate").value).toBe("15%"));
  await waitFor(() => expect(kpi("Overdue Evaluations").value).toBe("1"));

  answerMetrics = () =>
    Response.json({ ...metrics, participation: { count: 3, total: 13 } });
  overdueQueue = { ...queue, count: 0, faculty: [] };
  fireEvent.click(
    await screen.findByRole("button", { name: "Edit Deadlines" }),
  );
  fireEvent.click(screen.getByRole("button", { name: "Save Deadlines" }));
  await screen.findByText("Cycle deadlines updated.");
  await waitFor(() =>
    expect(kpi("Participation Rate")).toEqual({
      value: "23%",
      note: "3 of 13 due faculty signed up",
    }),
  );
  await waitFor(() => expect(kpi("Overdue Evaluations").value).toBe("0"));
});

test("coming back to the window refreshes the metrics and due list, keeping the old figures until then", async () => {
  await openOverview();
  await waitFor(() => expect(kpi("Faculty Due This Cycle").value).toBe("13"));
  await screen.findByText("Ollie Overdue");

  const held: ((response: Response) => void)[] = [];
  answerMetrics = () =>
    new Promise<Response>((resolve) => {
      held.push(resolve);
    });
  const [ollie] = queue.faculty;
  if (!ollie) throw new Error("Missing due faculty fixture");
  dueQueue = {
    ...queue,
    count: 2,
    faculty: [
      ollie,
      { ...ollie, professorId: 4, name: "Nora Newlydue", lastName: "Newlydue" },
    ],
  };
  act(() => {
    window.dispatchEvent(new Event("focus"));
  });
  await screen.findByText("Nora Newlydue");
  await waitFor(() => expect(held).toHaveLength(1));
  expect(kpi("Faculty Due This Cycle").value).toBe("13");

  held[0]?.(Response.json({ ...metrics, facultyDue: 14 }));
  await waitFor(() => expect(kpi("Faculty Due This Cycle").value).toBe("14"));
});

test("Faculty Due This Cycle takes the place of Eligibility Accuracy", async () => {
  await openOverview();
  const group = screen
    .getByRole("heading", { name: "Faculty evaluation" })
    .closest("section");
  if (!group) throw new Error("Missing faculty evaluation group");
  expect(within(group).getByText("Faculty Due This Cycle")).toBeDefined();
  expect(within(group).getByText("Overdue Evaluations")).toBeDefined();
  expect(screen.queryByText("Evaluation Eligibility Accuracy")).toBeNull();
});

test("cards outside the metrics keep their own data", async () => {
  await openOverview();
  await waitFor(() => expect(kpi("Overdue Evaluations").value).toBe("1"));
  expect(kpi("Overdue Evaluations").note).toBe("Requires committee attention");
  await waitFor(() => expect(kpi("Unmatched Faculty").value).toBe("1"));
});

test("a rate with nothing to count shows a dash", async () => {
  const none = { count: 0, total: 0 };
  answerMetrics = () =>
    Response.json({
      ...metrics,
      facultyDue: 0,
      participation: none,
      observerUtilization: none,
      listSufficiency: none,
      missingObservations: 0,
      surveyCompletion: none,
    });
  await openOverview();
  await waitFor(() => expect(kpi("Faculty Due This Cycle").value).toBe("0"));
  expect(kpi("Participation Rate")).toEqual({
    value: "—",
    note: "No faculty are due this cycle",
  });
  expect(kpi("Observer Utilization")).toEqual({
    value: "—",
    note: "No signups this cycle",
  });
  expect(kpi("List Sufficiency Rate")).toEqual({
    value: "—",
    note: "No signups this cycle",
  });
  expect(kpi("Survey Completion Rate")).toEqual({
    value: "—",
    note: "No observees to survey this cycle",
  });
  expect(kpi("Missing Observations").value).toBe("0");
  expect(document.body.textContent).not.toMatch(/NaN|Infinity/);
});

test("metric cards show a dash while the metrics load", async () => {
  answerMetrics = () => new Promise<Response>(() => {});
  await openOverview();
  for (const label of METRIC_LABELS) {
    expect(kpi(label)).toEqual({ value: "—", note: "Loading…" });
  }
});

test("a failed metrics request marks its cards unavailable without an error message", async () => {
  answerMetrics = () =>
    Response.json({ detail: "Metrics unavailable" }, { status: 503 });
  await openOverview();
  await waitFor(() =>
    expect(kpi("Participation Rate").value).toBe("Unavailable"),
  );
  for (const label of METRIC_LABELS) {
    expect(kpi(label)).toEqual({
      value: "Unavailable",
      note: "Metrics could not be loaded",
    });
  }
  expect(screen.queryByText(/Metrics unavailable/)).toBeNull();
  expect(screen.queryByRole("button", { name: /^Retry/ })).toBeNull();
  await waitFor(() => expect(kpi("Overdue Evaluations").value).toBe("1"));
});

const missingRow = (id: number, observer: string | null = null) => ({
  signupId: id,
  observeeId: id,
  observee: `Observee ${id}`,
  course: `CS 3345.00${id}`,
  observer,
  observationStatus: observer ? "SCHEDULED" : null,
});

test("missing observations panel says so when nobody is past the deadline", async () => {
  await openOverview();
  await screen.findByText("No missing observations.");
});

test("missing observations panel lists signups and says when no observer accepted", async () => {
  missingSignups.signups = [missingRow(1), missingRow(2, "Keira Larkspur")];
  await openOverview();
  await screen.findByText("2 signup(s) have no completed observation.");
  expect(screen.getByText("Observee 1")).toBeTruthy();
  expect(screen.getByText("CS 3345.001")).toBeTruthy();
  expect(screen.getByText("No observer accepted")).toBeTruthy();
  expect(screen.getByText("Keira Larkspur, scheduled")).toBeTruthy();
  expect(
    screen
      .getByRole("link", { name: "View Observations" })
      .getAttribute("href"),
  ).toBe("/ac/observations");
});

test("missing observations panel shows five rows and counts the rest", async () => {
  missingSignups.signups = Array.from({ length: 8 }, (_, i) =>
    missingRow(i + 1),
  );
  await openOverview();
  await screen.findByText("8 signup(s) have no completed observation.");
  expect(screen.getAllByText("No observer accepted")).toHaveLength(5);
  expect(screen.getByText("and 3 more")).toBeTruthy();
});
