import { afterEach, expect, test } from "bun:test";
import {
  cleanup,
  fireEvent,
  render,
  screen,
  within,
} from "@testing-library/react";
import { fakeBackend, resetBackend } from "../test/fakeBackend";
import { completedObservation } from "../test/observationFixtures";
import { FacultyEvaluationHistory } from "./FacultyEvaluationHistory";

afterEach(() => {
  cleanup();
  resetBackend();
});

test("shows only the faculty's own cycle observations and opens completed forms", async () => {
  const summary = {
    observationId: 11,
    role: "OBSERVEE",
    status: "COMPLETED",
    courseLabel: "CS 3345.001",
    observerName: "Oscar Observer",
    cycleId: 2,
    observationDate: "2026-10-20",
    startTime: "08:30:00",
  };
  fakeBackend({
    "/api/professors/21/evaluations": () =>
      Response.json([
        {
          cycleId: 2,
          term: "Fall",
          academicYear: 2026,
          status: "COMPLETED",
          observationCount: 1,
        },
        {
          cycleId: 3,
          term: "Spring",
          academicYear: 2027,
          status: "ACTIVE",
          observationCount: 1,
        },
      ]),
    "/api/professors/21/observations": () =>
      Response.json([
        summary,
        {
          ...summary,
          observationId: 12,
          status: "IN_PROGRESS",
          courseLabel: "Unfinished course",
        },
        {
          ...summary,
          observationId: 13,
          cycleId: 3,
          courseLabel: "Other cycle",
        },
        {
          ...summary,
          observationId: 14,
          role: "OBSERVER",
          courseLabel: "Other faculty",
        },
      ]),
    "/api/observations/11": () => Response.json(completedObservation),
  });
  render(
    <FacultyEvaluationHistory
      professorId={21}
      name="Olivia Observee"
      onClose={() => {}}
    />,
  );
  await screen.findByText("Completed: 1 observation(s)");
  expect(screen.getByText("In progress")).toBeTruthy();
  expect(screen.queryByText("In progress: 1 observation(s)")).toBeNull();
  fireEvent.click(
    within(
      screen
        .getByText("Completed: 1 observation(s)")
        .closest("li") as HTMLElement,
    ).getByRole("button", { name: "View Observation(s)" }),
  );
  await screen.findByText("CS 3345.001");
  expect(screen.getByText("Unfinished course")).toBeTruthy();
  expect(screen.queryByText("Other cycle")).toBeNull();
  expect(screen.queryByText("Other faculty")).toBeNull();
  expect(
    screen.getAllByRole("button", { name: "View Observation" }),
  ).toHaveLength(1);
  fireEvent.click(screen.getByRole("button", { name: "View Observation" }));
  const form = screen.getByRole("region", {
    name: "Completed observation form",
  });
  await within(form).findByText("Students asked questions.");
  fireEvent.click(within(form).getByRole("button", { name: "Close form" }));
  expect(
    screen.queryByRole("region", { name: "Completed observation form" }),
  ).toBeNull();
});

test("retries failed overview requests and displays an empty cycle", async () => {
  let fail = true;
  fakeBackend({
    "/api/professors/21/evaluations": () =>
      Response.json([
        {
          cycleId: 2,
          term: "Fall",
          academicYear: 2026,
          status: "ACTIVE",
          observationCount: 0,
        },
      ]),
    "/api/professors/21/observations": () =>
      fail
        ? Response.json({ detail: "Unavailable" }, { status: 503 })
        : Response.json([]),
  });
  render(
    <FacultyEvaluationHistory
      professorId={21}
      name="Olivia Observee"
      onClose={() => {}}
    />,
  );
  fireEvent.click(
    await screen.findByRole("button", { name: "View Observation(s)" }),
  );
  await screen.findByRole("alert");
  fail = false;
  fireEvent.click(screen.getByRole("button", { name: "Retry" }));
  await screen.findByText("No observations are recorded for this cycle.");
});
