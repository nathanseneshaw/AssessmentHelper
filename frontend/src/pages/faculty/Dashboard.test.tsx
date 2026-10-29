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
import type { EvaluationDeadline } from "../../api/evaluations";
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
import { signup } from "../../test/observerFixtures";

let state: FacultyState;
let list: ObservationSummary[];
let failures: Response[];
let deadline: EvaluationDeadline;

beforeEach(() => {
  state = facultyStateFor(OLIVIA);
  list = oliviaObservations();
  failures = [];
  deadline = {
    professorId: OLIVIA.professorId,
    professorRank: "ASSOCIATE",
    evaluationIntervalYears: 2,
    lastEvaluationDate: "2025-03-10",
    nextDueDate: "2027-03-10",
    overdue: false,
  };
  fakeBackend({
    [`/api/observer-eligibility/faculty/${OLIVIA.professorId}`]: () =>
      Response.json(state),
    [`/api/professors/${OLIVIA.professorId}/observations`]: () =>
      failures.shift() ?? Response.json(list),
    [`/api/professors/${OLIVIA.professorId}/evaluation-deadline`]: () =>
      Response.json(deadline),
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
    // Still a mockup until issue #108.
    { name: "Start Survey", href: "/faculty/feedback" },
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

function summaryCard(label: string) {
  const card = screen.getByText(label).parentElement;
  return [...(card?.querySelectorAll("p") ?? [])].map((p) => p.textContent);
}

test("the summary cards show the professor's schedule and next due date", async () => {
  await openDashboard();
  await screen.findByText("Every 2 Years");

  expect(summaryCard("Evaluation Status")).toEqual([
    "Evaluation Status",
    "Up to Date",
    "Nothing due in Fall 2026",
  ]);
  expect(summaryCard("Cycle")).toEqual([
    "Cycle",
    "Every 2 Years",
    "Associate professor schedule",
  ]);
  expect(summaryCard("Next Due")).toEqual([
    "Next Due",
    "Mar 2027",
    "Last evaluated Mar 10, 2025",
  ]);
  expect(
    screen.getByText("Associate Professor • Computer Science"),
  ).toBeTruthy();
  expect(screen.getByText("OO")).toBeTruthy();
});

test("the evaluation status follows the deadline and this cycle's sign-up", async () => {
  const cases: [
    Partial<EvaluationDeadline>,
    FacultyState["signup"],
    string[],
  ][] = [
    [
      { nextDueDate: "2026-05-01", overdue: true },
      null,
      ["Overdue", "Was due May 2026"],
    ],
    [
      { nextDueDate: "2026-11-01" },
      null,
      ["Due This Cycle", "Sign up by Oct 31, 2026"],
    ],
    [
      { nextDueDate: "2026-11-01" },
      signup,
      ["In Progress", "Signed up for Fall 2026"],
    ],
    [
      { lastEvaluationDate: "2026-10-20", nextDueDate: "2028-10-20" },
      signup,
      ["Completed", "Fall 2026 evaluation"],
    ],
  ];
  const base = deadline;
  for (const [changes, currentSignup, expected] of cases) {
    deadline = { ...base, ...changes };
    state.signup = currentSignup;
    await openDashboard();
    await waitFor(() =>
      expect(summaryCard("Evaluation Status").slice(1)).toEqual(expected),
    );
    cleanup();
  }
});
