import { afterEach, beforeEach, expect, type Mock, test } from "bun:test";
import {
  cleanup,
  fireEvent,
  render,
  screen,
  within,
} from "@testing-library/react";
import { App } from "../../App";
import type {
  ObservationDetail,
  ObservationSummary,
} from "../../api/observations";
import {
  facultyStateFor,
  OLIVIA,
  oliviaObservations,
  submittedObservation,
} from "../../test/facultyObservationFixtures";
import { fakeBackend, resetBackend, signInAs } from "../../test/fakeBackend";
import { completedObservation } from "../../test/observationFixtures";

let list: ObservationSummary[];
let failures: Response[];
let listRequests: number;

/** A completed observation of Olivia's class with no stored answers, as some demo records are. */
const unanswered: ObservationDetail = {
  ...structuredClone(completedObservation),
  observationId: 9,
  viewerRole: "OBSERVEE",
  course: { ...completedObservation.course, label: "CS 3345.002" },
  semesterLabel: "Spring 2026",
  responses: [],
};

beforeEach(() => {
  list = oliviaObservations();
  failures = [];
  listRequests = 0;
  fakeBackend({
    // The faculty dashboard stays behind the dialog and loads its own data.
    [`/api/observer-eligibility/faculty/${OLIVIA.professorId}`]: () =>
      Response.json(facultyStateFor(OLIVIA)),
    [`/api/professors/${OLIVIA.professorId}/observations`]: () => {
      listRequests++;
      return failures.shift() ?? Response.json(list);
    },
    "/api/observations/11": () => Response.json(submittedObservation()),
    "/api/observations/9": () => Response.json(unanswered),
  });
  signInAs("professor", OLIVIA);
});

afterEach(() => {
  cleanup();
  resetBackend();
});

async function openList() {
  window.history.pushState({}, "", "/faculty/observations/me");
  render(<App />);
  return within(
    await screen.findByRole("dialog", { name: "Observation Records for Me" }),
  );
}

/** The list item for one of Olivia's observations, found by its course. */
async function item(dialog: ReturnType<typeof within>, course: string) {
  const found = (await dialog.findByText(course)).closest("li");
  if (!found) throw new Error(`Missing observation of ${course}`);
  return within(found);
}

test("lists only observations of the professor's own classes, newest first", async () => {
  const dialog = await openList();
  await dialog.findByText("CS 3345.001");
  const courses = dialog
    .getAllByRole("listitem")
    .map((li) => li.querySelector("p")?.textContent);
  expect(courses).toEqual([
    "CS 4348.501",
    "CS 2305.001",
    "CS 3354.001",
    "CS 3345.001",
    "CS 3345.002",
  ]);
  // Observations Olivia makes of other professors are on the other list.
  expect(dialog.queryByText("CS 1337.002")).toBeNull();
  expect(dialog.queryByText(/Nina Notstarted/)).toBeNull();
});

test("only a submitted or completed observation can be opened", async () => {
  const dialog = await openList();

  const submitted = await item(dialog, "CS 3345.001");
  expect(submitted.getByText("Observer: Oscar Observer")).toBeDefined();
  expect(
    submitted.getByText("Fall 2026 · Oct 20, 2026, 8:30 AM"),
  ).toBeDefined();
  expect(submitted.getByText("Submitted")).toBeDefined();
  expect(
    submitted
      .getByRole("link", {
        name: "View the Fall 2026 CS 3345.001 observation",
      })
      .getAttribute("href"),
  ).toBe("/faculty/observations/11");

  const completed = await item(dialog, "CS 3345.002");
  expect(
    completed.getByText("Spring 2026 · Mar 9, 2026, 9:00 AM"),
  ).toBeDefined();
  expect(completed.getByText("Completed")).toBeDefined();
  expect(
    completed
      .getByRole("link", {
        name: "View the Spring 2026 CS 3345.002 observation",
      })
      .getAttribute("href"),
  ).toBe("/faculty/observations/9");

  // The observee can't open a draft, so earlier statuses have no link.
  for (const [course, status, when] of [
    ["CS 4348.501", "Not started", "Fall 2026 · Not scheduled"],
    ["CS 2305.001", "Not started", "Fall 2026 · Nov 5, 2026, 10:00 AM"],
    ["CS 3354.001", "In progress", "Fall 2026 · Oct 26, 2026, 2:00 PM"],
  ] as const) {
    const draft = await item(dialog, course);
    expect(draft.getByText(status)).toBeDefined();
    expect(draft.getByText(when)).toBeDefined();
    expect(
      draft.getByText("The observer has not submitted this observation yet."),
    ).toBeDefined();
    expect(draft.queryByRole("link")).toBeNull();
  }
  expect(dialog.getAllByRole("link", { name: /^View/ })).toHaveLength(2);
});

async function openRecord(name: string) {
  const dialog = await openList();
  fireEvent.click(await dialog.findByRole("link", { name }));
  return within(
    await screen.findByRole("dialog", { name: "Classroom Observation" }),
  );
}

test("View opens a submitted observation read-only, with who submitted it and when", async () => {
  const view = await openRecord("View the Fall 2026 CS 3345.001 observation");
  expect(window.location.pathname).toBe("/faculty/observations/11");
  await view.findByText(
    "Submitted by Oscar Observer on Oct 20, 2026, 9:15 AM CT.",
  );

  const summary = within(view.getByRole("region", { name: "Summary" }));
  expect(summary.getByText("Olivia Observee")).toBeDefined();
  expect(summary.getByText("Oscar Observer")).toBeDefined();
  expect(summary.getByText("Submitted")).toBeDefined();
  const responses = within(view.getByRole("region", { name: "Responses" }));
  expect(responses.getByText("Rated 4 out of 5")).toBeDefined();
  expect(responses.getByText("Students asked questions.")).toBeDefined();
  expect(responses.getByText("Rated 5 out of 5")).toBeDefined();
  expect(
    responses.getByText(/^Clear examples\.\s+Good pacing\.$/),
  ).toBeDefined();
  // The optional last question was left blank.
  expect(responses.getAllByText("No response")).toHaveLength(1);

  // Nothing to start, fill in, save, submit or sign.
  expect(view.queryAllByRole("button")).toHaveLength(0);
  expect(view.queryByRole("radiogroup")).toBeNull();
  expect(view.queryAllByRole("textbox")).toHaveLength(0);
  expect(view.queryAllByRole("checkbox")).toHaveLength(0);
  expect(view.queryAllByText(/sign/i)).toHaveLength(0);
});

test("a completed observation without stored answers says so for every question", async () => {
  const view = await openRecord("View the Spring 2026 CS 3345.002 observation");
  const responses = within(
    await view.findByRole("region", { name: "Responses" }),
  );
  expect(responses.getAllByRole("listitem")).toHaveLength(4);
  expect(responses.getAllByText("No response")).toHaveLength(4);
  expect(
    within(view.getByRole("region", { name: "Summary" })).getByText(
      "Completed",
    ),
  ).toBeDefined();
});

test("a professor nobody has observed yet sees an empty list", async () => {
  list = list.filter((o) => o.role === "OBSERVER");
  const dialog = await openList();
  await dialog.findByText("No observations of your classes yet.");
  expect(dialog.queryByRole("listitem")).toBeNull();
});

test("shows the API's reason when the list can't load, and Retry tries again", async () => {
  // One failure for the dashboard behind the dialog, and one for the dialog.
  failures.push(
    Response.json({ detail: "Observations are unavailable." }, { status: 503 }),
    Response.json({ detail: "Observations are unavailable." }, { status: 503 }),
  );
  const dialog = await openList();
  const alert = await dialog.findByRole("alert");
  expect(alert.textContent).toContain("Observations are unavailable.");

  fireEvent.click(within(alert).getByRole("button", { name: "Retry" }));
  await dialog.findByText("CS 3345.001");
  expect(dialog.queryByRole("alert")).toBeNull();
  expect(listRequests).toBe(3);
});

test("sessions without a faculty profile load nothing", async () => {
  signInAs("professor", { professorId: null });
  const dialog = await openList();
  expect(dialog.getByRole("alert").textContent).toContain(
    "Your session has no faculty profile",
  );
  const calls = (
    globalThis.fetch as unknown as Mock<typeof fetch>
  ).mock.calls.filter(([input]) => String(input).includes("/observations"));
  expect(calls).toHaveLength(0);
});
