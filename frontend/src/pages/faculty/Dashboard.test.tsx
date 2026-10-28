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
let requests = 0;

beforeEach(() => {
  state = structuredClone(faculty);
  current = structuredClone(dashboard);
  surveys = [fallSurvey];
  failure = undefined;
  requests = 0;
  fakeBackend({
    "/api/feedback-surveys": () => Response.json(surveys),
    "/api/observer-eligibility/faculty/1": () => Response.json(state),
    "/api/professors/1/dashboard": () => {
      requests += 1;
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
    "Observations This Term31 completed • 2 scheduled",
  );
});

test("a submitted evaluation says so", async () => {
  current.evaluation.status = "SUBMITTED";
  await open();
  expect(card("Evaluation Status")).toBe(
    "Evaluation StatusSubmittedSilas Brightwater • Nov 4, 2026",
  );
});

test("Upcoming links each item to what the professor can do about it", async () => {
  await open();
  await within(section("Upcoming")).findByRole("link", {
    name: "Start Survey",
  });

  // The summary's feedback item for CS 2336.001 is left out: the cycle's survey stands for process feedback.
  expect(upcomingActions()).toEqual([
    // The observee can open the record only once it is submitted, so they get their list.
    { name: "View Details (CS 4348.501)", href: "/faculty/observations/me" },
    { name: "Continue (CS 3345.002)", href: "/faculty/observations/31" },
    { name: "Start Survey", href: "/faculty/feedback?cycle=1" },
  ]);
  expect(within(section("Upcoming")).getByText("3 items due")).toBeDefined();

  const mine = upcoming("Observation — CS 4348.501");
  expect(mine.textContent).toContain("NOV04");
  expect(mine.textContent).toContain("Observer: Silas Brightwater");
  expect(mine.textContent).toContain("Not started");
  expect(mine.textContent).toContain("Nov 4, 2026, 8:30 AM");
  expect(mine.innerHTML).not.toContain("bg-peach");

  const theirs = upcoming("Observation — CS 3345.002");
  expect(theirs.textContent).toContain("Observee: Ivo Pembrook");
  expect(theirs.textContent).toContain("In progress");
  // No start time yet, so only the day shows.
  expect(theirs.textContent).toContain("Nov 6, 2026");

  const feedback = upcoming("Process Feedback Due");
  expect(feedback.textContent).toContain("Fall 2026");
  expect(feedback.textContent).toContain("DEC01");
  expect(feedback.textContent).toContain("Due Dec 1");
  expect(feedback.textContent).toContain("Action needed");
  expect(feedback.innerHTML).toContain("bg-peach");
});

test("an observation the observer has not started, with no date yet, says Start Observation and TBD", async () => {
  current.upcoming = current.upcoming.map((item) =>
    item.observationId === 31
      ? { ...item, status: "NOT_STARTED", date: null, startTime: null }
      : item,
  );
  await open();

  expect(upcomingActions()[1]).toEqual({
    name: "Start Observation (CS 3345.002)",
    href: "/faculty/observations/31",
  });
  const undated = upcoming("Observation — CS 3345.002");
  expect(undated.textContent).toContain("TBD");
  expect(undated.textContent).toContain("Not scheduled");
});

test("history and observation cards show the newest two records", async () => {
  await open();

  const history = section("Evaluation History");
  expect(history.textContent).toContain("Fall 2024Completed • 1 observation");
  expect(history.textContent).toContain("Fall 2022");
  expect(history.textContent).not.toContain("Spring 2021");

  expect(entries("Observations for Me")).toEqual([
    ["CS 4348.501", "Observer: Silas Brightwater", "Not started • Nov 4, 2026"],
    ["CS 4348.501", "Observer: Ivo Pembrook", "Completed • Oct 15, 2024"],
  ]);
  expect(entries("Observations for Others")).toEqual([
    ["CS 3345.002", "Observee: Ivo Pembrook", "In progress • Nov 6, 2026"],
    ["CS 2336.001", "Observee: Mira Castellane", "Submitted • Oct 20, 2026"],
  ]);
  for (const [title, href] of [
    ["Observations for Me", "/faculty/observations/me"],
    ["Observations for Others", "/faculty/observations/others"],
  ] as const)
    expect(
      within(section(title))
        .getByRole("link", { name: "View All Records →" })
        .getAttribute("href"),
    ).toBe(href);
});

test("submitting the survey replaces Start Survey with View Survey", async () => {
  await open();
  await within(section("Upcoming")).findByRole("link", {
    name: "Start Survey",
  });

  surveys = [
    { ...fallSurvey, status: "SUBMITTED", submittedDate: "2026-11-25" },
  ];
  act(() => observerChanged());

  await within(section("Upcoming")).findByRole("link", {
    name: "View Survey",
  });
  expect(upcomingActions().at(-1)).toEqual({
    name: "View Survey",
    href: "/faculty/feedback?cycle=1",
  });
  const submitted = upcoming("Process Feedback Submitted");
  expect(submitted.textContent).toContain("Submitted Nov 25, 2026");
  expect(submitted.innerHTML).not.toContain("bg-peach");
  expect(within(section("Upcoming")).getByText("2 items due")).toBeDefined();
});

test("the survey shows, disabled, until the observation deadline passes", async () => {
  surveys = [{ ...fallSurvey, status: "NOT_OPEN" }];
  await open();

  const button = await within(section("Upcoming")).findByRole("button", {
    name: "Start Survey",
  });
  expect((button as HTMLButtonElement).disabled).toBe(true);
  expect(upcoming("Process Feedback Due").textContent).toContain(
    "Not yet available",
  );
  expect(
    within(section("Upcoming")).queryByRole("link", { name: "Start Survey" }),
  ).toBeNull();

  // The survey is checked again when the window regains focus.
  surveys = [fallSurvey];
  fireEvent.focus(window);
  await within(section("Upcoming")).findByRole("link", {
    name: "Start Survey",
  });
  expect(
    within(section("Upcoming")).queryByRole("button", { name: "Start Survey" }),
  ).toBeNull();
});

test.each(["CLOSED", "UNCONFIGURED"] as const)(
  "a %s survey has no Upcoming item",
  async (status) => {
    surveys = [
      fallSurvey,
      { ...fallSurvey, cycleId: 2, label: "Spring 2026", status },
    ];
    await open();
    // Both surveys arrive together, so once Fall's shows, Spring's would have too.
    await within(section("Upcoming")).findByRole("link", {
      name: "Start Survey",
    });

    expect(within(section("Upcoming")).queryByText("Spring 2026")).toBeNull();
    expect(
      within(section("Upcoming")).getAllByText("Process Feedback Due"),
    ).toHaveLength(1);
  },
);

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
  current.schedule = {
    ...current.schedule,
    nextDueDate: "2026-01-27",
    nextDueSemester: "Spring 2026",
    dueStatus: "Overdue",
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
  expect(card("Next Due")).toBe(
    "Next DueJan 2026Overdue • Spring 2026 semester",
  );
  const signup = upcoming("Sign Up for Evaluation");
  expect(signup.textContent).toContain("Fall 2026 cycle");
  expect(signup.textContent).toContain("Due Oct 31");
  expect(upcomingActions()).toEqual([
    { name: "Sign Up", href: "/faculty/signup" },
  ]);
});

test("an observation past its date without a submitted form is flagged Overdue", async () => {
  current.upcoming = current.upcoming
    .slice(0, 1)
    .map((item) => ({ ...item, overdue: true }));
  current.observationsForMe = current.observationsForMe
    .slice(0, 1)
    .map((item) => ({ ...item, overdue: true }));
  await open();

  const late = upcoming("Observation — CS 4348.501");
  expect(late.textContent).toContain("Overdue");
  expect(late.innerHTML).toContain("bg-peach");
  expect(entries("Observations for Me")).toEqual([
    ["CS 4348.501", "Observer: Silas Brightwater", "Overdue • Nov 4, 2026"],
  ]);
});

test("a professor with nothing on record sees empty states", async () => {
  surveys = [];
  current = {
    ...current,
    evaluation: { ...current.evaluation, status: "NOT_DUE" },
    schedule: {
      rank: null,
      intervalYears: null,
      nextDueDate: null,
      nextDueSemester: null,
      dueStatus: null,
    },
    termObservations: { total: 0, completed: 0, scheduled: 0 },
    upcoming: [],
    history: [],
    observationsForMe: [],
    observationsForOthers: [],
  };
  await open();

  expect(card("Evaluation Status")).toBe(
    "Evaluation StatusNot DueNothing to do this cycle",
  );
  expect(card("Cycle")).toBe("CycleNot setNo evaluation policy applies");
  expect(card("Next Due")).toBe("Next DueNot setNo due date on file");
  expect(card("Observations This Term")).toBe(
    "Observations This Term0None yet",
  );
  expect(screen.getByText("Nothing coming up this cycle.")).toBeDefined();
  expect(screen.getByText("No previous evaluations.")).toBeDefined();
  expect(screen.getAllByText("No observations yet.")).toHaveLength(2);
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
  expect(screen.queryByText("Evaluation Status")).toBeNull();

  fireEvent.click(
    within(alert).getByRole("button", { name: "Retry evaluation summary" }),
  );
  await screen.findByText("Evaluation Status");
  expect(screen.queryByRole("alert")).toBeNull();
  expect(requests).toBe(2);
});

test("the dashboard reloads when a dialog changes an observation", async () => {
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
});

test("a new notification reloads the dashboard", async () => {
  await open();
  expect(entries("Observations for Me")[0]).toEqual([
    "CS 4348.501",
    "Observer: Silas Brightwater",
    "Not started • Nov 4, 2026",
  ]);

  // Silas's submission of the observation of Wren's class arrives with a notification.
  current.evaluation.status = "SUBMITTED";
  current.observationsForMe = current.observationsForMe.map((item) =>
    item.observationId === 30 ? { ...item, status: "SUBMITTED" } : item,
  );
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
    expect(entries("Observations for Me")[0]).toEqual([
      "CS 4348.501",
      "Observer: Silas Brightwater",
      "Submitted • Nov 4, 2026",
    ]),
  );
  expect(card("Evaluation Status")).toContain("Submitted");
});
