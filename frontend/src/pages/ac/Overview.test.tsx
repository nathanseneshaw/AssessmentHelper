import { afterEach, beforeEach, expect, test } from "bun:test";
import {
  cleanup,
  render,
  screen,
  waitFor,
  within,
} from "@testing-library/react";
import { App } from "../../App";
import type { OverdueQueue } from "../../api/evaluationEligibility";
import { metrics } from "../../test/acDashboardFixtures";
import { fakeBackend, resetBackend, signInAs } from "../../test/fakeBackend";
import { ac } from "../../test/observerFixtures";

const METRIC_LABELS = [
  "Faculty Due This Cycle",
  "Participation Rate",
  "Observer Utilization",
  "List Sufficiency Rate",
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

beforeEach(() => {
  answerMetrics = () => Response.json(metrics);
  fakeBackend({
    "/api/due-evaluations": () => Response.json(queue),
    "/api/overdue-evaluations": () => Response.json(queue),
    "/api/observer-eligibility/ac": () => Response.json(ac),
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
  expect(kpi("Missing Observations")).toEqual({
    value: "3",
    note: "Approved observations past their date and not completed",
  });
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
  expect(kpi("Survey Completion Rate")).toEqual({
    value: "78%",
    note: "Process feedback received",
  });
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
