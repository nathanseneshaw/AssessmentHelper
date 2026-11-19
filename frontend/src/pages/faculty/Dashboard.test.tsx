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
import type { FacultyDashboard } from "../../api/facultyDashboard";
import type { FeedbackSurvey } from "../../api/feedback";
import {
  type FacultyState,
  observerChanged,
} from "../../api/observerEligibility";
import { dashboard } from "../../test/facultyDashboardFixtures";
import { fakeBackend, resetBackend, signInAs } from "../../test/fakeBackend";
import { faculty } from "../../test/observerFixtures";

/** Wren's Fall 2026 process feedback survey, open after the observation deadline. */
const fallSurvey: FeedbackSurvey = {
  cycleId: 1,
  label: "Fall 2026",
  observationDeadline: "2026-11-20",
  feedbackDeadline: "2026-12-01",
  status: "AVAILABLE",
};

let state: FacultyState;
let current: FacultyDashboard;
let surveys: FeedbackSurvey[];
/** A one-shot failure for the next dashboard request. */
let failure: Response | undefined;

beforeEach(() => {
  state = structuredClone(faculty);
  current = structuredClone(dashboard);
  surveys = [fallSurvey];
  failure = undefined;
  fakeBackend({
    "/api/feedback-surveys": () => Response.json(surveys),
    "/api/observer-eligibility/faculty/1": () => Response.json(state),
    "/api/professors/1/dashboard": () => {
      const response = failure ?? Response.json(current);
      failure = undefined;
      return response;
    },
  });
  signInAs("professor", { professorId: 1, name: "Professor 1" });
});

afterEach(() => {
  cleanup();
  resetBackend();
});

async function open() {
  window.history.pushState({}, "", "/faculty");
  render(<App />);
  await screen.findByText("Evaluation Status");
}

/** The summary card with this label. */
function card(label: string) {
  const heading = screen.getByText(label);
  const element = heading.parentElement;
  if (!element) throw new Error(`No card for ${label}`);
  return element.textContent;
}

/** The section card with this heading. */
function section(title: string) {
  const heading = screen.getByRole("heading", { name: title });
  const element = heading.closest("div.rounded-xl");
  if (!(element instanceof HTMLElement)) throw new Error(`No ${title}`);
  return element;
}

/** The upcoming item with this title. */
function upcoming(title: string) {
  const item = within(section("Upcoming"))
    .getByText(title)
    .closest("div.border-t");
  if (!(item instanceof HTMLElement)) throw new Error(`No item ${title}`);
  return item;
}

/** Each link in Upcoming, with the course screen readers hear after it. */
function upcomingActions() {
  return within(section("Upcoming"))
    .getAllByRole("link")
    .map((link) => ({
      name: link.textContent,
      href: link.getAttribute("href"),
    }));
}

/** The lines of each entry on an observation card: the course, the other professor, and the status and date. */
function entries(title: string) {
  return within(section(title))
    .getAllByText(/^CS /)
    .map((course) =>
      [...(course.parentElement?.querySelectorAll("p") ?? [])].map(
        (p) => p.textContent,
      ),
    );
}

test("the summary cards show the professor's evaluation from the database", async () => {
  await open();

  expect(
    screen.getByText("Associate Professor • School of Engineering"),
  ).toBeDefined();
  expect(card("Evaluation Status")).toBe(
    "Evaluation StatusScheduledSilas Brightwater • Nov 4, 2026",
  );
  expect(card("Cycle")).toBe("CycleEvery 2 YearsAssociate Professor schedule");
  expect(card("Next Due")).toBe("Next DueSep 2026Fall 2026 semester");
  expect(card("Observations This Term")).toBe(
    "Observations This Term30 completed • 1 pending sign-off • 2 not yet submitted",
  );
});

test("Upcoming links each item to what the professor can do about it", async () => {
  // A submitted observation of the professor's class waits for her sign-off.
  current.upcoming.push({
    type: "SIGN_OFF",
    date: "2026-10-20",
    startTime: "08:30:00",
    overdue: false,
    observationId: 28,
    role: "OBSERVEE",
    status: "SUBMITTED",
    course: "CS 2305.001",
    with: "Silas Brightwater",
  });
  await open();
  await within(section("Upcoming")).findByRole("link", {
    name: "Start Survey",
  });

  // The summary's feedback item for CS 2336.001 is left out: the cycle's survey stands for process feedback.
  expect(upcomingActions()).toEqual([
    // The observee can open the record only once it is submitted, so they get their list.
    { name: "View Details (CS 4348.501)", href: "/faculty/observations/me" },
    { name: "Continue (CS 3345.002)", href: "/faculty/observations/31" },
    {
      name: "Review and Sign (CS 2305.001)",
      href: "/faculty/observations/28",
    },
    { name: "Start Survey", href: "/faculty/feedback?cycle=1" },
  ]);
  expect(upcoming("Observation — CS 4348.501").textContent).toContain(
    "Observer: Silas Brightwater",
  );
  expect(upcoming("Process Feedback Due").textContent).toContain("Due Dec 1");
});

test("history and observation cards show the newest two records", async () => {
  await open();

  const history = section("Evaluation History");
  expect(history.textContent).toContain("Fall 2024Completed • 1 observation");
  expect(history.textContent).toContain("Fall 2022");
  expect(history.textContent).not.toContain("Spring 2021");
  expect(
    within(history)
      .getByRole("link", { name: "View Evaluation (Fall 2024)" })
      .getAttribute("href"),
  ).toBe("/faculty/history?cycle=4");

  expect(entries("Observations for Me")).toEqual([
    ["CS 4348.501", "Observer: Silas Brightwater", "Not started • Nov 4, 2026"],
    ["CS 4348.501", "Observer: Ivo Pembrook", "Completed • Oct 15, 2024"],
  ]);
  expect(entries("Observations for Others")).toEqual([
    ["CS 3345.002", "Observee: Ivo Pembrook", "In progress • Nov 6, 2026"],
    [
      "CS 2336.001",
      "Observee: Mira Castellane",
      "Pending sign-off • Oct 20, 2026",
    ],
  ]);
});

test("a due professor who has not signed up is asked to sign up", async () => {
  // Only an observee in the cycle has a survey.
  surveys = [];
  current.evaluation = {
    status: "NOT_SIGNED_UP",
    signupDeadline: "2026-10-31",
    course: null,
    observer: null,
    observationDate: null,
    completedAt: null,
  };
  current.upcoming = [
    {
      type: "SIGNUP",
      date: "2026-10-31",
      startTime: null,
      overdue: false,
      observationId: null,
      role: null,
      status: null,
      course: null,
      with: null,
    },
  ];
  await open();

  expect(card("Evaluation Status")).toBe(
    "Evaluation StatusSign-up NeededSign up by Oct 31, 2026",
  );
  expect(upcomingActions()).toEqual([
    { name: "Sign Up", href: "/faculty/signup" },
  ]);
});

test("a failed load explains why, and Retry loads the dashboard", async () => {
  failure = Response.json(
    { status: 500, detail: "The evaluation cycle changed." },
    { status: 500 },
  );
  window.history.pushState({}, "", "/faculty");
  render(<App />);

  const alert = await screen.findByRole("alert");
  expect(alert.textContent).toContain("The evaluation cycle changed.");

  fireEvent.click(
    within(alert).getByRole("button", { name: "Retry evaluation summary" }),
  );
  await screen.findByText("Evaluation Status");
  expect(screen.queryByRole("alert")).toBeNull();
});

test("the dashboard reloads when a dialog changes an observation or a notification arrives", async () => {
  current.upcoming = current.upcoming.map((item) =>
    item.observationId === 31 ? { ...item, status: "NOT_STARTED" } : item,
  );
  await open();
  await within(section("Upcoming")).findByRole("link", {
    name: "Start Observation (CS 3345.002)",
  });

  // Ivo's observation was started in the dialog above the dashboard.
  current = structuredClone(dashboard);
  act(() => observerChanged());
  await within(section("Upcoming")).findByRole("link", {
    name: "Continue (CS 3345.002)",
  });

  // Silas's submission of the observation of Wren's class arrives with a notification.
  current.evaluation.status = "SUBMITTED";
  state.notifications = [
    {
      notificationId: 1,
      message:
        "Silas Brightwater submitted the observation of CS 4348.501. You can view it now.",
      createdAt: "2026-11-04T11:00:00-06:00",
    },
  ];
  // The status reloads when the window regains focus, as well as every few seconds.
  fireEvent.focus(window);
  await waitFor(() =>
    expect(card("Evaluation Status")).toContain("Pending sign-off"),
  );
});
