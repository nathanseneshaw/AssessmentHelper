import { afterEach, beforeEach, expect, test } from "bun:test";
import {
  cleanup,
  fireEvent,
  render,
  screen,
  within,
} from "@testing-library/react";
import { App } from "../../App";
import { dashboard } from "../../test/facultyDashboardFixtures";
import {
  facultyStateFor,
  OLIVIA,
  oliviaObservations,
  submittedObservation,
} from "../../test/facultyObservationFixtures";
import { fakeBackend, resetBackend, signInAs } from "../../test/fakeBackend";

let failures: Response[];

beforeEach(() => {
  failures = [];
  fakeBackend({
    // The faculty dashboard stays behind the dialog and loads its own data.
    [`/api/observer-eligibility/faculty/${OLIVIA.professorId}`]: () =>
      Response.json(facultyStateFor(OLIVIA)),
    [`/api/professors/${OLIVIA.professorId}/dashboard`]: () =>
      Response.json(dashboard),
    [`/api/professors/${OLIVIA.professorId}/observations`]: () =>
      failures.shift() ?? Response.json(oliviaObservations()),
    "/api/observations/11": () => Response.json(submittedObservation()),
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

test("lists only observations of the professor's own classes, with a link once the observer submits", async () => {
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

  // The observee can't open a draft, so only the submitted and completed observations link anywhere.
  expect(
    dialog
      .getAllByRole("link", { name: /^(View|Review and Sign) the / })
      .map((link) => link.getAttribute("href")),
  ).toEqual(["/faculty/observations/11", "/faculty/observations/9"]);
});

test("Review and Sign opens the submitted answers read-only", async () => {
  const dialog = await openList();
  fireEvent.click(
    await dialog.findByRole("link", {
      name: "Review and Sign the Fall 2026 CS 3345.001 observation",
    }),
  );
  const view = within(
    await screen.findByRole("dialog", { name: "Classroom Observation" }),
  );
  expect(window.location.pathname).toBe("/faculty/observations/11");
  await view.findByText(
    "Submitted by Oscar Observer on Oct 20, 2026, 9:15 AM CT.",
  );

  const responses = within(view.getByRole("region", { name: "Responses" }));
  expect(responses.getByText("Rated 4 out of 5")).toBeDefined();
  expect(responses.getByText("Students asked questions.")).toBeDefined();
  // The observee's only action is to sign off.
  expect(view.queryByRole("radiogroup")).toBeNull();
  expect(view.getAllByRole("button").map((b) => b.textContent)).toEqual([
    "Sign off",
  ]);
});

test("shows the API's reason when the list can't load, and Retry tries again", async () => {
  failures.push(
    Response.json({ detail: "Observations are unavailable." }, { status: 503 }),
  );
  const dialog = await openList();
  const alert = await dialog.findByRole("alert");
  expect(alert.textContent).toContain("Observations are unavailable.");

  fireEvent.click(within(alert).getByRole("button", { name: "Retry" }));
  await dialog.findByText("CS 3345.001");
  expect(dialog.queryByRole("alert")).toBeNull();
});
