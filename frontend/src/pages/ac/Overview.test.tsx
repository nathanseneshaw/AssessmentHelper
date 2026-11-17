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

// Reads the value shown on the KPI card with this label.
function kpi(label: string) {
  const card = screen.getByText(label, { selector: "p" }).parentElement;
  if (!card) throw new Error(`Missing ${label} card`);
  return card.querySelectorAll("p")[1]?.textContent;
}

test("cards show live metrics and the overdue count when the dashboard loads", async () => {
  await openOverview();
  await waitFor(() => expect(kpi("Faculty Due This Cycle")).toBe("13"));
  expect(kpi("Participation Rate")).toBe("15%");
  expect(kpi("Observer Utilization")).toBe("17%");
  expect(kpi("List Sufficiency Rate")).toBe("33%");
  expect(kpi("Survey Completion Rate")).toBe("44%");
  expect(kpi("Missing Observations")).toBe("3");
  await waitFor(() => expect(kpi("Overdue Evaluations")).toBe("1"));
});

test("lists every unmatched faculty member and opens the chosen case when Resolve Matches is clicked", async () => {
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
  await waitFor(() => expect(kpi("Unmatched Faculty")).toBe("6"));
  fireEvent.click(screen.getByRole("button", { name: "Resolve Matches" }));
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
});

test("survey completion explains an empty rate before the survey opens", async () => {
  answerMetrics = () =>
    Response.json({
      ...metrics,
      surveyCompletion: { count: 0, total: 8 },
      surveyStatus: "NOT_OPEN",
    });
  await openOverview();
  await waitFor(() => expect(kpi("Survey Completion Rate")).toBe("—"));
  expect(
    screen.getByText("Survey opens after the observation deadline"),
  ).toBeDefined();
});

test("saving new deadlines refreshes the metrics and overdue evaluations", async () => {
  matchingState.cycle.feedbackDeadline = "2026-12-01";
  await openOverview();
  await waitFor(() => expect(kpi("Participation Rate")).toBe("15%"));
  await waitFor(() => expect(kpi("Overdue Evaluations")).toBe("1"));

  answerMetrics = () =>
    Response.json({ ...metrics, participation: { count: 3, total: 13 } });
  overdueQueue = { ...queue, count: 0, faculty: [] };
  fireEvent.click(
    await screen.findByRole("button", { name: "Edit Deadlines" }),
  );
  fireEvent.click(screen.getByRole("button", { name: "Save Deadlines" }));
  await screen.findByText("Cycle deadlines updated.");
  await waitFor(() => expect(kpi("Participation Rate")).toBe("23%"));
  await waitFor(() => expect(kpi("Overdue Evaluations")).toBe("0"));
});

test("coming back to the window refreshes the metrics and due list, keeping the old figures until then", async () => {
  await openOverview();
  await waitFor(() => expect(kpi("Faculty Due This Cycle")).toBe("13"));
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
  expect(kpi("Faculty Due This Cycle")).toBe("13");

  held[0]?.(Response.json({ ...metrics, facultyDue: 14 }));
  await waitFor(() => expect(kpi("Faculty Due This Cycle")).toBe("14"));
});

test("only the metrics cards show Unavailable when the metrics request fails", async () => {
  answerMetrics = () =>
    Response.json({ detail: "Metrics unavailable" }, { status: 503 });
  await openOverview();
  await waitFor(() => expect(kpi("Participation Rate")).toBe("Unavailable"));
  expect(kpi("Faculty Due This Cycle")).toBe("Unavailable");
  await waitFor(() => expect(kpi("Overdue Evaluations")).toBe("1"));
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
