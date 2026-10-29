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
import type {
  EvaluationDeadline,
  EvaluationRecord,
} from "../../api/evaluations";
import type { ObservationSummary } from "../../api/observations";
import {
  type FacultyState,
  observerChanged,
} from "../../api/observerEligibility";
import {
  facultyStateFor,
  OLIVIA,
  oliviaObservations,
} from "../../test/facultyObservationFixtures";
import { fakeBackend, resetBackend, signInAs } from "../../test/fakeBackend";

let state: FacultyState;
let list: ObservationSummary[];
let failures: Response[];
let feedbackStatus: string;
let deadline: EvaluationDeadline;
let records: EvaluationRecord[];
let deadlineFailure: Response | null;
let recordsFailure: Response | null;

beforeEach(() => {
  state = facultyStateFor(OLIVIA);
  // The current cycle is the one Olivia's observations belong to.
  state.cycle = { ...state.cycle, cycleId: 2 };
  deadline = {
    professorId: OLIVIA.professorId,
    professorRank: "ASSOCIATE",
    evaluationIntervalYears: 2,
    lastEvaluationDate: "2026-03-10",
    nextDueDate: "2028-03-10",
    overdue: false,
  };
  records = [
    {
      cycleId: 2,
      term: "Fall",
      academicYear: 2026,
      status: "ACTIVE",
      observationCount: 4,
      lastObservationCompletedAt: null,
    },
    {
      cycleId: 1,
      term: "Spring",
      academicYear: 2026,
      status: "COMPLETED",
      observationCount: 1,
      lastObservationCompletedAt: "2026-03-10T11:00:00",
    },
    {
      cycleId: 0,
      term: "Fall",
      academicYear: 2024,
      status: "COMPLETED",
      observationCount: 2,
      lastObservationCompletedAt: "2024-11-12T09:30:00",
    },
  ];
  deadlineFailure = null;
  recordsFailure = null;
  list = oliviaObservations();
  failures = [];
  feedbackStatus = "AVAILABLE";
  fakeBackend({
    "/api/feedback-surveys": () =>
      Response.json([
        {
          cycleId: 1,
          label: "Fall 2026",
          observationDeadline: "2026-11-01",
          feedbackDeadline: "2026-11-10",
          status: feedbackStatus,
        },
      ]),
    [`/api/observer-eligibility/faculty/${OLIVIA.professorId}`]: () =>
      Response.json(state),
    [`/api/professors/${OLIVIA.professorId}/evaluation-deadline`]: () =>
      deadlineFailure ?? Response.json(deadline),
    [`/api/professors/${OLIVIA.professorId}/evaluations`]: () =>
      recordsFailure ?? Response.json(records),
    [`/api/professors/${OLIVIA.professorId}/observations`]: () =>
      failures.shift() ?? Response.json(list),
  });
  signInAs("professor", OLIVIA);
});

afterEach(() => {
  cleanup();
  resetBackend();
});

async function openDashboard() {
  window.history.pushState({}, "", "/faculty");
  render(<App />);
  await screen.findByRole("heading", {
    level: 1,
    name: "My Evaluation Dashboard",
  });
}

/** A dashboard card or panel, found by its heading. */
function panel(title: string) {
  const found = screen
    .getByRole("heading", { level: 3, name: title })
    .closest("div.rounded-xl");
  if (!found) throw new Error(`Missing panel ${title}`);
  return within(found as HTMLElement);
}

/** The text of each entry on a card: the course, the other professor, and the status and date. */
function entries(title: string) {
  return panel(title)
    .getAllByText(/^CS /)
    .map((course) =>
      [...(course.parentElement?.querySelectorAll("p") ?? [])].map(
        (p) => p.textContent,
      ),
    );
}

test("the observation cards show the newest two observations for and by the professor", async () => {
  await openDashboard();
  await panel("Observations for Me").findByText("CS 4348.501");

  expect(entries("Observations for Me")).toEqual([
    ["CS 4348.501", "Observer: Omar Observer", "Not started • Not scheduled"],
    ["CS 2305.001", "Observer: Omar Observer", "Not started • Nov 5, 2026"],
  ]);
  expect(entries("Observations for Others")).toEqual([
    ["CS 1337.002", "Observee: Nina Notstarted", "Not started • Not scheduled"],
    ["CS 2336.001", "Observee: Ian Inprogress", "In progress • Oct 27, 2026"],
  ]);
  expect(
    panel("Observations for Me")
      .getByRole("link", { name: "View All Records →" })
      .getAttribute("href"),
  ).toBe("/faculty/observations/me");
  expect(
    panel("Observations for Others")
      .getByRole("link", { name: "View All Records →" })
      .getAttribute("href"),
  ).toBe("/faculty/observations/others");
});

test("Upcoming lists the observations the professor has to start or continue, soonest first", async () => {
  await openDashboard();
  const upcoming = panel("Upcoming");
  await upcoming.findByText("Observation — CS 2336.001");

  const actions = upcoming.getAllByRole("link").map((link) => ({
    name: link.textContent,
    href: link.getAttribute("href"),
  }));
  expect(actions).toEqual([
    { name: "Continue (CS 2336.001)", href: "/faculty/observations/17" },
    {
      name: "Start Observation (CS 1337.002)",
      href: "/faculty/observations/16",
    },
    { name: "Start Survey", href: "/faculty/feedback?cycle=1" },
  ]);

  const resume = upcoming
    .getByText("Observation — CS 2336.001")
    .closest("div.border-t");
  if (!resume) throw new Error("Missing the observation to continue");
  expect(resume.textContent).toContain("OCT27");
  expect(resume.textContent).toContain("Observee: Ian Inprogress");
  expect(resume.textContent).toContain("In progress");
  expect(resume.textContent).toContain("Oct 27, 2026, 9:00 AM");
  expect(resume.innerHTML).not.toContain("bg-peach");

  const start = upcoming
    .getByText("Observation — CS 1337.002")
    .closest("div.border-t");
  if (!start) throw new Error("Missing the observation to start");
  expect(start.textContent).toContain("TBD");
  expect(start.textContent).toContain("Observee: Nina Notstarted");
  expect(start.textContent).toContain("Not scheduled");
  expect(start.innerHTML).not.toContain("bg-peach");

  // Nothing to do for observations of the professor's own classes, or for submitted and completed ones.
  for (const course of [
    "CS 3345.001",
    "CS 4348.501",
    "CS 2305.001",
    "CS 3354.001",
    "CS 3341.001",
    "CS 1336.001",
    "CS 3345.002",
  ])
    expect(upcoming.queryByText(`Observation — ${course}`)).toBeNull();
});

test("a professor with no observations sees empty cards", async () => {
  list = [];
  await openDashboard();
  await waitFor(() =>
    expect(screen.getAllByText("No observations yet.")).toHaveLength(2),
  );
  expect(
    panel("Upcoming")
      .getAllByRole("link")
      .map((link) => link.textContent),
  ).toEqual(["Start Survey"]);
});

test("shows the API's reason when observations can't load, and Retry tries again", async () => {
  failures.push(
    Response.json({ detail: "Observations are unavailable." }, { status: 503 }),
  );
  await openDashboard();
  const alert = await panel("Upcoming").findByRole("alert");
  expect(alert.textContent).toContain("Observations are unavailable.");
  expect(screen.getAllByText("Couldn't load observations.")).toHaveLength(2);

  fireEvent.click(within(alert).getByRole("button", { name: "Retry" }));
  await panel("Upcoming").findByText("Observation — CS 2336.001");
  expect(screen.queryByRole("alert")).toBeNull();
  expect(screen.queryByText("Couldn't load observations.")).toBeNull();
});

test("the observations reload when a dialog changes them", async () => {
  await openDashboard();
  await panel("Upcoming").findByRole("link", {
    name: "Start Observation (CS 1337.002)",
  });

  // Nina's observation was started in the dialog above the dashboard.
  list = list.map(
    (o): ObservationSummary =>
      o.observationId === 16 ? { ...o, status: "IN_PROGRESS" } : o,
  );
  act(() => observerChanged());

  await panel("Upcoming").findByRole("link", {
    name: "Continue (CS 1337.002)",
  });
});

test("submitting survey replaces Start Survey with View Survey", async () => {
  await openDashboard();
  await panel("Upcoming").findByRole("link", { name: "Start Survey" });
  feedbackStatus = "SUBMITTED";
  act(() => observerChanged());
  await waitFor(() =>
    expect(
      panel("Upcoming").queryByRole("link", { name: "Start Survey" }),
    ).toBeNull(),
  );
  expect(
    panel("Upcoming")
      .getByRole("link", { name: "View Survey" })
      .getAttribute("href"),
  ).toBe("/faculty/feedback?cycle=1");
  expect(
    panel("Upcoming").getByText("Process Feedback Submitted"),
  ).toBeTruthy();
});

test("upcoming survey is visible but disabled until the observation deadline passes", async () => {
  feedbackStatus = "NOT_OPEN";
  await openDashboard();
  const upcoming = panel("Upcoming");
  const button = await upcoming.findByRole("button", { name: "Start Survey" });
  expect((button as HTMLButtonElement).disabled).toBe(true);
  expect(upcoming.getByText("Process Feedback Due")).toBeTruthy();
  expect(
    upcoming.queryByText(/Available after the observation deadline/),
  ).toBeNull();
  expect(upcoming.queryByRole("link", { name: "Start Survey" })).toBeNull();

  feedbackStatus = "AVAILABLE";
  fireEvent.focus(window);
  await upcoming.findByRole("link", { name: "Start Survey" });
  expect(upcoming.queryByRole("button", { name: "Start Survey" })).toBeNull();
});

test.each(["CLOSED", "UNCONFIGURED"])(
  "%s survey has no dashboard task",
  async (status) => {
    feedbackStatus = status;
    await openDashboard();
    await panel("Upcoming").findByRole("link", {
      name: "Continue (CS 2336.001)",
    });
    expect(
      panel("Upcoming").queryByRole("link", { name: "Start Survey" }),
    ).toBeNull();
  },
);

test("a new notification reloads the observations", async () => {
  await openDashboard();
  await panel("Observations for Me").findByText("CS 4348.501");
  expect(entries("Observations for Me")[0]).toEqual([
    "CS 4348.501",
    "Observer: Omar Observer",
    "Not started • Not scheduled",
  ]);

  // Omar's submission of an observation of Olivia's class arrives with a notification.
  list = list.map(
    (o): ObservationSummary =>
      o.observationId === 12
        ? {
            ...o,
            status: "SUBMITTED",
            observationDate: "2026-10-27",
            startTime: "10:00:00",
            submittedAt: "2026-10-27T11:00:00",
          }
        : o,
  );
  state.notifications = [
    {
      notificationId: 1,
      message:
        "Omar Observer submitted the observation of CS 4348.501. You can view it now.",
      createdAt: "2026-10-27T11:00:00-05:00",
    },
  ];
  // The status reloads when the window regains focus, as well as every few seconds.
  fireEvent.focus(window);

  await waitFor(() =>
    expect(entries("Observations for Me")[0]).toEqual([
      "CS 4348.501",
      "Observer: Omar Observer",
      "Submitted • Oct 27, 2026",
    ]),
  );
});

/** One card of the evaluation summary, found by its label. */
function summaryCard(label: string) {
  const found = screen.getByText(label).closest("div.rounded-xl");
  if (!found) throw new Error(`Missing summary card ${label}`);
  return [...found.querySelectorAll("p")].map((p) => p.textContent);
}

test("the header shows the professor's initials, rank and department", async () => {
  await openDashboard();
  const rankLine = await screen.findByText(
    "Associate Professor • Computer Science",
  );
  expect(rankLine.previousElementSibling?.textContent).toBe("Olivia Observee");
  expect(screen.getByText("OO")).toBeTruthy();
  expect(screen.queryByText("P1")).toBeNull();
});

test("the evaluation summary comes from the professor's own deadline, records and observations", async () => {
  await openDashboard();
  await screen.findByText("Every 2 Years");

  expect(summaryCard("Evaluation Status")).toEqual([
    "Evaluation Status",
    "In Progress",
    "Current evaluation cycle",
  ]);
  expect(summaryCard("Cycle")).toEqual([
    "Cycle",
    "Every 2 Years",
    "Associate Professor schedule",
  ]);
  expect(summaryCard("Next Due")).toEqual([
    "Next Due",
    "Mar 2028",
    "Last evaluated Mar 10, 2026",
  ]);
  expect(summaryCard("Observations This Term")).toEqual([
    "Observations This Term",
    "4",
    "1 submitted • 3 not yet submitted",
  ]);
});

test("an overdue professor sees it on the status and due date", async () => {
  records = records.filter((r) => r.cycleId !== 2);
  deadline = { ...deadline, overdue: true, nextDueDate: "2025-08-01" };
  await openDashboard();
  await screen.findByText("Overdue");
  expect(summaryCard("Next Due")).toEqual([
    "Next Due",
    "Aug 2025",
    "Was due Aug 1, 2025",
  ]);
});

test("a professor with no rank still sees the rest of the dashboard", async () => {
  deadlineFailure = Response.json(
    { detail: "Professor 21 has no rank" },
    { status: 404 },
  );
  await openDashboard();
  await screen.findAllByText("Unavailable");
  expect(screen.getAllByText("Professor 21 has no rank")).toHaveLength(2);
  expect(screen.getByText("Computer Science")).toBeTruthy();
  await panel("Upcoming").findByText("Observation — CS 2336.001");
});

test("Evaluation History lists the previous cycles, not the one in progress", async () => {
  await openDashboard();
  await panel("Evaluation History").findByText("Spring 2026");
  const history = panel("Evaluation History");

  expect(history.getByText("Completed • 1 observation")).toBeTruthy();
  expect(history.getByText("Fall 2024")).toBeTruthy();
  expect(history.getByText("Completed • 2 observations")).toBeTruthy();
  expect(history.queryByText("Fall 2026")).toBeNull();
  expect(
    history.getByRole("link", { name: "View All →" }).getAttribute("href"),
  ).toBe("/faculty/history");
  expect(
    history
      .getByRole("link", { name: "View Evaluation (Spring 2026)" })
      .getAttribute("href"),
  ).toBe("/faculty/history?cycle=1");
});

test("Evaluation History is empty for a professor with only the current cycle", async () => {
  records = records.filter((r) => r.cycleId === 2);
  await openDashboard();
  await panel("Evaluation History").findByText("No previous evaluations yet.");
});

test("Evaluation History says when it can't load", async () => {
  recordsFailure = Response.json(
    { detail: "Records are unavailable." },
    { status: 503 },
  );
  await openDashboard();
  await panel("Evaluation History").findByText(
    "Couldn't load evaluation history.",
  );
});

test("the summary updates when a dialog changes the evaluation", async () => {
  await openDashboard();
  await screen.findByText("In Progress");

  records = records.map((r) =>
    r.cycleId === 2
      ? {
          ...r,
          status: "COMPLETED",
          lastObservationCompletedAt: "2026-10-27T11:00:00",
        }
      : r,
  );
  deadline = {
    ...deadline,
    lastEvaluationDate: "2026-10-27",
    nextDueDate: "2028-10-27",
  };
  act(() => observerChanged());

  await screen.findByText("Completed");
  expect(summaryCard("Next Due")[1]).toBe("Oct 2028");
  // The finished cycle is history now.
  await panel("Evaluation History").findByText("Fall 2026");
});

test("a professor not due until after this cycle sees Not Due", async () => {
  records = records.filter((r) => r.cycleId !== 2);
  await openDashboard();
  await screen.findByText("Not Due");
  expect(screen.getByText("Next evaluation due Mar 2028")).toBeTruthy();
});
