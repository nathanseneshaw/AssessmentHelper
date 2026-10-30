import { afterEach, beforeEach, expect, test } from "bun:test";
import {
  cleanup,
  fireEvent,
  render,
  screen,
  within,
} from "@testing-library/react";
import { App } from "../../App";
import type { EvaluationRecord } from "../../api/evaluations";
import {
  facultyStateFor,
  OLIVIA,
  oliviaObservations,
} from "../../test/facultyObservationFixtures";
import { fakeBackend, resetBackend, signInAs } from "../../test/fakeBackend";

let records: EvaluationRecord[];
let failures: Response[];

beforeEach(() => {
  records = [
    {
      cycleId: 2,
      term: "Fall",
      academicYear: 2026,
      status: "ACTIVE",
      observationCount: 1,
      lastObservationCompletedAt: null,
    },
    {
      cycleId: 1,
      term: "Spring",
      academicYear: 2026,
      status: "COMPLETED",
      observationCount: 2,
      lastObservationCompletedAt: "2026-03-10T11:00:00",
    },
  ];
  failures = [];
  fakeBackend({
    [`/api/professors/${OLIVIA.professorId}/evaluations`]: () =>
      failures.shift() ?? Response.json(records),
    [`/api/professors/${OLIVIA.professorId}/evaluation-deadline`]: () =>
      Response.json({ detail: "Not needed here" }, { status: 404 }),
    [`/api/observer-eligibility/faculty/${OLIVIA.professorId}`]: () =>
      Response.json(facultyStateFor(OLIVIA)),
    [`/api/professors/${OLIVIA.professorId}/observations`]: () =>
      Response.json(oliviaObservations()),
    "/api/feedback-surveys": () => Response.json([]),
  });
  signInAs("professor", OLIVIA);
});

afterEach(() => {
  cleanup();
  resetBackend();
});

async function openHistory(search = "") {
  window.history.pushState({}, "", `/faculty/history${search}`);
  render(<App />);
  return screen.findByRole("dialog");
}

test("the history dialog lists every cycle, newest first", async () => {
  const dialog = await openHistory();
  await within(dialog).findByText("Spring 2026");

  const rows = within(dialog)
    .getAllByRole("row")
    .slice(1)
    .map((row) =>
      [...row.querySelectorAll("th, td")].map((cell) => cell.textContent),
    );
  expect(rows).toEqual([
    [
      "Fall 2026",
      "In progress",
      "1 observation",
      "Not completed",
      "Details (Fall 2026)",
    ],
    [
      "Spring 2026",
      "Completed",
      "2 observations",
      "Mar 10, 2026, 11:00 AM CT",
      "Details (Spring 2026)",
    ],
  ]);
});

test("the history dialog says when there are no cycles yet", async () => {
  records = [];
  const dialog = await openHistory();
  await within(dialog).findByText(
    "You have not been part of an evaluation cycle yet.",
  );
});

test("the history dialog shows the API's reason and Retry tries again", async () => {
  failures.push(
    Response.json({ detail: "History is unavailable." }, { status: 503 }),
  );
  const dialog = await openHistory();
  const alert = await within(dialog).findByRole("alert");
  expect(alert.textContent).toContain("History is unavailable.");

  fireEvent.click(within(alert).getByRole("button", { name: "Retry" }));
  await within(dialog).findByText("Spring 2026");
  expect(within(dialog).queryByRole("alert")).toBeNull();
});

test("a cycle's details list the observations of the professor's classes in it", async () => {
  const dialog = await openHistory("?cycle=2");
  const detail = await within(dialog).findByRole("region", {
    name: "Fall 2026 observations",
  });
  await within(detail).findByText("CS 4348.501");

  // Olivia's four Fall 2026 observations; none of her observer work and nothing from Spring.
  expect(within(detail).getAllByRole("listitem")).toHaveLength(4);
  expect(within(detail).queryByText("CS 1337.002")).toBeNull();
  expect(within(detail).queryByText("CS 3345.002")).toBeNull();
  // Only a submitted observation can be opened.
  expect(
    within(detail)
      .getAllByRole("link")
      .map((link) => link.getAttribute("href")),
  ).toEqual(["/faculty/observations/11"]);
  expect(within(detail).getAllByText("Not submitted yet")).toHaveLength(3);
});

test("choosing Details shows that cycle's observations", async () => {
  const dialog = await openHistory();
  fireEvent.click(
    await within(dialog).findByRole("link", { name: "Details (Spring 2026)" }),
  );
  const detail = await within(dialog).findByRole("region", {
    name: "Spring 2026 observations",
  });
  await within(detail).findByRole("link", { name: "View CS 3345.002" });
  expect(within(detail).getAllByRole("listitem")).toHaveLength(1);
  expect(
    within(dialog)
      .getByRole("link", { name: "Details (Spring 2026)" })
      .getAttribute("aria-current"),
  ).toBe("true");
});

test("a cycle the professor was not part of is reported", async () => {
  const dialog = await openHistory("?cycle=99");
  await within(dialog).findByText("You have no evaluation for that cycle.");
  expect(within(dialog).queryByRole("region")).toBeNull();
});
